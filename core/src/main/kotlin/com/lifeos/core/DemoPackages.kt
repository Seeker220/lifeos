package com.lifeos.core

object DemoPackages {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    const val CHROME = "com.android.chrome"
    const val DOCS = "com.google.android.apps.docs"
    const val MAPS = "com.google.android.apps.maps"
    const val SELF = "com.lifeos.app"

    val SUBSTITUTES: Map<String, String> = mapOf(
        INSTAGRAM to YOUTUBE,
        "com.twitter.android" to YOUTUBE,
        "com.zhiliaoapp.musically" to YOUTUBE,
        "com.facebook.katana" to YOUTUBE,
        "com.reddit.frontpage" to CHROME,
    )

    val ALWAYS_ALLOW: Set<String> = setOf(
        SELF,
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.phone",
        "com.google.android.permissioncontroller",
    )

    val ALIASES: Map<String, String> = mapOf(
        "instagram" to INSTAGRAM,
        "youtube" to YOUTUBE,
        "chrome" to CHROME,
        "docs" to DOCS,
        "google docs" to DOCS,
        "maps" to MAPS,
        "lifeos" to SELF,
    )
}
