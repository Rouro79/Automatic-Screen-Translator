package com.example.comictranslate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

@Composable
fun TranslationOverlayView(translatedTexts: List<TranslatedText>) {
    val density = LocalDensity.current

    translatedTexts.forEach { item ->
        val box = item.boundingBox

        // Overlay anchor = top-left of detected bubble
        val offset = IntOffset(box.left, box.top)

        // Limit overlay width to bubble interior
        val maxWidthDp = with(density) { box.width().toDp() - 6.dp }

        Popup(
            offset = offset,
            // ensures overlay stays within bubble area
            onDismissRequest = {}
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidthDp)  // keep inside bubble
                    .background(
                        Color.White.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(6.dp)
            ) {
                Text(
                    text = item.translatedText,
                    color = Color.Black,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    softWrap = true,
                    maxLines = 10
                )
            }
        }
    }
}