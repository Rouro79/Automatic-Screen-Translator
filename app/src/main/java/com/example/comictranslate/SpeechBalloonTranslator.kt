package com.example.comictranslate

import android.content.Context
import android.graphics.*
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

class SpeechBalloonTranslator(private val context: Context) {
    var debugMode = false

    suspend fun runOcr(bitmap: Bitmap): List<OCRBox> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()

        val boxes = mutableListOf<OCRBox>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val rect = line.boundingBox
                val text = line.text.replace("\n", " ").trim()
                if (rect != null && text.isNotEmpty()) {
                    boxes.add(OCRBox(rect, text))
                }
            }
        }
        return boxes
    }

    fun detectBubbles(bitmap: Bitmap): List<Bubble> {
        if (bitmap.width == 0 || bitmap.height == 0) return emptyList()

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        if (mat.empty()) return emptyList()

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(mat, mat, Size(5.0, 5.0), 0.0)

        val th = Mat()
        Imgproc.adaptiveThreshold(
            mat, th, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 11, 8.0
        )

        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)
        )
        Imgproc.morphologyEx(th, th, Imgproc.MORPH_CLOSE, kernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            th, contours, hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val bubbles = mutableListOf<Bubble>()
        val screenArea = bitmap.width * bitmap.height

        for (c in contours) {
            val area = Imgproc.contourArea(c)

            // Filter area: 0.3% - 10% dari layar
            if (area < screenArea * 0.003 || area > screenArea * 0.1) continue

            val r = Imgproc.boundingRect(c)
            val aspect = r.width.toDouble() / r.height.toDouble()
            if (aspect > 8 || aspect < 0.125) continue

            bubbles.add(
                Bubble(c, Rect(r.x, r.y, r.x + r.width, r.y + r.height))
            )
        }
        return bubbles.sortedBy { it.boundingRect.top }
    }

    fun removeOriginalText(
        bitmap: Bitmap,
        bubbles: List<Bubble>,
        ocrBoxes: List<OCRBox>
    ): Bitmap {
        if (bitmap.width == 0 || bitmap.height == 0) return bitmap

        val srcMatRgba = Mat()
        Utils.bitmapToMat(bitmap, srcMatRgba)
        if (srcMatRgba.empty()) return bitmap

        val srcMat = Mat()
        Imgproc.cvtColor(srcMatRgba, srcMat, Imgproc.COLOR_RGBA2RGB)

        val mask = Mat.zeros(srcMat.size(), CvType.CV_8UC1)

        // Hapus teks di dalam bubble
        for (bubble in bubbles) {
            for (box in ocrBoxes) {
                if (isInsideBubble(box.rect, bubble.boundingRect, 0.4f)) {
                    val padding = max(10, (box.rect.width() * 0.05f).toInt())
                    val l = max(0, box.rect.left - padding)
                    val t = max(0, box.rect.top - padding)
                    val r = min(srcMat.cols() - 1, box.rect.right + padding)
                    val b = min(srcMat.rows() - 1, box.rect.bottom + padding)

                    Imgproc.rectangle(
                        mask,
                        Point(l.toDouble(), t.toDouble()),
                        Point(r.toDouble(), b.toDouble()),
                        Scalar(255.0),
                        -1
                    )
                }
            }
        }

        // Hapus teks bebas juga
        for (box in ocrBoxes) {
            val inBubble = bubbles.any { isInsideBubble(box.rect, it.boundingRect, 0.4f) }
            if (!inBubble) {
                val padding = max(8, (box.rect.width() * 0.04f).toInt())
                val l = max(0, box.rect.left - padding)
                val t = max(0, box.rect.top - padding)
                val r = min(srcMat.cols() - 1, box.rect.right + padding)
                val b = min(srcMat.rows() - 1, box.rect.bottom + padding)

                Imgproc.rectangle(
                    mask,
                    Point(l.toDouble(), t.toDouble()),
                    Point(r.toDouble(), b.toDouble()),
                    Scalar(255.0),
                    -1
                )
            }
        }

        val k = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0)
        )
        Imgproc.dilate(mask, mask, k)

        val inpainted = Mat()
        Photo.inpaint(srcMat, mask, inpainted, 5.0, Photo.INPAINT_NS)

        val out = Bitmap.createBitmap(
            inpainted.cols(),
            inpainted.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(inpainted, out)
        return out
    }

    fun drawTranslatedTextIntoBubbles(
        context: Context,
        baseBitmap: Bitmap,
        bubbles: List<Bubble>,
        ocrBoxes: List<OCRBox>,
        translations: Map<Rect, String>
    ): Bitmap {
        val result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val typeface = try {
            Typeface.createFromAsset(context.assets, "fonts/CCWildWords.ttf")
        } catch (e: Exception) {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        if (debugMode) {
            drawDebugOutlines(canvas, bubbles, ocrBoxes)
        }

        // RENDER TEKS SATU PER SATU
        for (box in ocrBoxes) {
            val translatedText = translations[box.rect] ?: continue
            drawTextInRect(canvas, translatedText, box.rect, typeface)
        }

        return result
    }

    private fun isInsideBubble(ocrRect: Rect, bubbleRect: Rect, threshold: Float = 0.4f): Boolean {
        val overlap = intersectionArea(ocrRect, bubbleRect)
        val ocrArea = ocrRect.width() * ocrRect.height()
        return overlap.toFloat() / ocrArea > threshold
    }

    private fun drawTextInRect(
        canvas: Canvas,
        text: String,
        rect: Rect,
        typeface: Typeface
    ) {
        // Warna adaptif
        val backgroundColor = getDominantBackgroundColor(canvas, rect)
        val isDarkBackground = isColorDark(backgroundColor)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            style = Paint.Style.STROKE
            strokeWidth = max(4f, rect.width() * 0.03f) // Stroke tebal
            textAlign = Paint.Align.CENTER
            color = if (isDarkBackground) Color.WHITE else Color.BLACK
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            color = if (isDarkBackground) Color.BLACK else Color.WHITE
        }

        val paddingX = rect.width() * 0.02f
        val paddingY = rect.height() * 0.02f
        val maxWidth = rect.width() - paddingX * 2
        val maxHeight = rect.height() - paddingY * 2

        // FONT LEBIH BESAR: maxSize 70f (dari 60f)
        var fontSize = estimateOptimalFontSize(text, maxWidth, maxHeight, 10f, 70f)
        strokePaint.textSize = fontSize
        fillPaint.textSize = fontSize

        var lines = wrapTextToLines(text, fillPaint, maxWidth)

        while (lines.size * (fillPaint.fontMetrics.descent - fillPaint.fontMetrics.ascent) > maxHeight && fontSize > 10f) {
            fontSize *= 0.92f
            strokePaint.textSize = fontSize
            fillPaint.textSize = fontSize
            lines = wrapTextToLines(text, fillPaint, maxWidth)
        }

        val fm = fillPaint.fontMetrics
        val lineHeight = fm.descent - fm.ascent
        val totalTextHeight = lineHeight * lines.size

        var startY = rect.top + paddingY - fm.ascent
        if (totalTextHeight < maxHeight) {
            startY = rect.top + (rect.height() - totalTextHeight) / 2 - fm.ascent
        }

        canvas.save()
        canvas.clipRect(
            rect.left + 2,
            rect.top + 2,
            rect.right - 2,
            rect.bottom - 2
        )

        val centerX = rect.centerX().toFloat()
        for (line in lines) {
            canvas.drawText(line, centerX, startY, strokePaint)
            canvas.drawText(line, centerX, startY, fillPaint)
            startY += lineHeight
        }

        canvas.restore()
    }

    private fun getDominantBackgroundColor(canvas: Canvas, rect: Rect): Int {
        return try {
            val bitmap = Bitmap.createBitmap(
                canvas.width,
                canvas.height,
                Bitmap.Config.ARGB_8888
            )
            val paint = Paint()
            val colors = intArrayOf(
                bitmap.getPixel(rect.left + 5, rect.top + 5),
                bitmap.getPixel(rect.right - 5, rect.top + 5),
                bitmap.getPixel(rect.left + 5, rect.bottom - 5),
                bitmap.getPixel(rect.right - 5, rect.bottom - 5)
            )
            colors.groupBy { it }.maxByOrNull { it.value.size }?.key ?: Color.WHITE
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun drawDebugOutlines(
        canvas: Canvas,
        bubbles: List<Bubble>,
        ocrBoxes: List<OCRBox>
    ) {
        val bubblePaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        val ocrPaint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        for (bubble in bubbles) {
            canvas.drawRect(bubble.boundingRect, bubblePaint)
        }

        for (box in ocrBoxes) {
            canvas.drawRect(box.rect, ocrPaint)
        }
    }

    // FONT LEBIH BESAR: maxSize 70f
    private fun estimateOptimalFontSize(
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        minSize: Float = 10f,
        maxSize: Float = 60f
    ): Float {
        var size = maxSize
        val testPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        while (true) {
            testPaint.textSize = size
            val lines = wrapTextToLines(text, testPaint, maxWidth)
            val fm = testPaint.fontMetrics
            val totalHeight = (fm.descent - fm.ascent) * lines.size

            if (totalHeight <= maxHeight || size <= minSize) break
            size -= 1f
        }
        return max(size, minSize)
    }

    private fun intersectionArea(a: Rect, b: Rect): Int {
        val l = max(a.left, b.left)
        val r = min(a.right, b.right)
        val t = max(a.top, b.top)
        val bt = min(a.bottom, b.bottom)
        return if (r > l && bt > t) (r - l) * (bt - t) else 0
    }

    private fun wrapTextToLines(
        text: String,
        paint: Paint,
        maxWidth: Float
    ): List<String> {
        if (text.isEmpty()) return emptyList()

        val words = text.split(" ").filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = ""

        for (w in words) {
            val test = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(test) <= maxWidth) {
                current = test
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = w
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }
}
