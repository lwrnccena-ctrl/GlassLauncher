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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Asks for the name that is shown after the greeting. onDone gets the typed name when Done is pressed. */
@Composable
fun NameDialog(backdrop: Backdrop, initial: String, onDone: (String) -> Unit) {
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }

    LaunchedEffect(Unit) {
        field.requestFocus()
        keyboard?.show()
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Glass(backdrop, Modifier.width(440.dp), RoundedCornerShape(32.dp), tint = Color.White.copy(alpha = 0.10f)) {
            Column(Modifier.padding(24.dp)) {
                BasicText("Your name", style = txt(Color.White, 22.sp, FontWeight.Bold))
                BasicText("Shown after the greeting", style = txt(Color.White.copy(alpha = 0.7f), 14.sp, FontWeight.Medium))
                Spacer(Modifier.height(16.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = txt(Color(0xFF111114), 18.sp, FontWeight.Medium),
                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        onDone(text.text.trim())
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(10.dp))
                BasicText(
                    "Type with the on-screen keyboard, then press Done. Back cancels.",
                    style = txt(Color.White.copy(alpha = 0.6f), 12.sp, FontWeight.Medium),
                )
            }
        }
    }
}
