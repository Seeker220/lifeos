package com.lifeos.ui.screens.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.ChatMessage
import com.lifeos.core.model.ChatRole
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.ActionChipRow
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.nav.LifeOsDestination

private val Suggestions = listOf(
    "Crack a Google interview in 1 month",
    "Focus mode, only Chrome and Docs",
    "Check my email for exams",
)

@Composable
fun ChatScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: ChatViewModel = viewModel { ChatViewModel(UiPorts.value) }
    val state by vm.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    var bannerDismissed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime),
    ) {
        val showEmpty = state.messages.isEmpty() && !state.sending
        if (showEmpty) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    "Tell me a goal.",
                    "I'll turn it into a schedule and enforce it.",
                )
                Spacer(Modifier.height(16.dp))
                SuggestionStrip(
                    onPick = { text ->
                        draft = text
                        vm.send(text)
                        draft = ""
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = false,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

        if (state.pendingEmailCount > 0 && !bannerDismissed) {
            PendingEmailBanner(
                count = state.pendingEmailCount,
                onReview = { onNavigate(LifeOsDestination.INBOX) },
                onDismiss = { bannerDismissed = true },
            )
        }

        ChatComposer(
            value = draft,
            onValueChange = { draft = it },
            sending = state.sending,
            onSend = submit,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionStrip(onPick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Suggestions.forEach { text ->
            SuggestionChip(
                onClick = { onPick(text) },
                label = { Text(text) },
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxBubble = maxWidth * 0.8f
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = maxBubble),
                color = if (fromUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (fromUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                shape = if (fromUser) {
                    RoundedCornerShape(16.dp).copy(bottomEnd = CornerSize(4.dp))
                } else {
                    RoundedCornerShape(16.dp).copy(bottomStart = CornerSize(4.dp))
                },
                border = if (fromUser) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                },
            ) {
                if (fromUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    Column(Modifier.padding(12.dp)) {
                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        if (chips.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            val plural = if (chips.size == 1) "" else "s"
                            Text(
                                "Applied ${chips.size} change$plural",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ActionChipRow(chips = chips, onChipClick = onChipClick)
                        }
                        if (expansionGoalId != null) {
                            TextButton(onClick = { onUndo(expansionGoalId) }) {
                                Text("Undo expansion")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp).copy(bottomStart = CornerSize(4.dp)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(380),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 160),
                        ),
                        label = "dot$index",
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(alpha)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
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
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$count email$plural need a decision",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text("Review") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val canSend = value.isNotBlank() && !sending
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask LifeOS…") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

private fun destinationFor(kind: ChangeKind): LifeOsDestination = when (kind) {
    ChangeKind.GOAL, ChangeKind.TASK, ChangeKind.XP -> LifeOsDestination.GOALS
    ChangeKind.EVENT, ChangeKind.HABIT, ChangeKind.BLOCK, ChangeKind.ALARM -> LifeOsDestination.TODAY
    ChangeKind.TIMEOUT, ChangeKind.FOCUS, ChangeKind.NETWORK -> LifeOsDestination.WELLBEING
    ChangeKind.EMAIL -> LifeOsDestination.INBOX
    ChangeKind.MEMORY, ChangeKind.PERSONA, ChangeKind.REVERT -> LifeOsDestination.MORE
}
