package com.lifeos.agent

import com.lifeos.core.DemoPackages
import com.lifeos.core.Time
import com.lifeos.core.model.Action
import com.lifeos.core.model.BlockKind
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.FocusWindow
import com.lifeos.core.model.Hardness
import com.lifeos.core.model.NetworkMode
import com.lifeos.core.model.TurnSource

object OfflineFallbacks {
    @Suppress("UNUSED_PARAMETER")
    fun match(userText: String, state: CanonicalLifeState): ParsedTurn {
        val lower = userText.lowercase()
        val scored = listOf(
            googleScore(lower) to { googleInterview() },
            whitelistScore(lower) to { whitelistFocus() },
            deadlineScore(lower) to { deadlineDoomscroll() },
            emailScore(lower) to { emailCheck() },
            0 to { generic(userText) },
        )
        val winner = scored.filter { it.first >= 0 }.maxBy { it.first }
        return winner.second()
    }

    private fun googleScore(lower: String): Int =
        scoreAnyTwo(lower, listOf("interview", "google", "crack", "month", "faang", "dsa", "leetcode"))

    private fun whitelistScore(lower: String): Int {
        if (!lower.contains("focus")) return -1
        val extras = listOf("whitelist", "only", "just").count { lower.contains(it) }
        return if (extras > 0) 1 + extras else -1
    }

    private fun deadlineScore(lower: String): Int =
        scoreAnyTwo(lower, listOf("assignment", "due", "tuesday", "instagram", "doomscroll", "wake"))

    private fun emailScore(lower: String): Int {
        val n = listOf("email", "inbox", "mail", "exam").count { lower.contains(it) }
        return if (n > 0) n else -1
    }

    private fun scoreAnyTwo(lower: String, words: List<String>): Int {
        val n = words.count { lower.contains(it) }
        return if (n >= 2) n else -1
    }

    private fun googleInterview(): ParsedTurn {
        val today = Time.todayIso()
        val deadline = Time.plusDaysIso(today, 30)
        val due3 = Time.plusDaysIso(today, 3)
        val due7 = Time.plusDaysIso(today, 7)
        val goalId = "g_google"
        return ParsedTurn(
            reply = "One month. I've put interview grind on your calendar 7 to 9 on weekdays, " +
                "LeetCode daily, and a mock every Saturday. Instagram is capped at 30 minutes a day " +
                "and YouTube at 45. Don't argue with me on day three.",
            actions = listOf(
                Action.CreateGoal(
                    id = goalId,
                    title = "Crack Google interview",
                    deadlineIso = deadline,
                    hardness = Hardness.HARD,
                ),
                Action.CreateHabit(
                    title = "LeetCode daily",
                    daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                    timeHhmm = "19:00",
                    sourceGoalId = goalId,
                ),
                Action.CreateHabit(
                    title = "Mock interview",
                    daysOfWeek = listOf(6),
                    timeHhmm = "10:00",
                    sourceGoalId = goalId,
                ),
                Action.AddScheduleBlock(
                    title = "Interview grind",
                    startHhmm = "19:00",
                    endHhmm = "21:00",
                    kind = BlockKind.STUDY,
                    daysOfWeek = listOf(1, 2, 3, 4, 5),
                    sourceGoalId = goalId,
                ),
                Action.CreateTask(
                    title = "Graphs and trees set",
                    dueIso = due3,
                    estMinutes = 120,
                    sourceGoalId = goalId,
                ),
                Action.CreateTask(
                    title = "System design notes: caching, sharding",
                    dueIso = due7,
                    estMinutes = 90,
                    sourceGoalId = goalId,
                ),
                Action.SetAppTimeout(
                    packageName = DemoPackages.INSTAGRAM,
                    limitMinutes = 30,
                    sourceGoalId = goalId,
                ),
                Action.SetAppTimeout(
                    packageName = DemoPackages.YOUTUBE,
                    limitMinutes = 45,
                    sourceGoalId = goalId,
                ),
                Action.SetFocusWindows(
                    windows = listOf(
                        FocusWindow(
                            daysOfWeek = listOf(1, 2, 3, 4, 5),
                            startHhmm = "19:00",
                            endHhmm = "21:00",
                            mode = FocusMode.BLACKLIST,
                            packages = listOf(DemoPackages.INSTAGRAM, DemoPackages.YOUTUBE),
                            sourceGoalId = goalId,
                        ),
                    ),
                    sourceGoalId = goalId,
                ),
                Action.SetAlarm(
                    label = "bedtime-check",
                    timeHhmm = "22:30",
                    personaLine = "LeetCode done? Don't lie to me.",
                    sourceGoalId = goalId,
                ),
                Action.Remember(fact = "Google interview in one month; strict on social app timeouts"),
            ),
            source = TurnSource.OFFLINE_FALLBACK,
        )
    }

    private fun whitelistFocus(): ParsedTurn {
        val allow = listOf(DemoPackages.CHROME, DemoPackages.DOCS, DemoPackages.SELF)
        return ParsedTurn(
            reply = "Whitelist only: Chrome, Docs, LifeOS. Fifty minutes. Everything else can wait.",
            actions = listOf(
                Action.FocusSetApps(mode = FocusMode.WHITELIST, packages = allow),
                Action.NetworkSetMode(mode = NetworkMode.WHITELIST),
                Action.NetworkSetApps(packages = allow),
                Action.FocusStart(mode = FocusMode.WHITELIST, packages = allow, minutes = 50),
                Action.Remember(fact = "User asked for whitelist-only focus (Chrome, Docs, LifeOS)"),
            ),
            source = TurnSource.OFFLINE_FALLBACK,
        )
    }

    private fun deadlineDoomscroll(): ParsedTurn {
        val today = Time.todayIso()
        val due = Time.plusDaysIso(today, 2)
        val goalId = "g_assignment"
        return ParsedTurn(
            reply = "Due in two days. Alarm at 7, Instagram at 20 minutes, assignment on the list. Wake up.",
            actions = listOf(
                Action.CreateGoal(
                    id = goalId,
                    title = "Finish the assignment",
                    deadlineIso = due,
                    hardness = Hardness.HARD,
                ),
                Action.CreateTask(
                    title = "Draft and submit the assignment",
                    dueIso = due,
                    estMinutes = 90,
                    sourceGoalId = goalId,
                ),
                Action.SetAlarm(
                    label = "wake-up",
                    timeHhmm = "07:00",
                    personaLine = "Assignment. Up. Now.",
                    sourceGoalId = goalId,
                ),
                Action.SetAppTimeout(
                    packageName = DemoPackages.INSTAGRAM,
                    limitMinutes = 20,
                    sourceGoalId = goalId,
                ),
                Action.FocusSetApps(
                    mode = FocusMode.BLACKLIST,
                    packages = listOf(DemoPackages.INSTAGRAM),
                ),
                Action.Remember(fact = "Assignment due in two days; Instagram capped; 07:00 wake-up"),
            ),
            source = TurnSource.OFFLINE_FALLBACK,
        )
    }

    private fun emailCheck(): ParsedTurn = ParsedTurn(
        reply = "Check the Inbox tab. I'll flag exams and deadlines there.",
        actions = emptyList(),
        source = TurnSource.OFFLINE_FALLBACK,
    )

    private fun generic(userText: String): ParsedTurn {
        val trimmed = userText.trim()
        return if (looksActionable(trimmed)) {
            ParsedTurn(
                reply = "Noted. It's on your list.",
                actions = listOf(Action.CreateTask(title = trimmed.take(80))),
                source = TurnSource.OFFLINE_FALLBACK,
            )
        } else {
            ParsedTurn(
                reply = "I'm here. Give me a goal, a habit, or something to lock down — " +
                    "I'll put it on your schedule and hold you to it.",
                actions = emptyList(),
                source = TurnSource.OFFLINE_FALLBACK,
            )
        }
    }

    // Greetings and questions must never silently become todos.
    private fun looksActionable(text: String): Boolean {
        if (text.endsWith("?")) return false
        if (text.split(WHITESPACE).size < 2) return false
        val lower = text.lowercase()
        return ACTION_VERBS.any { lower.contains(it) }
    }

    private val WHITESPACE = Regex("\\s+")

    private val ACTION_VERBS = listOf(
        "remind", "add ", "schedule", "plan ", "study", "gym", "workout", "read ",
        "call ", "buy ", "finish", "submit", "practice", "revise", "wake", "block",
        "limit", "cap ", "todo", "task", "meeting", "clean", "pay ", "book ",
        "write", "prepare", "start", "stop", "focus", "goal", "habit",
    )
}
