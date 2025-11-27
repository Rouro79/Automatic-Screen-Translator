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
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer

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

    // ⭐ Frame stabilizer variables — prevents repeated translations
    private var lastBitmap: Bitmap? = null
    private var lastProcessTime = 0L

    // ⭐ REQUIRED FOR ANDROID 14+
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("ScreenCaptureService", "MediaProjection stopped by system")
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        analyzer = ScreenCaptureAnalyzer(this)

        Log.d("ScreenCaptureService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            ACTION_START -> {
                Log.d("ScreenCaptureService", "ACTION_START received")
                startForegroundProper()

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData =
                    if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                    else
                        intent.getParcelableExtra(EXTRA_DATA)

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    Log.e("ScreenCaptureService", "Missing MediaProjection permission")
                    stopSelf()
                    return START_NOT_STICKY
                }

                mediaProjection =
                    mediaProjectionManager?.getMediaProjection(resultCode, resultData)

                Log.d("ScreenCaptureService", "MediaProjection initialized")

                mediaProjection?.registerCallback(projectionCallback, null)

                startCapture()
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    // -------------------------------------------------------------------------
    // MediaProjection + ImageReader
    // -------------------------------------------------------------------------

    private fun startCapture() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = android.graphics.Point()
        wm.defaultDisplay.getRealSize(size)

        val width = size.x
        val height = size.y

        imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888,
            2
        )

        Log.d("ScreenCaptureService", "ImageReader created: $width x $height")

        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, resources.displayMetrics.densityDpi,
            0,
            imageReader!!.surface,
            null, null
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // Proper cropped screen bitmap
            val screenBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

            // ---------------------------------------------------------------------
            // ⭐ FRAME STABILIZER: Prevent rapid repeated translations
            // ---------------------------------------------------------------------
            val now = System.currentTimeMillis()
            if (now - lastProcessTime < 500) {   // 2 FPS max
                return@setOnImageAvailableListener
            }
            lastProcessTime = now

            if (lastBitmap != null && screenBitmap.sameAs(lastBitmap)) {
                return@setOnImageAvailableListener
            }

            lastBitmap = screenBitmap.copy(Bitmap.Config.ARGB_8888, false)
            // ---------------------------------------------------------------------

            analyzer.analyze(screenBitmap) {}

        }, null)

        Log.d("ScreenCaptureService", "VirtualDisplay started")
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

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
            .setContentTitle("Screen Capture Running")
            .setContentText("Your screen is being analyzed")
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // -------------------------------------------------------------------------
    // Destroy
    // -------------------------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {}

        mediaProjection?.stop()
        imageReader?.close()
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}