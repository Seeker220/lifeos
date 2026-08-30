package com.lifeos.core

object LifeOsLog {
    var sink: ((tag: String, msg: String) -> Unit)? = null

    fun d(tag: String, msg: String) {
        sink?.invoke(tag, msg) ?: println("[$tag] $msg")
    }
}
