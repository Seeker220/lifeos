package com.lifeos.ui.screens.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.model.Hardness
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.AnimatedCheckbox
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.components.LifeOsCard
import com.lifeos.ui.components.LineageChip
import com.lifeos.ui.components.Pill
import com.lifeos.ui.components.PrimaryButton
import com.lifeos.ui.components.RiskRing
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.components.pressable
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.shell.LocalScreenPadding
import com.lifeos.ui.theme.Danger
import com.lifeos.ui.theme.Motion
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Success
import com.lifeos.ui.theme.SuccessWash
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import com.lifeos.ui.theme.Warn
import com.lifeos.ui.theme.surface

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: GoalsViewModel = viewModel { GoalsViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val contentPadding = LocalScreenPadding.current

    if (ui.empty) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = "No goals yet",
                subtitle = "Try: crack the Google interview in 1 month",
                actionLabel = "Open chat",
                onAction = { onNavigate(LifeOsDestination.CHAT) },
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "xp") {
            GamificationHeader(xp = ui.xp, streakDays = ui.streakDays)
        }
        if (ui.goals.isNotEmpty()) {
            item { SectionHeader("Goals") }
            items(ui.goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onToggle = { vm.toggleGoal(goal.id) },
                    onCompleteTask = vm::completeTask,
                    onUndo = { vm.revertExpansion(goal.id) },
                    onNavigate = onNavigate,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { SectionHeader("Todos") }
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add task",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        items(ui.todos, key = { "todo-${it.id}" }) { task ->
            SwipeTaskRow(task = task, onComplete = { vm.completeTask(task.id) })
        }
        item { Spacer(Modifier.height(S.x6)) }
    }

    if (showCreate) {
        CreateTaskDialog(
            onDismiss = { showCreate = false },
            onConfirm = { title ->
                vm.createTask(title)
                showCreate = false
            },
        )
    }
}

@Composable
private fun GamificationHeader(xp: Int, streakDays: Int) {
    var xpTarget by remember { mutableIntStateOf(0) }
    LaunchedEffect(xp) { xpTarget = xp }
    val animatedXp by animateIntAsState(
        targetValue = xpTarget,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "xpCount",
    )
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x2),
        level = 2,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = animatedXp.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                )
                Text("XP", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(S.x2)) {
                Icon(
                    imageVector = Icons.Outlined.Whatshot,
                    contentDescription = null,
                    tint = Warn,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = streakDays.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "day streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    goal: GoalCardUi,
    onToggle: () -> Unit,
    onCompleteTask: (String) -> Unit,
    onUndo: () -> Unit,
    onNavigate: (LifeOsDestination) -> Unit,
) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x2),
        level = 1,
        onClick = onToggle,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(S.x3),
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(S.x2),
                ) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    HardnessPill(goal.hardness)
                }
                if (goal.dueLabel != null) {
                    Text(
                        text = goal.dueLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (goal.dueOverdue) Danger else TextSecondary,
                        modifier = Modifier.padding(top = S.x2),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RiskRing(percent = goal.riskPercent)
                if (goal.openCount > 0) {
                    Text(
                        text = "${goal.openCount} open",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }
        }
        if (goal.lineage.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = S.x3),
                horizontalArrangement = Arrangement.spacedBy(S.x2),
                verticalArrangement = Arrangement.spacedBy(S.x2),
            ) {
                goal.lineage.forEach { chip ->
                    val dest = chip.target.destination()
                    if (dest == null) {
                        LineageChip(chip.label)
                    } else {
                        Box(Modifier.pressable { onNavigate(dest) }) {
                            LineageChip(chip.label)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = goal.expanded,
            enter = fadeIn(Motion.emphasized) + expandVertically(),
            exit = fadeOut(Motion.emphasized) + shrinkVertically(),
        ) {
            Column(Modifier.padding(top = S.x3), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                goal.tasks.forEach { task ->
                    SwipeTaskRow(
                        task = task,
                        onComplete = { onCompleteTask(task.id) },
                        inset = true,
                    )
                }
                goal.habits.forEach { habit ->
                    LinkedEntityRow(title = habit.title, detail = habit.detail)
                }
                goal.blocks.forEach { block ->
                    LinkedEntityRow(title = block.title, detail = block.detail)
                }
                goal.timeouts.forEach { timeout ->
                    LinkedEntityRow(title = timeout.title, detail = timeout.detail)
                }
                goal.alarms.forEach { alarm ->
                    LinkedEntityRow(title = alarm.title, detail = alarm.detail)
                }
                if (goal.canUndoExpansion) {
                    Text(
                        text = "Undo expansion",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = S.x1)
                            .pressable(onUndo),
                    )
                }
            }
        }
    }
}

@Composable
private fun HardnessPill(hardness: Hardness) {
    // Pill washes the fill; HARD/SOFT ink matches DangerWash / AccentWash.
    Pill(
        text = hardness.name,
        color = if (hardness == Hardness.HARD) Danger else MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeTaskRow(
    task: TaskRowUi,
    onComplete: () -> Unit,
    inset: Boolean = false,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && !task.done) {
                onComplete()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !task.done,
        enableDismissFromEndToStart = !task.done,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (inset) S.x1 else S.x4, vertical = S.x1)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SuccessWash),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "Done",
                    color = Success,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = S.x4),
                )
            }
        },
    ) {
        TaskRow(task = task, onComplete = onComplete, inset = inset)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    task: TaskRowUi,
    onComplete: () -> Unit,
    inset: Boolean = false,
) {
    val fade by animateFloatAsState(
        targetValue = if (task.done) 0.4f else 1f,
        animationSpec = Motion.standard,
        label = "todoFade",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (inset) S.x1 else S.x4, vertical = S.x1)
            .graphicsLayer { alpha = fade }
            .surface(level = 1, radius = Radius.md)
            .padding(horizontal = S.x3, vertical = S.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.x3),
    ) {
        AnimatedCheckbox(
            checked = task.done,
            onCheckedChange = { checked -> if (checked) onComplete() },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(S.x1)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(S.x2),
                verticalArrangement = Arrangement.spacedBy(S.x1),
            ) {
                if (task.sourceGoalLabel != null) {
                    LineageChip(task.sourceGoalLabel)
                }
                if (task.estimateLabel != null) {
                    Pill(text = task.estimateLabel, color = MaterialTheme.colorScheme.primary)
                }
                if (task.subtitle != null) {
                    Text(
                        text = task.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.overdue) Danger else TextSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkedEntityRow(title: String, detail: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = S.x3)
            .surface(level = 2, radius = Radius.md)
            .padding(horizontal = S.x3, vertical = S.x2),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun CreateTaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            PrimaryButton(
                text = "Add",
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun LineageTarget.destination(): LifeOsDestination? = when (this) {
    LineageTarget.WELLBEING -> LifeOsDestination.WELLBEING
    LineageTarget.TODAY -> LifeOsDestination.TODAY
    LineageTarget.STAY -> null
}
