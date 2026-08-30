package com.lifeos.agent

import com.lifeos.core.Personas
import com.lifeos.core.model.LifeStateProjection
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptBuilderTest {
    @Test
    fun buildContainsAllActionTypesAndProjection() {
        val projectionJson = """{"today":"2026-08-30","goals":[]}"""
        val prompt = SystemPromptBuilder.build(
            Personas.STRICT,
            LifeStateProjection(projectionJson),
            "talked about gym",
        )
        val types = listOf(
            "create_goal",
            "update_goal",
            "archive_goal",
            "create_task",
            "complete_task",
            "create_event",
            "create_habit",
            "complete_habit_today",
            "add_schedule_block",
            "remember",
            "set_persona",
            "set_alarm",
            "cancel_alarm",
            "set_app_timeout",
            "clear_app_timeout",
            "focus_start",
            "focus_stop",
            "focus_set_apps",
            "set_focus_windows",
            "network_set_mode",
            "network_set_apps",
            "network_set_domains",
            "promote_email",
            "dismiss_email",
            "revert_expansion",
            "award_xp",
        )
        for (type in types) {
            assertTrue("missing $type", prompt.contains(type))
        }
        assertTrue(prompt.contains(projectionJson))
        assertTrue(prompt.contains("talked about gym"))
        assertTrue(prompt.contains(Personas.STRICT.voice))
    }
}
