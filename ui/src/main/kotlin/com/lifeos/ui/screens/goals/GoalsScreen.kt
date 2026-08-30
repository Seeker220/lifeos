package com.lifeos.ui.screens.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.model.Hardness
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.components.RiskBadge
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.theme.LifeOsRadius
import com.lifeos.ui.theme.MdDanger
import com.lifeos.ui.theme.MdPrimary
import com.lifeos.ui.theme.MdSurface

@Composable
fun GoalsScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: GoalsViewModel = viewModel { GoalsViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    if (ui.empty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No goals yet.",
                subtitle = "Tell LifeOS what you're trying to do.",
                actionLabel = "Open chat",
                onAction = { onNavigate(LifeOsDestination.CHAT) },
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (ui.goals.isNotEmpty()) {
            item { SectionHeader("Goals") }
            items(ui.goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onToggle = { vm.toggleGoal(goal.id) },
                    onCompleteTask = vm::completeTask,
                    onUndo = { vm.revertExpansion(goal.id) },
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
                    Icon(Icons.Outlined.Add, contentDescription = "Add task")
                }
            }
        }
        items(ui.todos, key = { "todo-${it.id}" }) { task ->
            TaskRow(task = task, onComplete = { vm.completeTask(task.id) })
        }
        item { Spacer(Modifier.height(24.dp)) }
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
private fun GoalCard(
    goal: GoalCardUi,
    onToggle: () -> Unit,
    onCompleteTask: (String) -> Unit,
    onUndo: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MdSurface),
        shape = RoundedCornerShape(LifeOsRadius),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (goal.dueLabel != null) {
                            Text(
                                goal.dueLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HardnessChip(goal.hardness)
                        RiskBadge(goal.riskPercent)
                    }
                    if (goal.capsLine != null) {
                        Text(
                            goal.capsLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MdPrimary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Text(
                    "${goal.openCount} open",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (goal.expanded) {
                if (goal.tasks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    goal.tasks.forEach { task ->
                        TaskRow(task = task, onComplete = { onCompleteTask(task.id) })
                    }
                }
                if (goal.habits.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    goal.habits.forEach { habit ->
                        Column(Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                            Text(habit.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                habit.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                if (goal.canUndoExpansion) {
                    TextButton(onClick = onUndo) {
                        Text("Undo expansion")
                    }
                }
            }
        }
    }
}

@Composable
private fun HardnessChip(hardness: Hardness) {
    val hard = hardness == Hardness.HARD
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (hard) MdDanger.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            hardness.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (hard) MdDanger else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(task: TaskRowUi, onComplete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = { onComplete() })
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            if (task.subtitle != null) {
                Text(
                    task.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.overdue) MdDanger else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
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
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
