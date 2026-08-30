package com.lifeos.agent

import com.lifeos.core.model.Action
import com.lifeos.core.model.BlockKind
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.FocusWindow
import com.lifeos.core.model.Hardness
import com.lifeos.core.model.NetworkMode
import com.lifeos.core.model.SkippedAction
import com.lifeos.core.model.TurnSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class ParsedTurn(
    val reply: String,
    val actions: List<Action> = emptyList(),
    val skipped: List<SkippedAction> = emptyList(),
    val source: TurnSource = TurnSource.LLM,
)

object ActionParser {
    fun parse(raw: String): ParsedTurn {
        val extracted = extractJsonObject(stripFences(raw))
        val element = try {
            Json.parseToJsonElement(extracted)
        } catch (e: Exception) {
            return ParsedTurn(
                reply = raw.take(300),
                skipped = listOf(SkippedAction("parse", e.message ?: "invalid json")),
            )
        }
        val obj = element as? JsonObject ?: return ParsedTurn(
            reply = raw.take(300),
            skipped = listOf(SkippedAction("parse", "root is not an object")),
        )
        val reply = obj.str("reply")
        val actionsEl = obj["actions"]
        val array = actionsEl as? JsonArray ?: JsonArray(emptyList())
        val actions = ArrayList<Action>(array.size)
        val skipped = ArrayList<SkippedAction>()
        for (item in array) {
            val actionObj = item as? JsonObject
            if (actionObj == null) {
                skipped += SkippedAction("unknown", "action is not an object")
                continue
            }
            val type = actionObj.str("type")
            if (type.isBlank()) {
                skipped += SkippedAction("unknown", "unknown action type")
                continue
            }
            val parsed = parseAction(type, actionObj)
            if (parsed == null) {
                skipped += SkippedAction(type, "unknown action type")
            } else {
                actions += parsed
            }
        }
        return ParsedTurn(reply = reply, actions = actions, skipped = skipped)
    }

    private fun parseAction(type: String, obj: JsonObject): Action? = when (type) {
        "create_goal" -> Action.CreateGoal(
            id = obj.strOrNull("id"),
            title = obj.str("title"),
            deadlineIso = obj.strOrNull("deadlineIso"),
            hardness = obj.enum("hardness", Hardness.SOFT),
            notes = obj.str("notes"),
        )
        "update_goal" -> Action.UpdateGoal(
            id = obj.str("id"),
            title = obj.strOrNull("title"),
            deadlineIso = obj.strOrNull("deadlineIso"),
            hardness = obj.enumOrNull<Hardness>("hardness"),
            notes = obj.strOrNull("notes"),
        )
        "archive_goal" -> Action.ArchiveGoal(id = obj.str("id"))
        "create_task" -> Action.CreateTask(
            id = obj.strOrNull("id"),
            title = obj.str("title"),
            goalId = obj.strOrNull("goalId"),
            dueIso = obj.strOrNull("dueIso"),
            estMinutes = obj.int("estMinutes", 30),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "complete_task" -> Action.CompleteTask(
            id = obj.strOrNull("id"),
            title = obj.strOrNull("title"),
        )
        "create_event" -> Action.CreateEvent(
            id = obj.strOrNull("id"),
            title = obj.str("title"),
            startIso = obj.str("startIso"),
            endIso = obj.strOrNull("endIso"),
            hardness = obj.enum("hardness", Hardness.HARD),
            emailId = obj.strOrNull("emailId"),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "create_habit" -> Action.CreateHabit(
            id = obj.strOrNull("id"),
            title = obj.str("title"),
            daysOfWeek = obj.intList("daysOfWeek", listOf(1, 2, 3, 4, 5, 6, 7)),
            timeHhmm = obj.str("timeHhmm", "19:00"),
            remindMinutesBefore = obj.intOrNull("remindMinutesBefore"),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "complete_habit_today" -> Action.CompleteHabitToday(
            id = obj.strOrNull("id"),
            title = obj.strOrNull("title"),
        )
        "add_schedule_block" -> Action.AddScheduleBlock(
            id = obj.strOrNull("id"),
            title = obj.str("title"),
            startHhmm = obj.str("startHhmm"),
            endHhmm = obj.str("endHhmm"),
            kind = obj.enum("kind", BlockKind.OTHER),
            daysOfWeek = obj.intList("daysOfWeek"),
            dateIso = obj.strOrNull("dateIso"),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "remember" -> Action.Remember(fact = obj.str("fact"))
        "set_persona" -> Action.SetPersona(personaId = obj.str("personaId"))
        "set_alarm" -> Action.SetAlarm(
            id = obj.strOrNull("id"),
            label = obj.str("label"),
            timeHhmm = obj.str("timeHhmm"),
            personaLine = obj.str("personaLine"),
            triggerAtEpochMs = obj.longOrNull("triggerAtEpochMs"),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "cancel_alarm" -> Action.CancelAlarm(
            id = obj.strOrNull("id"),
            label = obj.strOrNull("label"),
        )
        "set_app_timeout" -> Action.SetAppTimeout(
            packageName = obj.str("packageName"),
            limitMinutes = obj.int("limitMinutes", 0),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "clear_app_timeout" -> Action.ClearAppTimeout(
            packageName = obj.strOrNull("packageName"),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "focus_start" -> Action.FocusStart(
            mode = obj.enumOrNull<FocusMode>("mode"),
            packages = obj.strListOrNull("packages"),
            minutes = obj.intOrNull("minutes"),
        )
        "focus_stop" -> Action.FocusStop
        "focus_set_apps" -> Action.FocusSetApps(
            mode = obj.enum("mode", FocusMode.BLACKLIST),
            packages = obj.strList("packages"),
        )
        "set_focus_windows" -> Action.SetFocusWindows(
            windows = parseWindows(obj["windows"]),
            sourceGoalId = obj.strOrNull("sourceGoalId"),
        )
        "network_set_mode" -> Action.NetworkSetMode(mode = obj.enum("mode", NetworkMode.OFF))
        "network_set_apps" -> Action.NetworkSetApps(packages = obj.strList("packages"))
        "promote_email" -> Action.PromoteEmail(
            candidateId = obj.str("candidateId"),
            titleOverride = obj.strOrNull("titleOverride"),
            startIsoOverride = obj.strOrNull("startIsoOverride"),
        )
        "dismiss_email" -> Action.DismissEmail(candidateId = obj.str("candidateId"))
        "revert_expansion" -> Action.RevertExpansion(goalId = obj.str("goalId"))
        "award_xp" -> Action.AwardXp(
            amount = obj.int("amount", 0),
            reason = obj.str("reason"),
        )
        else -> null
    }

    private fun parseWindows(element: JsonElement?): List<FocusWindow> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val w = item as? JsonObject ?: return@mapNotNull null
            FocusWindow(
                daysOfWeek = w.intList("daysOfWeek"),
                startHhmm = w.str("startHhmm"),
                endHhmm = w.str("endHhmm"),
                mode = w.enum("mode", FocusMode.BLACKLIST),
                packages = w.strList("packages"),
                sourceGoalId = w.strOrNull("sourceGoalId"),
            )
        }
    }

    internal fun stripFences(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            if (s.startsWith("json", ignoreCase = true)) {
                s = s.substring(4)
            }
            s = s.trimStart('\n', '\r', ' ')
            val end = s.lastIndexOf("```")
            if (end >= 0) s = s.substring(0, end)
        }
        return s.trim()
    }

    internal fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        if (start < 0) return raw
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return raw.substring(start)
    }
}

private fun JsonObject.str(key: String, default: String = ""): String {
    val el = this[key] ?: return default
    return when (el) {
        is JsonNull -> default
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() } ?: default
        else -> el.toString()
    }
}

private fun JsonObject.strOrNull(key: String): String? {
    val el = this[key] ?: return null
    return when (el) {
        is JsonNull -> null
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
        else -> el.toString()
    }
}

private fun JsonObject.int(key: String, default: Int): Int = intOrNull(key) ?: default

private fun JsonObject.intOrNull(key: String): Int? = this[key]?.asInt()

private fun JsonObject.longOrNull(key: String): Long? {
    val el = this[key] ?: return null
    if (el is JsonNull) return null
    if (el is JsonPrimitive) {
        el.longOrNull?.let { return it }
        el.intOrNull?.let { return it.toLong() }
        el.content.toLongOrNull()?.let { return it }
        el.doubleOrNull?.let { return it.toLong() }
    }
    return null
}

@Suppress("unused")
private fun JsonObject.bool(key: String, default: Boolean): Boolean {
    val el = this[key] ?: return default
    if (el is JsonNull) return default
    if (el is JsonPrimitive) {
        el.booleanOrNull?.let { return it }
        return when (el.content.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> default
        }
    }
    return default
}

private fun JsonObject.strList(key: String): List<String> = strListOrNull(key) ?: emptyList()

private fun JsonObject.strListOrNull(key: String): List<String>? {
    val el = this[key] ?: return null
    return when (el) {
        is JsonNull -> null
        is JsonPrimitive -> listOf(el.content)
        is JsonArray -> el.map { item ->
            if (item is JsonPrimitive) item.content else item.toString()
        }
        else -> null
    }
}

private fun JsonObject.intList(key: String, default: List<Int> = emptyList()): List<Int> {
    val el = this[key] ?: return default
    return when (el) {
        is JsonNull -> default
        is JsonPrimitive -> listOfNotNull(el.asInt())
        is JsonArray -> el.mapNotNull { it.asInt() }
        else -> default
    }
}

private inline fun <reified T : Enum<T>> JsonObject.enum(key: String, default: T): T =
    enumOrNull<T>(key) ?: default

// Models paraphrase enums ("block" for BLACKLIST, "allow" for WHITELIST). Aliases are
// matched against the target enum's own constants, so they can never cross enum types.
private val ENUM_ALIASES: Map<String, String> = mapOf(
    "block" to "BLACKLIST",
    "blocked" to "BLACKLIST",
    "blocklist" to "BLACKLIST",
    "deny" to "BLACKLIST",
    "denylist" to "BLACKLIST",
    "exclude" to "BLACKLIST",
    "allow" to "WHITELIST",
    "allowlist" to "WHITELIST",
    "allowonly" to "WHITELIST",
    "only" to "WHITELIST",
    "none" to "OFF",
    "disabled" to "OFF",
    "disable" to "OFF",
    "strict" to "HARD",
    "firm" to "HARD",
    "flexible" to "SOFT",
    "easy" to "SOFT",
    "workout" to "GYM",
    "exercise" to "GYM",
    "training" to "GYM",
    "work" to "DEEP_WORK",
    "focus" to "DEEP_WORK",
    "deep" to "DEEP_WORK",
    "studying" to "STUDY",
    "learn" to "STUDY",
)

private inline fun <reified T : Enum<T>> JsonObject.enumOrNull(key: String): T? {
    val raw = strOrNull(key)?.trim() ?: return null
    enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }
    val normalized = raw.lowercase().filter { it.isLetter() }
    enumValues<T>().firstOrNull {
        it.name.replace("_", "").equals(normalized, ignoreCase = true)
    }?.let { return it }
    val alias = ENUM_ALIASES[normalized] ?: return null
    return enumValues<T>().firstOrNull { it.name == alias }
}

private fun JsonElement.asInt(): Int? {
    if (this is JsonNull) return null
    if (this is JsonPrimitive) {
        intOrNull?.let { return it }
        content.toIntOrNull()?.let { return it }
        doubleOrNull?.let { return it.toInt() }
        content.toDoubleOrNull()?.let { return it.toInt() }
    }
    return null
}
