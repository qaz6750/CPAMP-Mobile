package com.cpamp.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumTest {
    @Test
    fun `parses checksum only for the exact apk filename`() {
        val digest = "a".repeat(64)
        assertEquals(digest, parseSha256("$digest  cpamp-mobile-v1.0.4.apk", "cpamp-mobile-v1.0.4.apk"))
        assertEquals(digest, parseSha256("$digest *cpamp-mobile-v1.0.4.apk", "cpamp-mobile-v1.0.4.apk"))
        assertNull(parseSha256(digest, "cpamp-mobile-v1.0.4.apk"))
        assertNull(parseSha256("$digest  another.apk", "cpamp-mobile-v1.0.4.apk"))
        assertNull(parseSha256("$digest  cpamp-mobile-v1.0.4.apk\nunexpected", "cpamp-mobile-v1.0.4.apk"))
        assertNull(
            parseSha256(
                "$digest  cpamp-mobile-v1.0.4.apk\n$digest  cpamp-mobile-v1.0.4.apk",
                "cpamp-mobile-v1.0.4.apk",
            ),
        )
        assertNull(parseSha256("not-a-digest", "cpamp-mobile-v1.0.4.apk"))
    }

    @Test
    fun `accepts only canonical update apk filenames`() {
        assertTrue(isExpectedUpdateFileName("cpamp-mobile-v1.2.3.apk"))
        assertFalse(isExpectedUpdateFileName("../cpamp-mobile-v1.2.3.apk"))
        assertFalse(isExpectedUpdateFileName("cpamp-mobile-v1.2.3.apk.sha256"))
        assertFalse(isExpectedUpdateFileName("cpamp-mobile-v1.2.apk"))
    }
}
