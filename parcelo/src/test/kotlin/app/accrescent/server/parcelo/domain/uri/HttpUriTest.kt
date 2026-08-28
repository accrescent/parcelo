// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.uri

import app.accrescent.server.parcelo.core.text.u
import app.accrescent.server.parcelo.core.unwrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpUriTest {
    @Test
    fun `fromString accepts http URI`() {
        val result = HttpUri.fromUString("http://example.com".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts https URI`() {
        val result = HttpUri.fromUString("https://example.com".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString rejects uppercase scheme`() {
        val result = HttpUri.fromUString("HTTP://example.com".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString accepts path of single slash`() {
        val result = HttpUri.fromUString("http://example.com/".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts query`() {
        val result = HttpUri.fromUString("http://example.com/path?key=value&key2=value2".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts empty query`() {
        val result = HttpUri.fromUString("http://example.com/path?".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts port`() {
        val result = HttpUri.fromUString("http://example.com:8080".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts empty port`() {
        val result = HttpUri.fromUString("http://example.com:/path".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts port above the TCP port range`() {
        val result = HttpUri.fromUString("http://example.com:99999/path".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts IPv4 host`() {
        val result = HttpUri.fromUString("http://192.0.2.16:80/path".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts IPv6 host`() {
        val result = HttpUri.fromUString("http://[2001:db8::7]/path".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString accepts percent-encoded path`() {
        val result = HttpUri.fromUString("http://example.com/a%20b".u)

        assertTrue(result.isSome())
    }

    @Test
    fun `fromString rejects non-HTTP scheme`() {
        val result = HttpUri.fromUString("ftp://example.com".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects missing scheme`() {
        val result = HttpUri.fromUString("//example.com/path".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects missing authority`() {
        val result = HttpUri.fromUString("http:example.com".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects empty host`() {
        val result = HttpUri.fromUString("http:///path".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects userinfo`() {
        val result = HttpUri.fromUString("http://user@example.com".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects empty userinfo`() {
        val result = HttpUri.fromUString("http://@example.com".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects fragment`() {
        val result = HttpUri.fromUString("http://example.com/path#section".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects non-ASCII host`() {
        val result = HttpUri.fromUString("http://exämple.com".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `fromString rejects empty string`() {
        val result = HttpUri.fromUString("".u)

        assertTrue(result.isNone())
    }

    @Test
    fun `instances with same representation are equal`() {
        val instance1 = HttpUri.fromUString("https://example.com".u)
        val instance2 = HttpUri.fromUString("https://example.com".u)

        assertEquals(instance1, instance2)
    }

    @Test
    fun `fromString and intoString round-trip data`() {
        val rawUri = "https://example.com/path?query=value".u
        val uri = HttpUri.fromUString(rawUri).unwrap()

        val result = uri.intoUString()

        assertEquals(rawUri, result)
    }
}
