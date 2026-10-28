package dev.valid7996.narcicgeteway.crypto

import dev.valid7996.narcicgeteway.domain.ai.AiProvider
import dev.valid7996.narcicgeteway.domain.ai.AiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedSecretsTest {
    @Test
    fun `AI keys are sealed and read back`() {
        val settings = AiSettings().withKey(AiProvider.GEMINI, "test-api-key")
        val stored = settings.sealedKeys.getValue(AiProvider.GEMINI.name)
        assertTrue(stored.startsWith(AiSettings.SEALED_PREFIX))
        assertFalse(stored.contains("test-api-key"))
        assertEquals("test-api-key", settings.key(AiProvider.GEMINI))
    }
}
