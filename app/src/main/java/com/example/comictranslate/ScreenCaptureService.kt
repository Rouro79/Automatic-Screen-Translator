package com.example.comictranslate

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var screenAnalyzer: ScreenCaptureAnalyzer
    private var isCapturing = false

    // Runnable for periodic captures
    private val captureRunnable = object : Runnable {
        override fun run() {
            if (isCapturing) {
                captureAndProcessImage()
                // Schedule the next capture after a delay
                handler.postDelayed(this, 2000L) // 2000ms = 2 seconds
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenAnalyzer = ScreenCaptureAnalyzer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle the action to stop the service
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        if (data != null && !isCapturing) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopCapture()
                }
            }, handler)
            startScreenCapture()
        }

        return START_STICKY // Keep service running
    }

    private fun startScreenCapture() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val screenDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        isCapturing = true
        // Start the periodic capture
        handler.post(captureRunnable)
    }

    private fun captureAndProcessImage() {
        val image = imageReader?.acquireLatestImage() ?: return

        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        var bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        image.close()

        // Analyze without a completion callback, as the service keeps running
        screenAnalyzer.analyze(bitmap) {}
    }

    private fun stopCapture() {
        isCapturing = false
        handler.removeCallbacks(captureRunnable) // Stop the capture loop
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Screen Capture Service Channel", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Comic Translate Active")
            .setContentText("Tap to stop.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "ScreenCaptureServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "ACTION_STOP"
    }
}