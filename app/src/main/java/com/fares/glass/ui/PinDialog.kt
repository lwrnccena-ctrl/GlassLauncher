package com.fares.glass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the PIN dialog is asking for. */
enum class PinMode { Enter, SetNew, Confirm }

/**
 * A 4-digit PIN dialog that works with a TV remote:
 * Up / Down change the digit, Right or OK go to the next one (OK on the last digit confirms),
 * the number keys type a digit, Left goes back one digit, Back cancels.
 */
@Composable
fun PinDialog(
    backdrop: Backdrop,
    title: String,
    message: String,
    digits: List<Int>,
    position: Int,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Glass(backdrop, Modifier.width(320.dp), RoundedCornerShape(28.dp), tint = Color.White.copy(alpha = 0.10f)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(title, style = txt(Color.White, 20.sp, FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                BasicText(
                    message.ifBlank { " " },
                    style = txt(Color.White.copy(alpha = 0.7f), 13.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    digits.forEachIndexed { i, d ->
                        val current = i == position
                        val fg = if (current) Color(0xFF111114) else Color.White
                        Box(
                            Modifier
                                .size(width = 52.dp, height = 64.dp)
                                .background(
                                    if (current) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(16.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                when {
                                    current -> d.toString()
                                    i < position -> "\u2022"
                                    else -> "\u2013"
                                },
                                style = txt(fg.copy(alpha = if (i > position) 0.4f else 1f), 28.sp, FontWeight.Bold),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                BasicText(
                    "\u2191\u2193 digit \u00B7 \u2192 next \u00B7 OK confirm \u00B7 Back cancel",
                    style = txt(Color.White.copy(alpha = 0.55f), 11.sp, FontWeight.Medium),
                )
            }
        }
    }
}
