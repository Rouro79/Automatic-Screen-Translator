package com.example.comictranslate

import android.graphics.Rect
import org.opencv.core.MatOfPoint

data class OCRBox(
    val rect: Rect,
    val text: String
)

data class Bubble(
    val contour: MatOfPoint,
    val boundingRect: Rect
)
