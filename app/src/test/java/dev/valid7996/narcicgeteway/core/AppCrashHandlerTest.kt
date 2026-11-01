package dev.valid7996.narcicgeteway.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppCrashHandlerTest {
    @Test
    fun crashHandler_basicSanity() {
        assertNotNull(AppCrashHandler)
        assertNotNull(AdbLogcat)
    }

    @Test
    fun adbLogcat_dumpDoesNotThrow() {
        val dump = runCatching { AdbLogcat.dump(10) }.getOrNull()
        assertNotNull(dump)
    }
}
