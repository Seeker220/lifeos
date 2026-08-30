package com.lifeos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsTest {
    @Test
    fun bareSiteNameExpandsToCdnFamily() {
        val expanded = Domains.expand("youtube")
        assertTrue(expanded.contains("youtube.com"))
        assertTrue(expanded.contains("googlevideo.com"))
        assertTrue(expanded.contains("ytimg.com"))
    }

    @Test
    fun explicitDomainAlsoPullsInItsFamily() {
        val expanded = Domains.expand("https://www.youtube.com/watch?v=abc")
        assertTrue(expanded.contains("googlevideo.com"))
    }

    @Test
    fun normalizeStripsSchemeWildcardAndPath() {
        assertEquals("example.com", Domains.normalize("*.example.com"))
        assertEquals("example.com", Domains.normalize("http://example.com/a/b"))
        assertEquals("example.com", Domains.normalize("example.com."))
        assertNull(Domains.normalize("not a domain"))
        assertNull(Domains.normalize("localhost"))
    }

    @Test
    fun matchesCoversSubdomainsOnly() {
        val blocked = listOf("youtube.com")
        assertTrue(Domains.matches("youtube.com", blocked))
        assertTrue(Domains.matches("www.youtube.com", blocked))
        assertTrue(Domains.matches("m.youtube.com.", blocked))
        assertFalse(Domains.matches("notyoutube.com", blocked))
        assertFalse(Domains.matches("youtube.com.evil.net", blocked))
    }

    @Test
    fun expandAllDeduplicates() {
        val all = Domains.expandAll(listOf("youtube", "youtube.com", "youtu.be"))
        assertEquals(all.distinct().size, all.size)
    }
}
