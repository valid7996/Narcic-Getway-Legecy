package dev.valid7996.narcicgeteway.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NrgLinkFieldCoverageTest {
    private fun fieldsOf(file: String, className: String): List<String> {
        val pathCandidates = listOf(
            "src/commonMain/kotlin/dev/valid7996/narcicgeteway/domain/config/$file",
            "shared/src/commonMain/kotlin/dev/valid7996/narcicgeteway/domain/config/$file",
            "../shared/src/commonMain/kotlin/dev/valid7996/narcicgeteway/domain/config/$file",
        )
        val f = pathCandidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("cannot find $file under any known prefix (pwd=${File(".").absolutePath})")

        val src = f.readText()
        val classStart = src.indexOf("class $className")
        assertTrue("$className not found in $file", classStart >= 0)
        val ctorStart = src.indexOf('(', classStart)
        var depth = 0
        var ctorEnd = -1
        for (i in ctorStart until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) { ctorEnd = i; break }
                }
            }
        }
        assertTrue("constructor parens for $className didn't close", ctorEnd > ctorStart)
        val ctor = src.substring(ctorStart + 1, ctorEnd)
        val regex = Regex("""(?:val|var)\s+([A-Za-z0-9_]+)""")
        return regex.findAll(ctor).map { it.groupValues[1] }.toList()
    }

    private fun assertAllFieldsPresent(file: String, className: String, source: ProfileSource) {
        val link = NrgLink.build("test", source)
            ?: error("$source produced null link")
        val json = NrgLink.debugJsonOf(link)
            ?: error("failed to extract inner json from $link")
        val fields = fieldsOf(file, className)

        assertTrue("$className: no fields parsed — the extractor is broken", fields.size >= 3)
        val missing = fields.filterNot { json.contains(""$it"") }
        assertTrue("$className is missing from the share link: $missing", missing.isEmpty())
        assertTrue("the check has no teeth", !json.contains(""bogusFieldXyz""))
    }

    @Test
    fun `psiphon carries every field`() =
        assertAllFieldsPresent("PsiphonProfile.kt", "PsiphonProfile", ProfileSource.Psiphon(PsiphonProfile()))
}
