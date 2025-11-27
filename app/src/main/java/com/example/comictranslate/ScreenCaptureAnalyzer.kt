package com.example.comictranslate

import android.content.Context
import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * This version includes:
 * - Speech bubble detection (BubbleDetector)
 * - Text filtering: only texts inside bubbles
 * - Cache so each bubble is translated only once
 * - TTL to avoid flicker
 * - Overlay exact positioning
 * - Noise text completely ignored
 */
class ScreenCaptureAnalyzer(
    private val context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val analysisScope = CoroutineScope(Dispatchers.Default)

    // Cache processed bubble text (unique bubble hash)
    private val processedTextCache = mutableSetOf<Int>()
    private val cacheTimestamps = mutableMapOf<Int, Long>()
    private val cacheTtlMs: Long = 800        // fade-off stabilization

    private val cropInsetPx = 6               // shrink bubble edge for cleaner OCR

    fun analyze(bitmap: Bitmap, onComplete: () -> Unit) {
        analysisScope.launch {

            // -------------------------------
            // Remove expired cache entries
            // -------------------------------
            val now = System.currentTimeMillis()
            val expired = cacheTimestamps.filter { now - it.value > cacheTtlMs }.keys
            expired.forEach {
                processedTextCache.remove(it)
                cacheTimestamps.remove(it)
            }

            // -------------------------------
            // Bubble detection
            // -------------------------------
            val detector = BubbleDetector()
            val bubbles: List<Rect> = detector.detectBubbleAreas(bitmap)

            val results = mutableListOf<TranslatedText>()

            for (bubble in bubbles) {

                // Safe bounds
                val left = bubble.left.coerceAtLeast(0)
                val top = bubble.top.coerceAtLeast(0)
                val right = bubble.right.coerceAtMost(bitmap.width)
                val bottom = bubble.bottom.coerceAtMost(bitmap.height)

                if (right <= left || bottom <= top) continue

                val width = right - left
                val height = bottom - top

                if (width < 10 || height < 10) continue

                // -------------------------------
                // Crop bubble region for OCR
                // -------------------------------
                val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)

                val image = InputImage.fromBitmap(cropped, 0)
                val visionText = try {
                    recognizer.process(image).await()
                } catch (_: Exception) {
                    continue
                }

                val text = visionText.text.trim().replace("\n", " ")
                if (text.isEmpty()) continue

                // -------------------------------
                // Create a strong unique hash:
                // text + bubble position
                // -------------------------------
                val key = (text.hashCode() * 31) xor bubble.flattenToString().hashCode()

                // Skip if already translated recently
                if (processedTextCache.contains(key)) continue

                // -------------------------------
                // Translation (cached internally)
                // -------------------------------
                val translated = try {
                    TranslatorManager.translateText(context, text)
                } catch (_: Exception) {
                    text
                }

                // -------------------------------
                // Provide full bubble rect for overlay
                // -------------------------------
                results.add(
                    TranslatedText(
                        originalText = text,
                        translatedText = translated,
                        boundingBox = Rect(left, top, right, bottom)
                    )
                )

                // Store in cache
                processedTextCache.add(key)
                cacheTimestamps[key] = System.currentTimeMillis()
            }

            // -------------------------------
            // Draw overlays
            // -------------------------------
            withContext(Dispatchers.Main) {
                if (results.isNotEmpty()) {
                    OverlayManager.showOverlays(context, results)
                }
                onComplete()
            }
        }
    }

    /** Clears all cache when app closes */
    fun clearCache() {
        processedTextCache.clear()
        cacheTimestamps.clear()
    }
}
