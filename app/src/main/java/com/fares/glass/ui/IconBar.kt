package com.fares.glass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val BarInk = Color(0xFF111114)

/**
 * The buttons at the top right of the home screen, icon only, in circles:
 * 0 Wi-Fi, 1 Bluetooth, 2 TV input, 3 Settings. selected = -1 means none is selected.
 * visible = false shrinks the icons away one by one; true brings them back.
 * plain = true (Simple mode) draws only the icons, with no glass circles behind them.
 */
@Composable
fun IconBar(
    backdrop: Backdrop,
    selected: Int,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    plain: Boolean = false,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BarIcon(backdrop, Icons.Rounded.Wifi, selected == 0, visible, 0, plain)
        BarIcon(backdrop, Icons.Rounded.Bluetooth, selected == 1, visible, 1, plain)
        BarIcon(backdrop, Icons.Rounded.SettingsInputHdmi, selected == 2, visible, 2, plain)
        BarIcon(backdrop, Icons.Rounded.Settings, selected == 3, visible, 3, plain)
    }
}

@Composable
private fun BarIcon(
    backdrop: Backdrop,
    icon: ImageVector,
    focused: Boolean,
    visible: Boolean,
    index: Int,
    plain: Boolean = false,
) {
    val f by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 650f), label = "barFocus")

    val show = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(120L + index * 50L)
            show.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
        } else {
            delay((3 - index) * 40L)
            show.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                val s = (1f + 0.12f * f) * (0.2f + 0.8f * show.value)
                scaleX = s
                scaleY = s
                alpha = show.value.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!plain) {
            Glass(
                backdrop,
                Modifier.matchParentSize(),
                shape = CircleShape,
                tint = Color.White.copy(alpha = 0.10f + 0.80f * f),
            )
        } else {
            // Simple mode: no circle, just a thin white ring while the icon is selected.
            Box(
                Modifier
                    .matchParentSize()
                    .border(2.dp, Color.White.copy(alpha = f), CircleShape),
            )
        }
        Image(
            rememberVectorPainter(icon), null, Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(if (plain) Color.White else lerp(Color.White, BarInk, f)),
        )
    }
}
