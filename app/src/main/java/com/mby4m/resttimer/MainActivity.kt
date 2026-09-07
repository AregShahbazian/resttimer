package com.mby4m.resttimer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val Bg = Color(0xFF12141A)
private val Surface = Color(0xFF1C1F27)
private val Accent = Color(0xFF4ADE80)
private val AccentDim = Color(0xFF2A3A31)
private val TextHi = Color(0xFFF2F4F8)
private val TextLo = Color(0xFF8A90A0)

private val PRESETS = listOf(30, 45, 60, 90, 120, 180)
private const val PREFS = "rest_timer_prefs"
private const val KEY_LAST = "last_seconds"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RestTimerApp() }
    }
}

/** Fires a short buzz + beep when a set is done. */
private fun alertDone(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    val pattern = longArrayOf(0, 250, 150, 250)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
        tone.release()
    }
}

private fun formatTime(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun RestTimerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var totalSeconds by rememberSaveable { mutableIntStateOf(prefs.getInt(KEY_LAST, 60)) }
    var remainingMs by rememberSaveable { mutableLongStateOf(totalSeconds * 1000L) }
    var running by rememberSaveable { mutableStateOf(false) }
    var justFinished by rememberSaveable { mutableStateOf(false) }

    // Keep the screen awake only while a set is actually counting down.
    val window = (context as? ComponentActivity)?.window
    DisposableEffect(running) {
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Deadline-based countdown: immune to drift from delay() overshoot.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val deadline = SystemClock.elapsedRealtime() + remainingMs
        while (isActive) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                remainingMs = 0
                running = false
                justFinished = true
                alertDone(context)
                break
            }
            remainingMs = left
            delay(50)
        }
    }

    fun selectPreset(seconds: Int) {
        totalSeconds = seconds
        remainingMs = seconds * 1000L
        running = false
        justFinished = false
        prefs.edit().putInt(KEY_LAST, seconds).apply()
    }

    fun adjust(deltaSeconds: Int) {
        val next = (totalSeconds + deltaSeconds).coerceIn(5, 3600)
        selectPreset(next)
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface)) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "REST TIMER",
                    color = TextLo,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.weight(1f))

                TimerDial(
                    remainingMs = remainingMs,
                    totalMs = totalSeconds * 1000L,
                    finished = justFinished
                )

                Spacer(Modifier.weight(1f))

                AdjustRow(
                    totalSeconds = totalSeconds,
                    enabled = !running,
                    onAdjust = ::adjust
                )

                Spacer(Modifier.height(20.dp))

                PresetRow(
                    selected = totalSeconds,
                    enabled = !running,
                    onSelect = ::selectPreset
                )

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (justFinished) {
                                remainingMs = totalSeconds * 1000L
                                justFinished = false
                            }
                            running = !running
                        },
                        modifier = Modifier
                            .weight(2f)
                            .height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                    ) {
                        Text(
                            when {
                                running -> "PAUSE"
                                justFinished -> "AGAIN"
                                remainingMs < totalSeconds * 1000L -> "RESUME"
                                else -> "START"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            running = false
                            justFinished = false
                            remainingMs = totalSeconds * 1000L
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi)
                    ) {
                        Text("RESET", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TimerDial(remainingMs: Long, totalMs: Long, finished: Boolean) {
    val progress = if (totalMs <= 0L) 0f else (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = AccentDim,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Accent,
                startAngle = -90f,
                sweepAngle = -360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatTime(remainingMs),
                color = if (finished) Accent else TextHi,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
            if (finished) {
                Text(
                    "DONE",
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
        }
    }
}

@Composable
private fun AdjustRow(totalSeconds: Int, enabled: Boolean, onAdjust: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepButton("−15", enabled) { onAdjust(-15) }
        Text(
            "${totalSeconds}s set",
            color = TextLo,
            fontSize = 14.sp,
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.Center
        )
        StepButton("+15", enabled) { onAdjust(15) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PresetRow(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PRESETS.forEach { seconds ->
            val isSelected = seconds == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(
                        color = if (isSelected) Accent else Surface,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { onSelect(seconds) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60}m" else "${seconds}s",
                        color = if (isSelected) Bg else TextHi,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
