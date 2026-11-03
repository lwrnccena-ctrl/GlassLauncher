package com.fares.glass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Your photo, filling the screen. Plain black until a photo is found in Glass/depth/. */
@Composable
fun DepthBackground(image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
    } else {
        Box(modifier.background(Color(0xFF101418)))
    }
}

/** The big clock and date. It is drawn between the photo and the foreground cutout. */
@Composable
fun DepthClock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val time = remember(now / 60_000L) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 3_600_000L) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val ink = Color(0xFFCFE3E8)
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                date,
                style = TextStyle(color = ink.copy(alpha = 0.9f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            )
            BasicText(
                time,
                style = TextStyle(color = ink, fontSize = 168.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }
    }
}
