package com.lifeos.data

import kotlinx.serialization.json.Json

object LifeOsJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
}
