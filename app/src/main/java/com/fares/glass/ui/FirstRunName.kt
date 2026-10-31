package com.fares.glass.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Hello" in many languages, written one after another on the first screen. */
private val Greetings = listOf(
    "Hello", "Hola", "Bonjour", "你好", "こんにちは", "안녕하세요", "Ciao",
    "Hallo", "Olá", "Привет", "مرحبا", "नमस्ते", "Kumusta",
)

/** Colors of the written word. */
private val HelloBrush = Brush.linearGradient(
    listOf(Color(0xFF0A84FF), Color(0xFF8E5CF7), Color(0xFFFF5FA2), Color(0xFFFF9F0A)),
)

// Hello cycle timing: the word is written, stays, then fades out before the next one starts.
private const val WriteMs = 1100
private const val HoldMs = 1000L
private const val FadeMs = 450
private val WriteEase = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

// Name card timing: opening takes this long in total (card + the last line to arrive). Closing is faster.
private const val InMs = 1000
private const val OutMs = 420

/** Slight settle at the end, like the Control Center. */
private val Settle = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1.08f)
private val Smooth = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** Local progress (0..1, eased) of one piece of the name card's opening timeline. [t] is the opening progress (0..1). */
private fun introStage(t: Float, delayMs: Float, durMs: Float, ease: Easing): Float =
    ease.transform(((t * InMs - delayMs) / durMs).coerceIn(0f, 1f))

/** A line that rises a little and fades in, later for lines with a bigger [delayMs]. */
private fun Modifier.rise(enter: Animatable<Float, *>, delayMs: Float): Modifier = graphicsLayer {
    val p = introStage(enter.value, delayMs, 480f, Smooth)
    alpha = p
    translationY = (1f - p) * 14.dp.toPx()
}

/**
 * One greeting. [write] (0..1) is how much of it has been written: a soft edge sweeps from left to
 * right and reveals the word, like handwriting. [fade] (0..1) fades it out with a tiny zoom.
 * Both are read only while drawing, so the animation never recomposes the screen.
 */
@Composable
private fun HelloWord(text: String, write: () -> Float, fade: () -> Float) {
    BasicText(
        text,
        Modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                val f = fade()
                alpha = 1f - f
                val s = 1f + 0.04f * f
                scaleX = s
                scaleY = s
            }
            .drawWithContent {
                drawContent()
                val soft = 0.18f
                val edge = write() * (1f + soft)
                val s1 = edge.coerceIn(0f, 1f)
                val s0 = (edge - soft).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black, s0 to Color.Black, s1 to Color.Transparent, 1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        style = TextStyle(
            brush = HelloBrush,
            fontSize = 96.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
    )
}

/**
 * Shown once, the first time Glass is opened.
 *
 * 1. Hello cycle: a black screen where "Hello" is written in many languages, one after another.
 *    Press OK to begin.
 * 2. Name card: the black lifts to a dim over the home screen, a card grows and settles in the middle,
 *    and its lines appear one by one: "Hello", a question and a box for the name.
 *
 * Closing (after Done): [onSave] is called right away, so the greeting behind already shows the name,
 * while the card shrinks and fades and the dim lifts. [onFinished] is called when the closing ends.
 * Only alpha, scale, translation and a mask are animated, so it stays smooth on a TV.
 */
@Composable
fun FirstRunName(onSave: (String) -> Unit, onFinished: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) } // 0 = Hello cycle, 1 = name card
    var wordIdx by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var leaving by remember { mutableStateOf(false) }

    val blackIn = remember { Animatable(0f) }
    val write = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val hint = remember { Animatable(0f) }
    val toName = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()
    val root = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        root.requestFocus()
        launch { blackIn.animateTo(1f, tween(500)) }
        delay(1800)
        hint.animateTo(1f, tween(600))
    }

    // Stage 0: write a greeting, hold it, fade it, next language.
    LaunchedEffect(stage) {
        if (stage != 0) return@LaunchedEffect
        while (true) {
            write.snapTo(0f)
            fade.snapTo(0f)
            write.animateTo(1f, tween(WriteMs, easing = WriteEase))
            delay(HoldMs)
            fade.animateTo(1f, tween(FadeMs, easing = FastOutSlowInEasing))
            wordIdx = (wordIdx + 1) % Greetings.size
        }
    }

    // Stage 1: the black lifts, the card opens, the keyboard comes up.
    LaunchedEffect(stage) {
        if (stage != 1) return@LaunchedEffect
        launch { toName.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        withFrameNanos { }
        field.requestFocus()
        launch { delay(600); keyboard?.show() }
        delay(150)
        enter.animateTo(1f, tween(InMs, easing = LinearEasing))
    }

    fun submit() {
        val n = name.trim()
        if (n.isEmpty() || leaving) return
        leaving = true
        keyboard?.hide()
        onSave(n)
        scope.launch {
            exit.animateTo(1f, tween(OutMs, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(root)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = stage == 0,
            ) { stage = 1 },
        contentAlignment = Alignment.Center,
    ) {
        // Black at first, relaxing to a dim over the home screen when the name card opens.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val base = 1f - 0.35f * toName.value
                    alpha = blackIn.value * base * (1f - exit.value)
                }
                .background(Color.Black),
        )

        // Stage 0: the Hello cycle.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blackIn.value * (1f - toName.value) },
            contentAlignment = Alignment.Center,
        ) {
            HelloWord(Greetings[wordIdx], { write.value }, { fade.value })
            BasicText(
                "Press OK to begin",
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .graphicsLayer { alpha = hint.value },
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }

        // Stage 1: the name card.
        if (stage == 1) {
            Column(
                Modifier
                    .width(460.dp)
                    .graphicsLayer {
                        val p = introStage(enter.value, 80f, 640f, Settle)
                        val o = exit.value
                        val s = (0.9f + 0.1f * p) * (1f - 0.06f * o)
                        scaleX = s
                        scaleY = s
                        translationY = (1f - p) * 36.dp.toPx() - o * 18.dp.toPx()
                        alpha = introStage(enter.value, 80f, 360f, Smooth) * (1f - o)
                    }
                    .background(Color(0xFF1C1C21), RoundedCornerShape(32.dp))
                    .padding(28.dp),
            ) {
                BasicText(
                    "Hello",
                    Modifier.rise(enter, 220f),
                    style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "What should I call you?",
                    Modifier.rise(enter, 300f),
                    style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(18.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { if (!leaving && it.length <= 20) name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF111114), fontSize = 20.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .rise(enter, 380f)
                        .focusRequester(field)
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Spacer(Modifier.height(12.dp))
                BasicText(
                    "Type your name, then press Done.",
                    Modifier.rise(enter, 460f),
                    style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}
