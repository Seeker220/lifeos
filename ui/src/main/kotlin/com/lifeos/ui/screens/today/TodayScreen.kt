package com.lifeos.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.Time
import com.lifeos.core.model.TimelineItem
import com.lifeos.core.model.TimelineKind
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.AnimatedCheckbox
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.components.LifeOsCard
import com.lifeos.ui.components.LineageChip
import com.lifeos.ui.components.Pill
import com.lifeos.ui.components.PrimaryButton
import com.lifeos.ui.components.ProgressRing
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.components.pressable
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.theme.AccentVivid
import com.lifeos.ui.theme.AccentWash
import com.lifeos.ui.theme.BorderSubtle
import com.lifeos.ui.theme.Danger
import com.lifeos.ui.theme.Motion
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Success
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import com.lifeos.ui.theme.TimeNumeric
import com.lifeos.ui.theme.Warn
import com.lifeos.ui.theme.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private sealed interface RailRow {
    data class Header(val title: String) : RailRow
    data class Event(val item: TodayItem) : RailRow
    data object Now : RailRow
}

@Composable
fun TodayScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: TodayViewModel = viewModel { TodayViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000)
            tick++
        }
    }

    val nowMinutes = remember(tick) { nowMinutes() }
    val nowHhmm = remember(tick) { Time.formatHhmm(Time.nowEpochMs()) }
    val allItems = remember(ui.groups) { ui.groups.flatMap { it.items } }
    val hero = remember(allItems, ui.isToday, tick) { resolveHero(allItems, ui.isToday, nowMinutes) }
    val rail = remember(ui.groups, ui.isToday, tick) {
        flattenRail(ui.groups, ui.isToday, nowHhmm)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        val contentPadding = consumedScreenPadding(scaffoldPadding)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "hero") {
                NowHeroCard(
                    hero = hero,
                    isToday = ui.isToday,
                    nowMinutes = nowMinutes,
                    focusActive = ui.focusActive,
                    onPlanDay = { onNavigate(LifeOsDestination.CHAT) },
                    onFocus = {
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
                )
            }
            item(key = "week") {
                WeekStrip(
                    title = if (ui.isToday) "Today" else ui.dateTitle,
                    days = ui.weekDays,
                    onSelect = vm::selectDate,
                )
            }
            if (ui.empty) {
                item(key = "empty") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = "Nothing scheduled",
                            subtitle = "Ask LifeOS for a goal and it will fill this in.",
                            actionLabel = "Open chat",
                            onAction = { onNavigate(LifeOsDestination.CHAT) },
                        )
                    }
                }
            } else {
                items(rail, key = { row -> railKey(row) }) { row ->
                    when (row) {
                        is RailRow.Header -> TimelineSectionHeader(row.title)
                        is RailRow.Event -> TimelineRailItem(
                            item = row.item,
                            onComplete = { vm.completeItem(row.item.item) },
                        )
                        RailRow.Now -> NowLine(timeHhmm = nowHhmm)
                    }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(S.x8)) }
        }
    }
}

@Composable
private fun NowHeroCard(
    hero: TodayItem?,
    isToday: Boolean,
    nowMinutes: Int,
    focusActive: Boolean,
    onPlanDay: () -> Unit,
    onFocus: () -> Unit,
) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x2),
        level = 2,
        active = true,
    ) {
        if (hero == null) {
            Text("Your day is open", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(S.x1))
            Text(
                text = "Ask LifeOS to plan it.",
                style = TimeNumeric,
                color = TextSecondary,
            )
            Spacer(Modifier.height(S.x3))
            PrimaryButton(text = "Open chat", onClick = onPlanDay)
            return@LifeOsCard
        }
        val inProgress = isToday && hero.startMinutes != null &&
            nowMinutes >= hero.startMinutes &&
            (hero.endMinutes == null || nowMinutes < hero.endMinutes)
        val progress = if (inProgress) {
            elapsedFraction(hero.startMinutes, hero.endMinutes, nowMinutes)
        } else {
            0f
        }
        val countdown = heroCountdown(hero, isToday, nowMinutes, inProgress)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(S.x4),
        ) {
            ProgressRing(progress = progress, size = 56.dp, color = AccentVivid) {
                val remaining = if (inProgress && hero.endMinutes != null) {
                    (hero.endMinutes - nowMinutes).coerceAtLeast(0)
                } else {
                    hero.startMinutes?.minus(nowMinutes)?.coerceAtLeast(0)
                }
                if (remaining != null) {
                    Text(
                        text = "${remaining}m",
                        style = TimeNumeric,
                        color = AccentVivid,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = hero.item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(S.x1))
                Text(text = countdown, style = TimeNumeric, color = TextSecondary)
                Spacer(Modifier.height(S.x3))
                PrimaryButton(
                    text = if (focusActive) "Stop Focus" else "Start Focus",
                    onClick = onFocus,
                )
            }
        }
    }
}

@Composable
private fun WeekStrip(
    title: String,
    days: List<WeekDayUi>,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = S.x2, bottom = S.x1)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = S.x4),
        )
        Spacer(Modifier.height(S.x3))
        LazyRow(
            contentPadding = PaddingValues(horizontal = S.x4),
            horizontalArrangement = Arrangement.spacedBy(S.x2),
        ) {
            items(days, key = { it.dateIso }) { day ->
                val shape = RoundedCornerShape(Radius.md)
                val fill = if (day.isToday) scheme.primary else Color.Transparent
                val label = if (day.isToday) scheme.onPrimary else TextPrimary
                val meta = if (day.isToday) scheme.onPrimary.copy(alpha = 0.78f) else TextSecondary
                Column(
                    modifier = Modifier
                        .pressable { onSelect(day.dateIso) }
                        .width(48.dp)
                        .then(
                            if (day.isToday) {
                                Modifier
                                    .clip(shape)
                                    .background(fill)
                            } else {
                                Modifier.surface(level = 1, radius = Radius.md)
                            },
                        )
                        .then(
                            if (day.selected) {
                                Modifier.border(2.dp, AccentWash, shape)
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = S.x2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = day.weekdayInitial, style = MaterialTheme.typography.labelSmall, color = meta)
                    Text(
                        text = day.dayNumber.toString(),
                        style = TimeNumeric,
                        color = label,
                    )
                    Spacer(Modifier.height(S.x1))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !day.hasItems -> Color.Transparent
                                    day.isToday -> scheme.onPrimary
                                    else -> scheme.primary
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = S.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(title)
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(BorderSubtle),
        )
    }
}

@Composable
private fun TimelineRailItem(item: TodayItem, onComplete: () -> Unit) {
    var collapsing by remember(item.key) { mutableStateOf(false) }
    val faded by animateFloatAsState(
        targetValue = if (item.item.done) 0.4f else 1f,
        animationSpec = Motion.standard,
        label = "railFade",
    )
    LaunchedEffect(item.item.done) {
        if (item.item.done && item.completable) {
            delay(220)
            collapsing = true
        }
    }
    AnimatedVisibility(
        visible = !collapsing,
        enter = fadeIn(Motion.enter) + scaleIn(initialScale = 0.94f, animationSpec = Motion.enter),
        exit = fadeOut(Motion.standard) + shrinkVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
        ),
        modifier = Modifier.animateItem(),
    ) {
        val timed = item.timed
        val dot = kindColor(item)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .graphicsLayer { alpha = faded }
                .padding(horizontal = S.x4, vertical = S.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (timed) item.item.timeHhmm else "--:--",
                style = TimeNumeric,
                color = if (timed) TextPrimary else TextTertiary,
                modifier = Modifier.width(56.dp),
            )
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(BorderSubtle),
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .surface(level = 1, radius = Radius.md)
                    .padding(S.x3),
            ) {
                Text(
                    text = item.item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (item.item.done) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    ),
                    color = TextPrimary,
                )
                if (item.item.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(S.x1))
                    Text(
                        text = item.item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                if (item.external || item.sourceGoalLabel != null) {
                    Spacer(Modifier.height(S.x2))
                    Row(horizontalArrangement = Arrangement.spacedBy(S.x2)) {
                        if (item.external) {
                            Pill(text = "Google Calendar", color = Success)
                        }
                        item.sourceGoalLabel?.let { LineageChip(it) }
                    }
                }
            }
            if (item.completable) {
                Spacer(Modifier.width(S.x2))
                AnimatedCheckbox(
                    checked = item.item.done,
                    onCheckedChange = { checked -> if (checked) onComplete() },
                )
            }
        }
    }
}

@Composable
private fun NowLine(timeHhmm: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = S.x4, vertical = S.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeHhmm,
            style = TimeNumeric,
            color = AccentVivid,
            modifier = Modifier.width(56.dp),
        )
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentVivid),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(AccentVivid),
        )
    }
}

@Composable
private fun kindColor(item: TodayItem): Color {
    if (item.external) return Success
    return when (item.item.kind) {
        TimelineKind.ALARM -> Warn
        TimelineKind.EVENT -> if (item.item.hard) Danger else MaterialTheme.colorScheme.primary
        TimelineKind.BLOCK -> MaterialTheme.colorScheme.primary
        TimelineKind.HABIT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        TimelineKind.TASK -> TextTertiary
    }
}

private fun heroCountdown(
    hero: TodayItem,
    isToday: Boolean,
    nowMinutes: Int,
    inProgress: Boolean,
): String {
    if (!isToday) {
        return if (hero.timed) "at ${hero.item.timeHhmm}" else "unscheduled"
    }
    val target = if (inProgress) hero.endMinutes else hero.startMinutes
    if (target == null) return if (hero.timed) "at ${hero.item.timeHhmm}" else "unscheduled"
    return formatCountdown(target - nowMinutes, ending = inProgress)
}

private fun flattenRail(
    groups: List<TimelineGroup>,
    isToday: Boolean,
    nowHhmm: String,
): List<RailRow> {
    val rows = mutableListOf<RailRow>()
    var inserted = !isToday
    groups.forEach { group ->
        if (!inserted && group.title == "Anytime") {
            rows += RailRow.Now
            inserted = true
        }
        rows += RailRow.Header(group.title)
        group.items.forEach { item ->
            if (!inserted && item.timed && item.item.timeHhmm >= nowHhmm) {
                rows += RailRow.Now
                inserted = true
            }
            rows += RailRow.Event(item)
        }
    }
    if (!inserted) rows += RailRow.Now
    return rows
}

private fun railKey(row: RailRow): String = when (row) {
    is RailRow.Header -> "header-${row.title}"
    is RailRow.Event -> row.item.key
    RailRow.Now -> "now-line"
}

@Composable
private fun consumedScreenPadding(fallback: PaddingValues): PaddingValues {
    val local = remember { resolveScreenPaddingLocal() }
    return local?.current ?: fallback
}

private fun resolveScreenPaddingLocal(): CompositionLocal<PaddingValues>? {
    val classes = listOf(
        "com.lifeos.ui.nav.LocalScreenPaddingKt",
        "com.lifeos.ui.shell.LocalScreenPaddingKt",
        "com.lifeos.ui.nav.LifeOsNavKt",
        "com.lifeos.ui.shell.ShellKt",
        "com.lifeos.ui.shell.LifeOsShellKt",
    )
    for (name in classes) {
        val value = runCatching {
            val clazz = Class.forName(name)
            val method = clazz.methods.firstOrNull { method ->
                method.name == "getLocalScreenPadding" && method.parameterCount == 0
            } ?: return@runCatching null
            method.invoke(null)
        }.getOrNull()
        if (value is CompositionLocal<*>) {
            @Suppress("UNCHECKED_CAST")
            return value as CompositionLocal<PaddingValues>
        }
    }
    return null
}
