package com.fares.glass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * City picker. Step 1 (typing): type a place with the on-screen keyboard and press Done.
 * Step 2: choose from the matches. The first row, "Detect automatically", goes back to the automatic location.
 * selected: 0 = "Detect automatically", 1.. = the matches in labels.
 */
@Composable
fun LocationDialog(
    backdrop: Backdrop,
    typing: Boolean,
    labels: List<String>,
    selected: Int,
    status: String?,
    onSearch: (String) -> Unit,
) {
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(typing) {
        if (typing) {
            field.requestFocus()
            keyboard?.show()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Glass(backdrop, Modifier.width(440.dp), RoundedCornerShape(32.dp), tint = Color.White.copy(alpha = 0.10f)) {
            Column(Modifier.padding(24.dp)) {
                BasicText("Your city", style = txt(Color.White, 22.sp, FontWeight.Bold))
                BasicText(
                    "Type a place, press Done, then choose it from the list",
                    style = txt(Color.White.copy(alpha = 0.7f), 14.sp, FontWeight.Medium),
                )
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = txt(Color(0xFF111114), 18.sp, FontWeight.Medium),
                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        onSearch(text.text.trim())
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(10.dp))
                if (typing) {
                    BasicText(
                        "Back closes this window.",
                        style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium),
                    )
                } else {
                    status?.let {
                        BasicText(
                            it,
                            Modifier.padding(start = 4.dp, bottom = 6.dp),
                            style = txt(Color.White.copy(alpha = 0.8f), 13.sp, FontWeight.Medium),
                        )
                    }
                    PickRow("Detect automatically", selected == 0)
                    labels.forEachIndexed { i, label -> PickRow(label, selected == i + 1) }
                    BasicText(
                        "OK chooses · Back to type again",
                        Modifier.padding(start = 4.dp, top = 8.dp),
                        style = txt(Color.White.copy(alpha = 0.55f), 11.sp, FontWeight.Medium),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickRow(label: String, on: Boolean) {
    BasicText(
        label,
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (on) Color.White.copy(alpha = 0.95f) else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = txt(if (on) Color(0xFF111114) else Color.White, 14.sp, FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
