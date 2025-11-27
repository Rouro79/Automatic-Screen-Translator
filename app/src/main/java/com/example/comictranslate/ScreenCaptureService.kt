package com.example.comictranslate

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_DATA = "RESULT_DATA"
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var imageReader: ImageReader? = null

    private lateinit var analyzer: ScreenCaptureAnalyzer

    private val TAG = "ScreenCaptureService"

    // Debounce guard: true while an analyze job is running
    private val isAnalyzing = AtomicBoolean(false)

    // Required for Android 14+
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system")
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // get screen size once
        val (screenWidth, screenHeight) = getScreenSize()

        // analyzer expects (context, screenWidth, screenHeight) per your earlier design
        analyzer = ScreenCaptureAnalyzer(this, screenWidth, screenHeight)

        Log.d(TAG, "Service created: $screenWidth x $screenHeight")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            ACTION_START -> {
                startForegroundProper()

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData =
                    if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                    else
                        intent.getParcelableExtra(EXTRA_DATA)

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    Log.e(TAG, "Missing MediaProjection permission")
                    stopSelf()
                    return START_NOT_STICKY
                }

                mediaProjection =
                    mediaProjectionManager?.getMediaProjection(resultCode, resultData)

                // register callback (Android 14+ safe)
                mediaProjection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

                startCapture()
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    // ---------------------------------------------------------
    // ✔ Correct Screen Size for Services (Android 10 - 14)
    // ---------------------------------------------------------
    private fun getScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    // ---------------------------------------------------------
    // MediaProjection + ImageReader
    // ---------------------------------------------------------
    private fun startCapture() {
        val (width, height) = getScreenSize()

        // Use a single buffer to avoid the "maxImages" problems on some devices
        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            1
        )

        try {
            mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                resources.displayMetrics.densityDpi,
                0,
                imageReader!!.surface,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "createVirtualDisplay failed: ${e.message}")
            return
        }

        // Listen on main looper — image processing will be dispatched into analyzer's coroutine
        imageReader!!.setOnImageAvailableListener({ reader ->

            // If an overlay is currently shown, skip analysis — keep overlay visible until user taps
            try {
                if (OverlayManager.hasOverlays()) {
                    // do not call analyze while overlay is present
                    // this prevents overlays being constantly replaced or removed
                    return@setOnImageAvailableListener
                }
            } catch (ignored: Exception) {
                // If OverlayManager.hasOverlays() throws, fall back to normal behavior
            }

            // Do not start another analyze if one is already running
            if (!isAnalyzing.compareAndSet(false, true)) {
                // already analyzing, skip this frame
                return@setOnImageAvailableListener
            }

            val image = try {
                reader.acquireLatestImage()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "ImageReader acquire error: ${e.message}")
                isAnalyzing.set(false)
                return@setOnImageAvailableListener
            }

            if (image == null) {
                isAnalyzing.set(false)
                return@setOnImageAvailableListener
            }

            try {
                val planes = image.planes
                val buffer: ByteBuffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val paddedBitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                paddedBitmap.copyPixelsFromBuffer(buffer)

                val croppedBitmap = Bitmap.createBitmap(
                    paddedBitmap,
                    0,
                    0,
                    width,
                    height
                )

                // call analyzer and release the analyzing flag in onComplete
                analyzer.analyze(croppedBitmap) {
                    // onComplete runs on Main due to analyzer implementation, safe to update atomic
                    isAnalyzing.set(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame: ${e.message}")
                isAnalyzing.set(false)
            } finally {
                // ensure the image is closed no matter what
                try {
                    image.close()
                } catch (_: Exception) {}
            }

        }, Handler(Looper.getMainLooper()))

        Log.d(TAG, "VirtualDisplay started")
    }

    // ---------------------------------------------------------
    // Notification helpers
    // ---------------------------------------------------------
    private fun startForegroundProper() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }

        val pendingStop = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen capture running")
            .setContentText("Analyzing screen…")
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", pendingStop)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // ---------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()
        try { mediaProjection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}

        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}

        mediaProjection = null
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}