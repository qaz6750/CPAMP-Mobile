package com.cpamp.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecksumTest {
    @Test
    fun `parses checksum with or without filename`() {
        val digest = "a".repeat(64)
        assertEquals(digest, parseSha256("$digest  cpamp-mobile-v1.0.4.apk", "cpamp-mobile-v1.0.4.apk"))
        assertEquals(digest, parseSha256(digest, "cpamp-mobile-v1.0.4.apk"))
        assertNull(parseSha256("not-a-digest", "cpamp-mobile-v1.0.4.apk"))
    }
}