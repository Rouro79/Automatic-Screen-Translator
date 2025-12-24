package com.example.comictranslate

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ScreenCaptureAnalyzer(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun analyze(bitmap: Bitmap, onComplete: () -> Unit) {
        if (bitmap.width < 100 || bitmap.height < 100) {
            onComplete()
            return
        }

        scope.launch {
            try {
                val translator = SpeechBalloonTranslator(context)
                val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

                val ocrBoxes = translator.runOcr(safeBitmap)
                val bubbles = translator.detectBubbles(safeBitmap)

                val translations = mutableMapOf<android.graphics.Rect, String>()
                for (box in ocrBoxes) {
                    if (box.text.isNotBlank()) {
                        val surroundingText = getSurroundingText(box, ocrBoxes)
                        val translated = try {
                            TranslatorManager.translateText(context, box.text, surroundingText)
                        } catch (e: Exception) {
                            box.text
                        }
                        translations[box.rect] = translated
                    }
                }

                val cleanedBitmap = translator.removeOriginalText(safeBitmap, bubbles, ocrBoxes)
                val finalBitmap = translator.drawTranslatedTextIntoBubbles(
                    context,
                    cleanedBitmap,
                    bubbles,
                    ocrBoxes,
                    translations
                )

                withContext(Dispatchers.Main) {
                    OverlayManager.showBitmap(context, finalBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    private fun getSurroundingText(currentBox: OCRBox, allBoxes: List<OCRBox>): String {
        val nearby = allBoxes.filter {
            abs(it.rect.centerY() - currentBox.rect.centerY()) < 200 && it != currentBox
        }.joinToString(" ") { it.text }
        return nearby
    }
}
