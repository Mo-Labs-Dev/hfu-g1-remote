package com.example.g1remote

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// White & green palette (matches the rest of G1 Remote).
private val TGreen = Color(0xFF087B39)
private val TGreenDark = Color(0xFF05602C)
private val TMint = Color(0xFFE7F5EC)
private val TMintStrong = Color(0xFFCDEBD8)
private val TBackground = Color(0xFFF5F8F6)
private val TDark = Color(0xFF172820)
private val TMuted = Color(0xFF64756B)
private val TWarn = Color(0xFF9D4B00)

/**
 * Visitor-facing "Talk to G1" layout. All logic (speech recognition,
 * backend calls) stays in TalkToG1Screen in MainActivity.kt.
 * messages: ("G1" | "Visitor" | "System") to text.
 */
@Composable
fun TalkToG1Content(
    isGerman: Boolean,
    onLanguageChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    messages: List<Pair<String, String>>,
    status: String,
    listening: Boolean,
    thinking: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onNewConversation: () -> Unit,
    onSuggestion: (String) -> Unit,
    extraSuggestions: List<String> = emptyList()
) {
    fun t(en: String, de: String) = if (isGerman) de else en

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TBackground)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------- Header ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TGreen)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Back", "Zurück"))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t("Talk to G1", "Mit G1 sprechen"),
                    color = TDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    t("Ask me anything about the lab", "Fragen Sie mich alles zum Labor"),
                    color = TMuted,
                    fontSize = 13.sp
                )
            }
            LanguageToggle(isGerman = isGerman, onChange = onLanguageChange)
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = onNewConversation,
                enabled = !thinking && !listening,
                colors = ButtonDefaults.textButtonColors(contentColor = TGreen)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(t("New chat", "Neues Gespräch"))
            }
        }

        // ---------------- Body ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left: microphone + suggestions
            Card(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    MicButton(
                        listening = listening,
                        thinking = thinking,
                        onClick = onMicClick
                    )
                    Text(
                        when {
                            listening -> t("I'm listening…", "Ich höre zu…")
                            thinking -> t("Let me think…", "Einen Moment…")
                            else -> t("Tap and speak", "Tippen und sprechen")
                        },
                        color = TDark,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        status,
                        color = TMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(color = TMint)

                    Text(
                        t("Try asking", "Probieren Sie"),
                        color = TMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val suggestions = if (isGerman) listOf(
                        "Wer bist du?",
                        "Was macht ihr in diesem Labor?",
                        "Gib mir die Hand",
                        "Kannst du winken?"
                    ) else listOf(
                        "Who are you?",
                        "What do you do in this lab?",
                        "Shake my hand",
                        "Can you wave?"
                    )
                    (extraSuggestions + suggestions).take(5).forEach { s ->
                        PromptChip(text = s, enabled = !thinking && !listening) {
                            onSuggestion(s)
                        }
                    }
                }
            }

            // Right: conversation
            Card(
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(messages.size, thinking) {
                        val last = messages.size + (if (thinking) 1 else 0) - 1
                        if (last >= 0) listState.animateScrollToItem(last)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        itemsIndexed(messages) { _, (speaker, text) ->
                            ChatBubble(speaker = speaker, text = text, isGerman = isGerman)
                        }
                        if (thinking) {
                            item { ThinkingBubble() }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            placeholder = { Text(t("Type a message…", "Nachricht eingeben…")) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TGreen,
                                unfocusedBorderColor = TMintStrong,
                                cursorColor = TGreen
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        val canSend = inputText.isNotBlank() && !thinking
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (canSend) TGreen else TMintStrong)
                                .clickable(enabled = canSend) { onSend() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = t("Send", "Senden"),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageToggle(isGerman: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TMint)
            .padding(4.dp)
    ) {
        listOf(false to "🇬🇧  English", true to "🇩🇪  Deutsch").forEach { (german, label) ->
            val selected = german == isGerman
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) TGreen else Color.Transparent)
                    .clickable { onChange(german) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else TGreenDark,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, thinking: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )

    Box(modifier = Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            // Two expanding rings while listening.
            listOf(pulse, (pulse + 0.5f) % 1f).forEach { p ->
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(1f + p * 0.4f)
                        .alpha(0.45f * (1f - p))
                        .clip(CircleShape)
                        .background(TGreen)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    when {
                        thinking -> TMintStrong
                        listening -> TGreenDark
                        else -> TGreen
                    }
                )
                .border(6.dp, TMint, CircleShape)
                .clickable(enabled = !thinking) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (thinking) {
                CircularProgressIndicator(color = TGreen, strokeWidth = 4.dp, modifier = Modifier.size(46.dp))
            } else {
                Icon(
                    if (listening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}

@Composable
private fun PromptChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() },
        color = TMint,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            "“$text”",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (enabled) TGreenDark else TMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ChatBubble(speaker: String, text: String, isGerman: Boolean) {
    when (speaker) {
        "Visitor" -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = TGreen,
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                modifier = Modifier.widthIn(max = 440.dp)
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        "G1" -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TGreen),
                contentAlignment = Alignment.Center
            ) {
                Text("G1", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = TMint,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    color = TDark,
                    fontSize = 16.sp
                )
            }
        }

        else -> Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = TWarn,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(TGreen),
            contentAlignment = Alignment.Center
        ) {
            Text("G1", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Surface(color = TMint, shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { i ->
                    val active = phase.toInt() == i
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .alpha(if (active) 1f else 0.35f)
                            .clip(CircleShape)
                            .background(TGreen)
                    )
                }
            }
        }
    }
}
