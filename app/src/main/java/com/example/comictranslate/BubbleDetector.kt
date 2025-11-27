package com.example.comictranslate

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class BubbleDetector {

    /**
     * Detects speech bubbles and returns bounding boxes in BITMAP coordinates.
     */
    fun detectBubbleAreas(bitmap: Bitmap): List<Rect> {

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Convert RGBA → Gray (most ScreenAnalyzers provide RGBA frames)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        // Preprocess for bubble extraction
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        Imgproc.adaptiveThreshold(
            gray, gray,
            255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C,
            Imgproc.THRESH_BINARY_INV,
            15, 3.0
        )

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            gray,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val bubbles = mutableListOf<Rect>()

        for (c in contours) {

            val ocvRect = Imgproc.boundingRect(c)

            // Filter out tiny artifacts
            if (ocvRect.width < 60 || ocvRect.height < 60) continue

            if (!isBubbleShape(c, ocvRect)) continue

            // Convert OpenCV Rect → Android Rect
            bubbles.add(
                Rect(
                    ocvRect.x,
                    ocvRect.y,
                    ocvRect.x + ocvRect.width,
                    ocvRect.y + ocvRect.height
                )
            )
        }

        // Cleanup
        src.release()
        gray.release()
        hierarchy.release()

        return bubbles
    }

    /**
     * Bubble shape filtering:
     * - Must be round-ish (>= 6 vertices)
     * - Must have reasonable aspect ratio
     */
    private fun isBubbleShape(
        contour: MatOfPoint,
        rect: org.opencv.core.Rect
    ): Boolean {

        val contour2f = MatOfPoint2f(*contour.toArray())
        val peri = Imgproc.arcLength(contour2f, true)

        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approx, peri * 0.02, true)

        // Bubbles are curved → many points
        if (approx.toArray().size < 6) return false

        val ratio = rect.width.toDouble() / rect.height
        if (ratio !in 0.4..2.5) return false

        return true
    }
}
