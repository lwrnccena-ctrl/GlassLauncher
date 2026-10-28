package com.fares.glass.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

private val BarInk = Color(0xFF111114)

/**
 * The buttons at the top right of the home screen, icon only, in circles:
 * 0 Wi-Fi, 1 Bluetooth, 2 TV input, 3 Settings. selected = -1 means none is selected.
 */
@Composable
fun IconBar(backdrop: Backdrop, selected: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BarIcon(backdrop, Icons.Rounded.Wifi, selected == 0)
        BarIcon(backdrop, Icons.Rounded.Bluetooth, selected == 1)
        BarIcon(backdrop, Icons.Rounded.SettingsInputHdmi, selected == 2)
        BarIcon(backdrop, Icons.Rounded.Settings, selected == 3)
    }
}

@Composable
private fun BarIcon(backdrop: Backdrop, icon: ImageVector, focused: Boolean) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 650f), label = "barFocus")
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = 1f + 0.12f * f; scaleY = 1f + 0.12f * f },
        contentAlignment = Alignment.Center,
    ) {
        Glass(
            backdrop,
            Modifier.matchParentSize(),
            shape = CircleShape,
            tint = Color.White.copy(alpha = 0.10f + 0.80f * f),
        )
        Image(
            rememberVectorPainter(icon), null, Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(lerp(Color.White, BarInk, f)),
        )
    }
}
