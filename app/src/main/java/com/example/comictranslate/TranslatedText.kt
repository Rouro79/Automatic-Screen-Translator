package com.example.comictranslate

import android.graphics.Rect

data class TranslatedText(
    val originalText: String,
    val translatedText: String,
    val boundingBox: Rect
)