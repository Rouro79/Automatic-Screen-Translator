package com.example.comictranslate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

object OverlayManager {
    private var container: FrameLayout? = null

    fun showBitmap(context: Context, bitmap: Bitmap) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (container == null) {
            val imageView = ImageView(context).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            container = FrameLayout(context).apply {
                addView(
                    imageView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                setOnClickListener {
                    clear(context)
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            wm.addView(container, params)
        } else {
            (container?.getChildAt(0) as? ImageView)?.setImageBitmap(bitmap)
        }
    }

    fun clear(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        container?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            container = null
        }
    }
}
