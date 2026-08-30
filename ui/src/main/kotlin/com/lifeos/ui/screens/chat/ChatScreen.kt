package com.lifeos.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.ChatMessage
import com.lifeos.core.model.ChatRole
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.ActionChipRow
import com.lifeos.ui.components.GhostButton
import com.lifeos.ui.components.LifeOsCard
import com.lifeos.ui.components.ScrimEdge
import com.lifeos.ui.components.pressable
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.shell.LocalScreenPadding
import com.lifeos.ui.theme.AccentVivid
import com.lifeos.ui.theme.AgentGradient
import com.lifeos.ui.theme.AgentGradientEdge
import com.lifeos.ui.theme.BorderSubtle
import com.lifeos.ui.theme.Motion
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Success
import com.lifeos.ui.theme.SuccessWash
import com.lifeos.ui.theme.Surface1
import com.lifeos.ui.theme.Surface2
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import com.lifeos.ui.theme.surface
import kotlinx.coroutines.delay

private data class HeroSuggestion(
    val title: String,
    val subtitle: String,
    val sendText: String,
    val chipLabel: String,
    val icon: ImageVector,
)

private val HeroSuggestions = listOf(
    HeroSuggestion(
        title = "Crack Google interview",
        subtitle = "One month of prep, scheduled",
        sendText = "help me crack the Google interview in 1 month",
        chipLabel = "Crack a Google interview in 1 month",
        icon = Icons.Outlined.Flag,
    ),
    HeroSuggestion(
        title = "Deep focus block",
        subtitle = "Start a 50-minute session",
        sendText = "start a 50 minute focus session",
        chipLabel = "Start a 50-min focus block",
        icon = Icons.Outlined.Timer,
    ),
    HeroSuggestion(
        title = "Cap distractions",
        subtitle = "Instagram limited to 30 minutes",
        sendText = "cap Instagram at 30 minutes a day",
        chipLabel = "Cap Instagram at 30m",
        icon = Icons.Outlined.HourglassBottom,
    ),
    HeroSuggestion(
        title = "Triage my inbox",
        subtitle = "Surface anything that matters",
        sendText = "check my email for anything important",
        chipLabel = "Check my email",
        icon = Icons.Outlined.Mail,
    ),
)

@Composable
fun ChatScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: ChatViewModel = viewModel { ChatViewModel(UiPorts.value) }
    val state by vm.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    var bannerDismissed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val screenPadding = LocalScreenPadding.current
    val showHero = state.messages.isEmpty() && !state.sending
    val showBanner = state.pendingEmailCount > 0 && !bannerDismissed

    LaunchedEffect(state.messages.size, state.sending) {
        val lastIndex = state.messages.size + if (state.sending) 0 else -1
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    val submit: () -> Unit = {
        val text = draft
        if (text.isNotBlank() && !state.sending) {
            draft = ""
            vm.send(text)
        }
    }

    val sendSuggestion: (String) -> Unit = { text ->
        if (!state.sending) {
            draft = ""
            vm.send(text)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Crossfade(
                targetState = showHero,
                modifier = Modifier.fillMaxSize(),
                animationSpec = Motion.enter,
                label = "chatHero",
            ) { hero ->
                if (hero) {
                    AgentHero(
                        topPadding = screenPadding.calculateTopPadding(),
                        sending = state.sending,
                        onPick = sendSuggestion,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = S.x4,
                            end = S.x4,
                            top = screenPadding.calculateTopPadding() + S.x3,
                            bottom = S.x3,
                        ),
                        verticalArrangement = Arrangement.spacedBy(S.x3),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            val chips = state.chipsFor(message)
                            val expansionId = state.expansionIdFor(message)
                            ChatBubble(
                                message = message,
                                chips = chips,
                                expansionGoalId = expansionId,
                                onChipClick = { chip -> onNavigate(destinationFor(chip.kind)) },
                                onUndo = { vm.undoExpansion(it) },
                            )
                        }
                        if (state.sending) {
                            item(key = "typing") {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }

            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                ScrimEdge(top = true)
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                ScrimEdge(top = false)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showBanner,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = screenPadding.calculateTopPadding()),
                enter = fadeIn(Motion.enter) + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f),
                    initialOffsetY = { -it },
                ),
                exit = fadeOut(Motion.standard) + slideOutVertically(
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f),
                    targetOffsetY = { -it },
                ),
            ) {
                PendingEmailBanner(
                    count = state.pendingEmailCount,
                    onReview = { onNavigate(LifeOsDestination.INBOX) },
                    onDismiss = { bannerDismissed = true },
                )
            }
        }

        if (!showHero) {
            SuggestionStrip(enabled = !state.sending, onPick = sendSuggestion)
        }

        ChatComposer(
            value = draft,
            onValueChange = { draft = it },
            sending = state.sending,
            bottomPadding = screenPadding.calculateBottomPadding(),
            onSend = submit,
        )
    }
}

@Composable
private fun AgentHero(
    topPadding: Dp,
    sending: Boolean,
    onPick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .padding(horizontal = S.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(168.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AgentGradient),
            )
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        Text(
            text = "What should we change today?",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = S.x2),
        )
        Spacer(Modifier.height(S.x6))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(S.x3),
        ) {
            HeroSuggestionCard(
                suggestion = HeroSuggestions[0],
                enabled = !sending,
                onPick = onPick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            HeroSuggestionCard(
                suggestion = HeroSuggestions[1],
                enabled = !sending,
                onPick = onPick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(S.x3))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(S.x3),
        ) {
            HeroSuggestionCard(
                suggestion = HeroSuggestions[2],
                enabled = !sending,
                onPick = onPick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            HeroSuggestionCard(
                suggestion = HeroSuggestions[3],
                enabled = !sending,
                onPick = onPick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(S.x4))
    }
}

@Composable
private fun HeroSuggestionCard(
    suggestion: HeroSuggestion,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifeOsCard(
        modifier = modifier,
        onClick = if (enabled) {
            { onPick(suggestion.sendText) }
        } else {
            null
        },
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(S.x2))
        Text(
            text = suggestion.title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(S.x1))
        Text(
            text = suggestion.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SuggestionStrip(enabled: Boolean, onPick: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = S.x4, vertical = S.x2),
        horizontalArrangement = Arrangement.spacedBy(S.x2),
    ) {
        items(HeroSuggestions, key = { it.sendText }) { suggestion ->
            Text(
                text = suggestion.chipLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .then(
                        if (enabled) {
                            Modifier.pressable { onPick(suggestion.sendText) }
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(Radius.full))
                    .background(Surface2)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(Radius.full))
                    .padding(horizontal = S.x3, vertical = S.x2),
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    chips: List<AppliedChange>,
    expansionGoalId: String?,
    onChipClick: (AppliedChange) -> Unit,
    onUndo: (String) -> Unit,
) {
    val fromUser = message.role == ChatRole.USER
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = Motion.enter,
        label = "bubbleEnter",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter
                val scale = 0.94f + 0.06f * enter
                scaleX = scale
                scaleY = scale
            },
    ) {
        val maxBubble = maxWidth * 0.88f
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            if (fromUser) {
                UserBubble(text = message.text, maxWidth = maxBubble)
            } else {
                AssistantBubble(
                    text = message.text,
                    chips = chips,
                    expansionGoalId = expansionGoalId,
                    maxWidth = maxBubble,
                    onChipClick = onChipClick,
                    onUndo = onUndo,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, maxWidth: Dp) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(
        topStart = Radius.lg,
        topEnd = Radius.lg,
        bottomStart = Radius.lg,
        bottomEnd = Radius.xs,
    )
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(scheme.primary)
            .padding(horizontal = S.x3, vertical = S.x3),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onPrimary,
        )
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    chips: List<AppliedChange>,
    expansionGoalId: String?,
    maxWidth: Dp,
    onChipClick: (AppliedChange) -> Unit,
    onUndo: (String) -> Unit,
) {
    val shape = assistantShape()
    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .then(if (chips.isNotEmpty()) Modifier.fillMaxWidth(0.88f) else Modifier)
            .clip(shape)
            .background(Surface1)
            .border(1.dp, BorderSubtle, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AgentGradientEdge),
        )
        Column(Modifier.padding(S.x3)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(S.x3))
                AppliedChangesCard(chips = chips, onChipClick = onChipClick)
            }
            if (expansionGoalId != null) {
                Spacer(Modifier.height(S.x2))
                GhostButton(
                    text = "Undo",
                    onClick = { onUndo(expansionGoalId) },
                    icon = Icons.Outlined.Undo,
                )
            }
        }
    }
}

@Composable
private fun AppliedChangesCard(
    chips: List<AppliedChange>,
    onChipClick: (AppliedChange) -> Unit,
) {
    val plural = if (chips.size == 1) "" else "s"
    LifeOsCard(level = 2) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(S.x2),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Applied ${chips.size} change$plural",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(S.x3))
        ActionChipRow(chips = chips, onChipClick = onChipClick)
    }
}

@Composable
private fun TypingIndicator() {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(160)
            phase = (phase + 1) % 3
        }
    }
    val shape = assistantShape()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(Surface1)
                .border(1.dp, BorderSubtle, shape),
        ) {
            Box(
                Modifier
                    .widthIn(min = 72.dp)
                    .height(1.dp)
                    .background(AgentGradientEdge),
            )
            Row(
                Modifier.padding(horizontal = S.x4, vertical = S.x3),
                horizontalArrangement = Arrangement.spacedBy(S.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    val scale by animateFloatAsState(
                        targetValue = if (phase == index) 1f else 0.45f,
                        animationSpec = Motion.emphasized,
                        label = "dot$index",
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(AccentVivid, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingEmailBanner(
    count: Int,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val plural = if (count == 1) "" else "s"
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = S.x4, vertical = S.x2)
            .pressable(onReview)
            .clip(shape)
            .background(SuccessWash)
            .border(1.dp, Success.copy(alpha = 0.35f), shape)
            .padding(start = S.x3, end = S.x1, top = S.x2, bottom = S.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.x2),
    ) {
        Icon(
            imageVector = Icons.Outlined.Mail,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "$count email$plural need a decision",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .pressable(onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    bottomPadding: Dp,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val canSend = value.isNotBlank() && !sending
    val dispatch by animateFloatAsState(
        targetValue = if (sending) 1f else 0f,
        animationSpec = Motion.emphasized,
        label = "sendDispatch",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = S.x4, end = S.x4, top = S.x1, bottom = S.x2 + bottomPadding)
            .surface(level = 3, radius = Radius.xl)
            .padding(start = S.x4, end = S.x2, top = S.x2, bottom = S.x2),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(S.x2),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = S.x2),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
            cursorBrush = SolidColor(scheme.primary),
            maxLines = 5,
            enabled = !sending,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Ask LifeOS…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary,
                        )
                    }
                    inner()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    val scale = 1f - 0.12f * dispatch
                    scaleX = scale
                    scaleY = scale
                    rotationZ = 40f * dispatch
                }
                .clip(CircleShape)
                .background(
                    if (canSend) scheme.primary else scheme.primary.copy(alpha = 0.35f),
                )
                .then(if (canSend) Modifier.pressable(onSend) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) scheme.onPrimary else scheme.onPrimary.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun assistantShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = Radius.lg,
    topEnd = Radius.lg,
    bottomStart = Radius.xs,
    bottomEnd = Radius.lg,
)

private fun destinationFor(kind: ChangeKind): LifeOsDestination = when (kind) {
    ChangeKind.FOCUS, ChangeKind.TIMEOUT, ChangeKind.NETWORK -> LifeOsDestination.WELLBEING
    ChangeKind.GOAL, ChangeKind.TASK, ChangeKind.XP -> LifeOsDestination.GOALS
    ChangeKind.EVENT, ChangeKind.BLOCK, ChangeKind.ALARM, ChangeKind.HABIT -> LifeOsDestination.TODAY
    ChangeKind.EMAIL -> LifeOsDestination.INBOX
    ChangeKind.MEMORY, ChangeKind.PERSONA, ChangeKind.REVERT -> LifeOsDestination.MORE
}
