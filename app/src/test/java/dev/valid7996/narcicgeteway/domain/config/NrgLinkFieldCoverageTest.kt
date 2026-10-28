package dev.valid7996.narcicgeteway.domain.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class NrgLinkFieldCoverageTest {
    private val srcDir = File("../shared/src/commonMain/kotlin/dev/valid7996/narcicgeteway/domain/config")

    private fun fieldsOf(file: String, className: String): List<String> {
        val text = File(srcDir, file).readText()
        val start = text.indexOf("data class $className(")
        require(start >= 0) { "$className not found in $file" }
        var depth = 0
        var i = text.indexOf('(', start)
        val body = StringBuilder()
        while (i < text.length) {
            val c = text[i]
            if (c == '(') depth++
            else if (c == ')') { depth--; if (depth == 0) break }
            if (depth >= 1) body.append(c)
            i++
        }
        return Regex("""^\s*val\s+(\w+)\s*:""", RegexOption.MULTILINE)
            .findAll(body.toString()).map { it.groupValues[1] }.toList()
    }

    private fun payloadJson(source: ProfileSource): String {
        val link = NrgLink.build("n", source)!!
        val body = link.removePrefix(NrgLink.SCHEME)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
            .decode(body).decodeToString()
    }

    private fun assertAllFieldsPresent(file: String, className: String, source: ProfileSource) {
        val json = payloadJson(source)
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
