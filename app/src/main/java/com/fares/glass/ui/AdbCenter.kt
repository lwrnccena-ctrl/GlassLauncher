package com.fares.glass.ui

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.glass.sys.AdbController
import kotlinx.coroutines.launch

private fun secureGranted(c: Context) =
    c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

private fun listenerOn(c: Context) =
    (Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: "")
        .contains(c.packageName)

/** Small button for the Control Center header (top-right). */
@Composable
fun AdbHeaderButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AdbTile("ADB", "Setup", false, onClick, modifier.width(96.dp).height(64.dp))
}

/** Second Control Center. Show it from ControlCenter with `if (showAdb) AdbCenter { showAdb = false }`. */
@Composable
fun AdbCenter(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Connecting to ADB…") }
    var secure by remember { mutableStateOf(secureGranted(ctx)) }
    var listener by remember { mutableStateOf(listenerOn(ctx)) }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }

    fun refresh() { secure = secureGranted(ctx); listener = listenerOn(ctx) }

    fun exec(cmd: String) {
        if (busy) return
        busy = true; message = "Running…"
        scope.launch {
            val r = AdbController.run(ctx, cmd)
            connected = if (r.ok) true else connected
            message = if (r.ok) "Done" else r.output
            refresh(); busy = false
        }
    }

    // Auto-connect as soon as the panel opens.
    LaunchedEffect(Unit) {
        val r = AdbController.connect(ctx)
        connected = r.ok
        message = if (r.ok) "Connected to ADB"
        else "Not paired yet. Settings → Developer options → Wireless debugging → ON → “Pair device with pairing code”, then enter the port and code below."
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.padding(24.dp).width(420.dp)
                .background(Color(0xCC2A2D33), RoundedCornerShape(32.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ADB settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = Color(0xCCFFFFFF), fontSize = 13.sp)

            if (connected != true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pairPort, { pairPort = it.filter(Char::isDigit) },
                        label = { Text("Pairing port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(pairCode, { pairCode = it.filter(Char::isDigit) },
                        label = { Text("6-digit code") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                }
                AdbTile("Pair", "Use the code above", false, {
                    val port = pairPort.toIntOrNull()
                    if (!busy && port != null && pairCode.length == 6) {
                        busy = true; message = "Pairing…"
                        scope.launch { val r = AdbController.pair(ctx, port, pairCode)
                            connected = r.ok; message = r.output; busy = false }
                    } else message = "Enter the port and the 6-digit code first."
                }, Modifier.fillMaxWidth().height(64.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdbTile(
                    "ADB", when (connected) { true -> "Connected"; false -> "Reconnect"; null -> "…" },
                    connected == true,
                    { if (!busy) { busy = true; message = "Connecting…"
                        scope.launch { val r = AdbController.connect(ctx); connected = r.ok
                            message = if (r.ok) "Connected to ADB" else r.output; busy = false } } },
                    Modifier.weight(1f).height(84.dp)
                )
                AdbTile("Secure settings", if (secure) "Granted" else "Grant", secure,
                    { exec(AdbController.CMD_WRITE_SECURE) }, Modifier.weight(1f).height(84.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdbTile("Media listener", if (listener) "Allowed" else "Allow", listener,
                    { exec(AdbController.CMD_MEDIA_LISTENER) }, Modifier.weight(1f).height(84.dp))
                AdbTile("Close", "Back", false, onClose, Modifier.weight(1f).height(84.dp))
            }
        }
    }
}

@Composable
private fun AdbTile(title: String, subtitle: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .scale(if (focused) 1.06f else 1f)
            .background(if (on) Color(0xEEFFFFFF) else Color(0x33FFFFFF), shape)
            .border(BorderStroke(if (focused) 2.dp else 0.dp, Color.White), shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val fg = if (on) Color(0xFF1B1D21) else Color.White
        Text(title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = fg.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}
