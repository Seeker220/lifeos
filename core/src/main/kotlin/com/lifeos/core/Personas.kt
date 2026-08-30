package com.lifeos.core

data class Persona(
    val id: String,
    val name: String,
    val voice: String,
)

object Personas {
    val STRICT = Persona("strict", "Strict", "Blunt, terse, holds them to the deadline. No pep talk.")
    val SUPPORTIVE = Persona("supportive", "Supportive", "Warm, encouraging, forgiving of one slip.")
    val COACH = Persona("coach", "Coach", "Energetic, competitive, frames work as training.")
    val ALL = listOf(STRICT, SUPPORTIVE, COACH)
    fun byId(id: String): Persona = ALL.firstOrNull { it.id == id } ?: STRICT
}
