package com.example.voiceassistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voiceassistant.ui.theme.KalkiAlertRed
import com.example.voiceassistant.ui.theme.KalkiElectricBlue
import com.example.voiceassistant.ui.theme.KalkiEmerald
import com.example.voiceassistant.ui.theme.KalkiGlassBorder
import com.example.voiceassistant.ui.theme.KalkiGlassSurface
import com.example.voiceassistant.ui.theme.KalkiMidnight
import com.example.voiceassistant.ui.theme.KalkiNeonCyan
import com.example.voiceassistant.ui.theme.KalkiNeonPurple
import com.example.voiceassistant.ui.theme.KalkiNeonViolet
import com.example.voiceassistant.ui.theme.KalkiObsidian
import com.example.voiceassistant.ui.theme.KalkiTextMuted
import com.example.voiceassistant.ui.theme.KalkiTextPrimary
import com.example.voiceassistant.ui.theme.KalkiTextSecondary
import kotlin.math.sin

@Composable
fun TapToTalkScreen(
    viewModel: ConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val isListening = uiState.state == AssistantState.LISTENING || uiState.state == AssistantState.WAKE_DETECTED
    val isThinking = uiState.state == AssistantState.THINKING
    val isSpeaking = uiState.state == AssistantState.SPEAKING
    val isError = uiState.state == AssistantState.ERROR

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        KalkiObsidian,
                        KalkiMidnight,
                        KalkiObsidian
                    )
                )
            )
    ) {
        // Ambient background glowing orbs
        AmbientGlowEffect()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Cockpit Status Bar
            CabinTopBar()

            // 2. Dynamic State Headline
            StateHeadline(state = uiState.state)

            // 3. Floating Speech & Conversation Glass Card
            ConversationGlassCard(
                lastTranscript = uiState.lastTranscript,
                lastReply = uiState.lastReply,
                state = uiState.state,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // 4. Center Holographic Animated AI Core
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                HolographicAiCore(state = uiState.state)
            }

            // 5. Bottom Control Dock (Quick Chips + Mic Action Button)
            BottomControlDock(
                state = uiState.state,
                isListening = isListening,
                isThinking = isThinking,
                isError = isError,
                errorMessage = uiState.errorMessage,
                onMicClick = { viewModel.onTapToTalkPressed() },
                onChipClick = { query -> viewModel.sendQuickQuery(query) },
                onDismissError = { viewModel.clearError() }
            )
        }
    }
}

// =========================================================
// 1. CABIN TOP BAR
// =========================================================

@Composable
private fun CabinTopBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "top_dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: KALKI AI Branding
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(KalkiNeonCyan, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "KALKI AI",
                color = KalkiTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }

        // Right: Vehicle Cabin Status Badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(KalkiGlassSurface)
                .border(1.dp, KalkiGlassBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(KalkiEmerald.copy(alpha = dotAlpha), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AV CABIN: ONLINE",
                color = KalkiEmerald,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

// =========================================================
// 2. STATE HEADLINE
// =========================================================

@Composable
private fun StateHeadline(state: AssistantState) {
    val titleText: String
    val subtitleText: String
    val accentColor: Color

    when (state) {
        AssistantState.SLEEPING -> {
            titleText = "Say \"Hey Kalki\""
            subtitleText = "Autonomous Cabin Assistant Ready"
            accentColor = KalkiNeonCyan
        }
        AssistantState.WAKE_DETECTED -> {
            titleText = "I'm listening"
            subtitleText = "Go ahead, speak naturally"
            accentColor = KalkiNeonCyan
        }
        AssistantState.LISTENING -> {
            titleText = "Listening to you…"
            subtitleText = "Microphone active — 5s pause to send"
            accentColor = KalkiNeonCyan
        }
        AssistantState.THINKING -> {
            titleText = "Analyzing…"
            subtitleText = "Evaluating route & cabin safety"
            accentColor = KalkiElectricBlue
        }
        AssistantState.SPEAKING -> {
            titleText = "KALKI is speaking"
            subtitleText = "Audio guidance in progress"
            accentColor = KalkiNeonPurple
        }
        AssistantState.ERROR -> {
            titleText = "Connection Notice"
            subtitleText = "Tap the microphone to reconnect"
            accentColor = KalkiAlertRed
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = titleText,
            color = accentColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitleText,
            color = KalkiTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

// =========================================================
// 3. CONVERSATION GLASS CARD
// =========================================================

@Composable
private fun ConversationGlassCard(
    lastTranscript: String,
    lastReply: String,
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(KalkiGlassSurface)
            .border(1.dp, KalkiGlassBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        if (lastTranscript.isBlank() && lastReply.isBlank()) {
            // Default Welcome Prompt
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome to your Autonomous Journey",
                    color = KalkiNeonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ask about the route, traffic conditions, weather alerts, or vehicle safety score.",
                    color = KalkiTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (lastTranscript.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "YOU",
                            color = KalkiElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KalkiElectricBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = lastTranscript,
                            color = KalkiTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (lastTranscript.isNotBlank() && lastReply.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(KalkiGlassBorder)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (lastReply.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "KALKI",
                            color = KalkiNeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KalkiNeonCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = lastReply,
                            color = KalkiTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================
// 4. HOLOGRAPHIC AI CORE & WAVEFORM VISUALIZER
// =========================================================

@Composable
private fun HolographicAiCore(state: AssistantState) {
    val infiniteTransition = rememberInfiniteTransition(label = "holographic_core")

    // Pulsing energy scales
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale_1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) 1.4f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale_2"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 900
                    AssistantState.SPEAKING -> 1100
                    AssistantState.THINKING -> 700
                    else -> 2200
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val coreColors = when (state) {
        AssistantState.LISTENING -> listOf(KalkiNeonCyan, KalkiElectricBlue)
        AssistantState.SPEAKING -> listOf(KalkiNeonPurple, KalkiNeonViolet)
        AssistantState.THINKING -> listOf(KalkiElectricBlue, KalkiNeonPurple)
        AssistantState.ERROR -> listOf(KalkiAlertRed, KalkiNeonViolet)
        else -> listOf(KalkiNeonCyan, KalkiMidnight)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2.2f

        // Outer ambient halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColors[0].copy(alpha = 0.25f),
                    coreColors[1].copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius * pulseScale2
            ),
            radius = maxRadius * pulseScale2,
            center = center
        )

        // Middle energy shockwave ring
        drawCircle(
            color = coreColors[0].copy(alpha = 0.35f),
            radius = (maxRadius * 0.75f) * pulseScale1,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Concentric inner ring
        drawCircle(
            color = coreColors[1].copy(alpha = 0.5f),
            radius = (maxRadius * 0.52f) * pulseScale1,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Core Glowing Orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColors[0],
                    coreColors[1],
                    KalkiObsidian
                ),
                center = center,
                radius = maxRadius * 0.36f
            ),
            radius = maxRadius * 0.36f,
            center = center
        )

        // Sinusoidal Holographic Audio Wave Line
        val waveAmplitude = when (state) {
            AssistantState.LISTENING -> 26.dp.toPx()
            AssistantState.SPEAKING -> 20.dp.toPx()
            AssistantState.THINKING -> 12.dp.toPx()
            else -> 6.dp.toPx()
        }

        val wavePath = Path()
        val waveWidth = size.width * 0.9f
        val startX = (size.width - waveWidth) / 2f

        for (x in 0..waveWidth.toInt() step 4) {
            val progress = x / waveWidth
            // Windowing envelope so wave tapers off at ends
            val envelope = sin(progress * Math.PI).toFloat()
            val y = center.y + sin((progress * 4 * Math.PI).toFloat() + wavePhase) * waveAmplitude * envelope

            if (x == 0) {
                wavePath.moveTo(startX + x, y)
            } else {
                wavePath.lineTo(startX + x, y)
            }
        }

        drawPath(
            path = wavePath,
            color = coreColors[0].copy(alpha = 0.9f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// =========================================================
// 5. BOTTOM CONTROL DOCK
// =========================================================

@Composable
private fun BottomControlDock(
    state: AssistantState,
    isListening: Boolean,
    isThinking: Boolean,
    isError: Boolean,
    errorMessage: String?,
    onMicClick: () -> Unit,
    onChipClick: (String) -> Unit,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Error Notice Bar (if any error)
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(KalkiAlertRed.copy(alpha = 0.15f))
                    .border(1.dp, KalkiAlertRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = errorMessage ?: "Connection error. Tap mic to retry.",
                    color = KalkiAlertRed,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Retry",
                        tint = KalkiAlertRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Quick Suggestion Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            QuickChip(label = "🚦 Route options", query = "What route options do we have?", onClick = onChipClick)
            Spacer(modifier = Modifier.width(8.dp))
            QuickChip(label = "🗺️ Traffic status", query = "How is the traffic ahead?", onClick = onChipClick)
            Spacer(modifier = Modifier.width(8.dp))
            QuickChip(label = "🛡️ Safety score", query = "What is our safety score?", onClick = onChipClick)
            Spacer(modifier = Modifier.width(8.dp))
            QuickChip(label = "🤖 Who are you?", query = "Who are you?", onClick = onChipClick)
        }

        // Illuminated Floating Mic Button
        GlowingMicButton(
            isListening = isListening,
            isThinking = isThinking,
            onClick = onMicClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tactile Action Label
        Text(
            text = when {
                isListening -> "TAP TO FINISH & SEND"
                isThinking -> "PROCESSING QUERY…"
                else -> "TAP TO TALK"
            },
            color = if (isListening) KalkiNeonCyan else KalkiTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

// =========================================================
// QUICK CHIP COMPONENT
// =========================================================

@Composable
private fun QuickChip(
    label: String,
    query: String,
    onClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KalkiGlassSurface)
            .border(1.dp, KalkiGlassBorder, RoundedCornerShape(16.dp))
            .clickable { onClick(query) }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = KalkiTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// =========================================================
// GLOWING ILLUMINATED MIC BUTTON
// =========================================================

@Composable
private fun GlowingMicButton(
    isListening: Boolean,
    isThinking: Boolean,
    onClick: () -> Unit
) {
    val buttonSize = 78.dp

    val infiniteTransition = rememberInfiniteTransition(label = "mic_glow_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.28f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse_scale"
    )

    val buttonBrush = if (isListening) {
        Brush.linearGradient(listOf(KalkiAlertRed, KalkiNeonViolet))
    } else {
        Brush.linearGradient(listOf(KalkiNeonCyan, KalkiElectricBlue))
    }

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing energy ring
        Box(
            modifier = Modifier
                .size(buttonSize * pulseScale)
                .background(
                    color = (if (isListening) KalkiAlertRed else KalkiNeonCyan).copy(alpha = 0.15f),
                    shape = CircleShape
                )
        )

        // Middle soft glow border
        Box(
            modifier = Modifier
                .size(buttonSize + 10.dp)
                .border(
                    width = 1.5.dp,
                    color = (if (isListening) KalkiAlertRed else KalkiNeonCyan).copy(alpha = 0.35f),
                    shape = CircleShape
                )
        )

        // Core Floating Action Surface
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(buttonBrush)
                .semantics {
                    contentDescription = if (isListening) "Stop listening and send" else "Talk to KALKI"
                }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = KalkiObsidian,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// =========================================================
// AMBIENT BACKGROUND GLOW EFFECT
// =========================================================

@Composable
private fun AmbientGlowEffect() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Subtle Cyan glow at top right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    KalkiNeonCyan.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.9f, size.height * 0.1f),
                radius = size.width * 0.6f
            ),
            radius = size.width * 0.6f,
            center = Offset(size.width * 0.9f, size.height * 0.1f)
        )

        // Subtle Violet glow at bottom left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    KalkiNeonViolet.copy(alpha = 0.09f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.1f, size.height * 0.85f),
                radius = size.width * 0.7f
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * 0.1f, size.height * 0.85f)
        )
    }
}