package com.lifeos.core

import kotlin.random.Random

object Ids {
    private val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun new(prefix: String): String {
        val suffix = buildString {
            repeat(8) { append(alphabet[Random.nextInt(alphabet.length)]) }
        }
        return "${prefix}_$suffix"
    }
}
