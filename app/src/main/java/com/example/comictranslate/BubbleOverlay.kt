package com.example.comictranslate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BubbleText(text: String, modifier: Modifier = Modifier) {
    // State to hold the dynamically adjusted font size
    var fontSize by remember { mutableStateOf(16.sp) }
    // State to hold the final text layout result
    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    Box(
        modifier = modifier
            .background(
                color = Color.LightGray,
                shape = RoundedCornerShape(8.dp)
            )
            .drawBehind {
                // Draw the bubble's tail
                val tailPath = Path().apply {
                    moveTo(size.width / 2 - 15, size.height)
                    lineTo(size.width / 2, size.height + 30)
                    lineTo(size.width / 2 + 15, size.height)
                    close()
                }
                drawPath(tailPath, color = Color.LightGray)
            }
            .padding(8.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            maxLines = 5, // Set a max number of lines to prevent infinite loops
            overflow = TextOverflow.Ellipsis,
            onTextLayout = {
                // This callback is triggered when the text is laid out.
                // We check if the text has overflown its container.
                if (it.hasVisualOverflow && fontSize.value > 8) {
                    // If it overflows, reduce the font size by a small amount.
                    // This will trigger a re-composition and re-layout.
                    fontSize = (fontSize.value - 1).sp
                } else {
                    // If it fits, we store the layout result.
                    textLayoutResult = it
                }
            }
        )
    }
}