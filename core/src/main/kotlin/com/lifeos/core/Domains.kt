package com.lifeos.core

/**
 * Domain-name handling for DNS-level blocking.
 *
 * Blocking a site by its obvious name is not enough: YouTube serves video from
 * googlevideo.com and thumbnails from ytimg.com, so a request to "block youtube"
 * expands to the whole family.
 */
object Domains {
    val PRESETS: Map<String, List<String>> = mapOf(
        "youtube" to listOf(
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "ytimg.com",
            "googlevideo.com",
            "ggpht.com",
        ),
        "instagram" to listOf("instagram.com", "cdninstagram.com", "instagr.am"),
        "reddit" to listOf("reddit.com", "redd.it", "redditstatic.com", "redditmedia.com"),
        "twitter" to listOf("twitter.com", "twimg.com"),
        "x" to listOf("x.com", "twimg.com"),
        "facebook" to listOf("facebook.com", "fbcdn.net", "fb.com", "fbsbx.com"),
        "tiktok" to listOf("tiktok.com", "tiktokcdn.com", "tiktokv.com"),
        "netflix" to listOf("netflix.com", "nflxvideo.net", "nflximg.net"),
        "snapchat" to listOf("snapchat.com", "sc-cdn.net"),
        "twitch" to listOf("twitch.tv", "ttvnw.net", "jtvnw.net"),
    )

    /** Turns one user or model supplied term into the domains that must be blocked. */
    fun expand(raw: String): List<String> {
        val cleaned = strip(raw) ?: return emptyList()
        PRESETS[cleaned]?.let { return it }
        val normalized = normalize(cleaned) ?: return emptyList()
        val family = PRESETS[registrableLabel(normalized)]
        return if (family != null) (family + normalized).distinct() else listOf(normalized)
    }

    /** "www.youtube.com" and "youtube.com" both key on "youtube". */
    private fun registrableLabel(domain: String): String {
        val labels = domain.split('.')
        return if (labels.size >= 2) labels[labels.size - 2] else domain
    }

    fun expandAll(raw: Collection<String>): List<String> =
        raw.flatMap { expand(it) }.distinct()

    /** Accepts URLs, wildcards and trailing dots; rejects anything that is not a hostname. */
    fun normalize(raw: String): String? {
        val s = strip(raw) ?: return null
        if (!s.contains('.')) return null
        if (s.any { it !in 'a'..'z' && it !in '0'..'9' && it != '.' && it != '-' }) return null
        if (s.startsWith('-') || s.endsWith('-')) return null
        return s
    }

    fun matches(qname: String, blocked: Collection<String>): Boolean {
        if (qname.isEmpty() || blocked.isEmpty()) return false
        val name = qname.trimEnd('.').lowercase()
        return blocked.any { raw ->
            val domain = raw.trimEnd('.').lowercase()
            domain.isNotEmpty() && (name == domain || name.endsWith(".$domain"))
        }
    }

    private fun strip(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/').substringBefore('?')
        s = s.removePrefix("*.").removePrefix(".").removePrefix("www.")
        s = s.substringBefore(':').trimEnd('.')
        return s.ifEmpty { null }
    }
}
