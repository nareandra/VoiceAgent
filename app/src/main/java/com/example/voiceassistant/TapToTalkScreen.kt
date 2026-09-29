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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voiceassistant.ui.theme.TeslaAlertRed
import com.example.voiceassistant.ui.theme.TeslaBorder
import com.example.voiceassistant.ui.theme.TeslaBorderLight
import com.example.voiceassistant.ui.theme.TeslaCyanGlow
import com.example.voiceassistant.ui.theme.TeslaCyanSoft
import com.example.voiceassistant.ui.theme.TeslaDarkCanvas
import com.example.voiceassistant.ui.theme.TeslaDriverBubble
import com.example.voiceassistant.ui.theme.TeslaAiBubble
import com.example.voiceassistant.ui.theme.TeslaCardSurface
import com.example.voiceassistant.ui.theme.TeslaPearlShade
import com.example.voiceassistant.ui.theme.TeslaPearlWhite
import com.example.voiceassistant.ui.theme.TeslaPillBackground
import com.example.voiceassistant.ui.theme.TeslaPillBorder
import com.example.voiceassistant.ui.theme.TeslaPureBlack
import com.example.voiceassistant.ui.theme.TeslaTextMuted
import com.example.voiceassistant.ui.theme.TeslaTextSecondary
import com.example.voiceassistant.ui.theme.TeslaTextWhite
import kotlin.math.sin

@Composable
fun TapToTalkScreen(
    viewModel: ConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val isListening = uiState.state == AssistantState.LISTENING || uiState.state == AssistantState.WAKE_DETECTED
    val isThinking = uiState.state == AssistantState.THINKING
    val isError = uiState.state == AssistantState.ERROR

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TeslaPureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Minimalist Status Bar
            TeslaTopHeader()

            // 2. Multi-Turn Conversation Stream (Scrollable chat history)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                TeslaConversationStream(
                    messages = uiState.messages,
                    state = uiState.state
                )
            }

            // 3. Center Glowing AI Voice Pearl & Acoustic Soundwave
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                TeslaVoicePearl(
                    state = uiState.state,
                    audioAmplitude = uiState.audioAmplitude
                )
            }

            // 4. Bottom Controls: Quick Chips + Pill Mic Button + State Label
            TeslaBottomControls(
                state = uiState.state,
                isListening = isListening,
                isThinking = isThinking,
                isError = isError,
                errorMessage = uiState.errorMessage,
                onMicClick = { viewModel.onTapToTalkPressed() },
                onChipClick = { query -> viewModel.sendQuickQuery(query) },
                onKeyboardClick = { viewModel.toggleTextInput(true) },
                onDismissError = { viewModel.clearError() }
            )
        }

        // 5. Text Input Dialog (when user taps keyboard icon)
        if (uiState.isTextInputVisible) {
            TeslaTextInputDialog(
                onDismiss = { viewModel.toggleTextInput(false) },
                onSend = { text -> viewModel.sendTextMessage(text) }
            )
        }
    }
}

// =========================================================
// 1. TOP MINIMALIST HEADER
// =========================================================

@Composable
private fun TeslaTopHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left spacer for symmetric centering
        Box(modifier = Modifier.width(48.dp))

        // Center: Bold, spaced luxury brand title
        Text(
            text = "KALKI",
            color = TeslaTextWhite,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )

        // Right: Status icons (Bluetooth & Battery)
        Row(
            modifier = Modifier.width(48.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = "Bluetooth Connected",
                tint = TeslaTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.BatteryFull,
                contentDescription = "Battery",
                tint = TeslaTextSecondary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

// =========================================================
// 2. MULTI-TURN CONVERSATION STREAM
// =========================================================

@Composable
private fun TeslaConversationStream(
    messages: List<ChatMessage>,
    state: AssistantState
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = when (state) {
                    AssistantState.SLEEPING -> "Say \"Hey Kalki\""
                    AssistantState.WAKE_DETECTED,
                    AssistantState.LISTENING -> "I'm listening…"
                    AssistantState.THINKING -> "Processing…"
                    AssistantState.SPEAKING -> "KALKI speaking…"
                    AssistantState.ERROR -> "Connection notice"
                },
                color = if (state == AssistantState.LISTENING) TeslaCyanGlow else TeslaTextSecondary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Autonomous navigation, traffic & vehicle assistant",
                color = TeslaTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.sender == ChatSender.DRIVER) {
                    // Driver query: Right-aligned sleek dark bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                                .background(TeslaDriverBubble)
                                .border(1.dp, TeslaBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = msg.text,
                                color = TeslaTextWhite,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                } else {
                    // KALKI reply: Left-aligned sleek obsidian bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                                .background(TeslaAiBubble)
                                .border(1.dp, TeslaBorder, RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = msg.text,
                                color = TeslaTextWhite,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================
// 3. MINIMALIST AI VOICE PEARL & ACOUSTIC SOUNDWAVE
// =========================================================

@Composable
private fun TeslaVoicePearl(
    state: AssistantState,
    audioAmplitude: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pearl_transition")

    // Pearl scale breathing animation
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = when (state) {
            AssistantState.LISTENING -> 1.15f
            AssistantState.SPEAKING -> 1.10f
            AssistantState.THINKING -> 1.05f
            else -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantState.LISTENING) 800 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    // Wave phase animation for fluid sinusoidal acoustic waves
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 1000
                    AssistantState.THINKING -> 700
                    AssistantState.SPEAKING -> 1200
                    else -> 3000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val basePearlRadius = 52.dp.toPx()
        val currentPearlRadius = basePearlRadius * breathingScale

        // 1. Acoustic Sinusoidal Wave lines radiating across the width
        val waveWidth = size.width
        val waveAmplitudePx = when (state) {
            AssistantState.LISTENING -> (16.dp.toPx() + (audioAmplitude * 28.dp.toPx()))
            AssistantState.SPEAKING -> 15.dp.toPx()
            AssistantState.THINKING -> 8.dp.toPx()
            else -> 4.dp.toPx()
        }

        // Draw multiple harmonic wave layers (main wave + secondary ambient harmonic wave)
        val harmonicFrequencies = listOf(2.5, 3.2, 4.0)
        val harmonicAlphas = listOf(0.6f, 0.35f, 0.2f)

        for (i in harmonicFrequencies.indices) {
            val freq = harmonicFrequencies[i]
            val alpha = harmonicAlphas[i]
            val path = Path()

            for (x in 0..waveWidth.toInt() step 3) {
                val progress = x / waveWidth
                // Windowing envelope: dampens wave at left/right edges so it cleanly fades
                val envelope = sin(progress * Math.PI).toFloat()
                val y = center.y + sin((progress * freq * Math.PI).toFloat() + wavePhase * (i + 1)) * waveAmplitudePx * envelope * (1f / (i + 1f))

                if (x == 0) {
                    path.moveTo(x.toFloat(), y)
                } else {
                    path.lineTo(x.toFloat(), y)
                }
            }

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        TeslaCyanGlow.copy(alpha = alpha),
                        TeslaPearlWhite.copy(alpha = alpha * 0.9f),
                        TeslaCyanGlow.copy(alpha = alpha),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = (2.2f - i * 0.4f).dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 2. Soft Ambient Cyan Glow Aura behind Pearl
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    TeslaCyanGlow.copy(alpha = if (state == AssistantState.LISTENING) 0.35f else 0.18f),
                    TeslaCyanSoft.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center,
                radius = currentPearlRadius * 2.2f
            ),
            radius = currentPearlRadius * 2.2f,
            center = center
        )

        // 3. The Minimalist Pearl Core Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    TeslaPearlWhite,
                    TeslaPearlShade,
                    TeslaCyanGlow.copy(alpha = 0.8f)
                ),
                center = Offset(center.x - currentPearlRadius * 0.25f, center.y - currentPearlRadius * 0.25f),
                radius = currentPearlRadius
            ),
            radius = currentPearlRadius,
            center = center
        )

        // Subtle specular highlight on top-left of the pearl
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = currentPearlRadius * 0.22f,
            center = Offset(center.x - currentPearlRadius * 0.35f, center.y - currentPearlRadius * 0.35f)
        )
    }
}

// =========================================================
// 4. BOTTOM CONTROLS & ERGONOMIC PILL DOCK
// =========================================================

@Composable
private fun TeslaBottomControls(
    state: AssistantState,
    isListening: Boolean,
    isThinking: Boolean,
    isError: Boolean,
    errorMessage: String?,
    onMicClick: () -> Unit,
    onChipClick: (String) -> Unit,
    onKeyboardClick: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Error Notice Bar
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
                    .background(TeslaAlertRed.copy(alpha = 0.15f))
                    .border(1.dp, TeslaAlertRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = errorMessage ?: "Connection notice. Tap mic to retry.",
                    color = TeslaAlertRed,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Retry",
                        tint = TeslaAlertRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Quick Suggestion Pills Row: Route, Traffic, Safety, Who are you?
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TeslaQuickPill(label = "Route", query = "What route options do we have?", onClick = onChipClick)
            Spacer(modifier = Modifier.width(10.dp))
            TeslaQuickPill(label = "Traffic", query = "How is the traffic ahead?", onClick = onChipClick)
            Spacer(modifier = Modifier.width(10.dp))
            TeslaQuickPill(label = "Safety", query = "What is our safety score?", onClick = onChipClick)
            Spacer(modifier = Modifier.width(10.dp))
            TeslaQuickPill(label = "Who are you?", query = "Who are you?", onClick = onChipClick)
        }

        // Floating Minimalist Mic Pill Button + Keyboard Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Keyboard toggle button (left of mic)
            Surface(
                onClick = onKeyboardClick,
                shape = CircleShape,
                color = TeslaPillBackground,
                modifier = Modifier
                    .size(46.dp)
                    .border(1.dp, TeslaPillBorder, CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = "Type a message",
                        tint = TeslaTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Main Pill Mic Button
            TeslaPillMicButton(
                isListening = isListening,
                isThinking = isThinking,
                onClick = onMicClick
            )

            Spacer(modifier = Modifier.width(20.dp))

            // Empty spacer matching keyboard button for visual center balance
            Box(modifier = Modifier.size(46.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // State Subtitle Label
        Text(
            text = when {
                isListening -> "LISTENING…"
                isThinking -> "THINKING…"
                state == AssistantState.SPEAKING -> "SPEAKING…"
                else -> "TAP TO TALK"
            },
            color = if (isListening) TeslaCyanGlow else TeslaTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
    }
}

// =========================================================
// 5. QUICK PILL COMPONENT
// =========================================================

@Composable
private fun TeslaQuickPill(
    label: String,
    query: String,
    onClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TeslaPillBackground)
            .border(1.dp, TeslaPillBorder, RoundedCornerShape(20.dp))
            .clickable { onClick(query) }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = TeslaTextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// =========================================================
// 6. PILL MIC BUTTON
// =========================================================

@Composable
private fun TeslaPillMicButton(
    isListening: Boolean,
    isThinking: Boolean,
    onClick: () -> Unit
) {
    val pillWidth = 92.dp
    val pillHeight = 52.dp

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pill_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isListening) 0.85f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_alpha"
    )

    val borderColor = when {
        isListening -> TeslaAlertRed.copy(alpha = pulseAlpha)
        isThinking -> TeslaCyanGlow.copy(alpha = pulseAlpha)
        else -> TeslaCyanGlow.copy(alpha = 0.6f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = pillWidth, height = pillHeight)
    ) {
        // Subtle ambient halo
        Box(
            modifier = Modifier
                .size(width = pillWidth + 8.dp, height = pillHeight + 8.dp)
                .background(
                    color = (if (isListening) TeslaAlertRed else TeslaCyanGlow).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(28.dp)
                )
        )

        // Pill Surface
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(26.dp),
            color = TeslaCardSurface,
            modifier = Modifier
                .size(width = pillWidth, height = pillHeight)
                .border(1.5.dp, borderColor, RoundedCornerShape(26.dp))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Talk to KALKI",
                    tint = if (isListening) TeslaAlertRed else TeslaTextWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// =========================================================
// 7. TEXT INPUT DIALOG
// =========================================================

@Composable
private fun TeslaTextInputDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(TeslaCardSurface)
                .border(1.dp, TeslaBorderLight, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Message KALKI",
                        color = TeslaTextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = TeslaTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text("Ask about route, traffic, or battery…", color = TeslaTextMuted, fontSize = 14.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TeslaTextWhite,
                        unfocusedTextColor = TeslaTextWhite,
                        focusedBorderColor = TeslaCyanGlow,
                        unfocusedBorderColor = TeslaBorder,
                        focusedContainerColor = TeslaDarkCanvas,
                        unfocusedContainerColor = TeslaDarkCanvas
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        onClick = {
                            val trimmed = textInput.trim()
                            if (trimmed.isNotBlank()) {
                                onSend(trimmed)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = TeslaPillBackground,
                        modifier = Modifier.border(1.dp, TeslaCyanGlow.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Send",
                                color = TeslaTextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "Send",
                                tint = TeslaCyanGlow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}