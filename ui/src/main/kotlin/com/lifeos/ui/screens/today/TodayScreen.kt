package com.lifeos.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.model.TimelineItem
import com.lifeos.core.model.TimelineKind
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.theme.MdDanger
import com.lifeos.ui.theme.MdPrimary
import com.lifeos.ui.theme.MdWarn
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: TodayViewModel = viewModel { TodayViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (ui.focusActive) {
                        vm.stopFocus()
                    } else if (!UiPorts.value.system.permissions().enforcementReady) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Grant usage access and overlay first",
                                actionLabel = "More",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onNavigate(LifeOsDestination.MORE)
                            }
                        }
                    } else {
                        vm.startFocus()
                    }
                },
                containerColor = MdPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(if (ui.focusActive) "Stop Focus" else "Start Focus")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                DateHeader(
                    title = ui.dateTitle,
                    isToday = ui.isToday,
                    onPrev = { vm.shiftDate(-1) },
                    onNext = { vm.shiftDate(1) },
                )
            }
            if (ui.empty) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = "Nothing scheduled.",
                            subtitle = "Ask LifeOS for a goal and it will fill this in.",
                            actionLabel = "Open chat",
                            onAction = { onNavigate(LifeOsDestination.CHAT) },
                        )
                    }
                }
            } else {
                ui.groups.forEach { group ->
                    item(key = "header-${group.title}") {
                        SectionHeader(group.title)
                    }
                    itemsIndexed(
                        group.items,
                        key = { index, item -> "${group.title}-$index-${item.refId}-${item.kind}-${item.title}" },
                    ) { _, item ->
                        TimelineRow(item = item, onComplete = { vm.completeItem(item) })
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun DateHeader(
    title: String,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isToday) "Today" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
            }
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, onComplete: () -> Unit) {
    val completable = item.kind == TimelineKind.HABIT || item.kind == TimelineKind.TASK
    val timed = item.timeHhmm.isNotBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (timed) item.timeHhmm else "--:--",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (timed) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(56.dp),
        )
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(kindColor(item)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                color = if (item.done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        if (completable) {
            Checkbox(checked = item.done, onCheckedChange = { onComplete() })
        }
    }
}

@Composable
private fun kindColor(item: TimelineItem): Color = when (item.kind) {
    TimelineKind.ALARM -> MdWarn
    TimelineKind.EVENT -> if (item.hard) MdDanger else MdPrimary
    TimelineKind.BLOCK -> MdPrimary
    TimelineKind.HABIT -> MdPrimary.copy(alpha = 0.6f)
    TimelineKind.TASK -> MaterialTheme.colorScheme.onSurfaceVariant
}
