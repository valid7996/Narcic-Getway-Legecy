package dev.valid7996.narcicgeteway.domain.config

import kotlinx.serialization.Serializable

@Serializable
data class SstpProfile(
    val server: String = "",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val sni: String = "",
    val acceptAllCertificates: Boolean = true,
    val authProtocol: String = AUTH_MSCHAPV2,
    val mtu: Int = 1400,
    val mru: Int = 1400,
    val useIpv6: Boolean = false,
    val customCaPem: String = "",
) {
    val isValid: Boolean get() = server.isNotBlank() && username.isNotBlank()

    fun serverHost(): String {
        val s = server.trim()
        val withoutScheme = if (s.contains("://")) s.substringAfter("://") else s
        val hostPart = withoutScheme.substringBefore('/').substringBefore(':')
        return hostPart.trim('[', ']')
    }

    fun effectivePort(): Int = if (port in 1..65535) port else 443

    fun effectiveSni(): String = sni.trim().ifBlank { serverHost() }

    fun toLink(name: String = ""): String {
        val u = percentEncode(username)
        val p = percentEncode(password)
        val h = serverHost()
        val pt = effectivePort()
        val s = percentEncode(effectiveSni())
        val base = "sstp://$u:$p@$h:$pt?sni=$s&trustAll=$acceptAllCertificates&mtu=$mtu"
        return if (name.isNotBlank()) "$base#${percentEncode(name)}" else base
    }

    companion object {
        const val AUTH_MSCHAPV2 = "MSCHAPv2"
        const val AUTH_PAP = "PAP"
        const val AUTH_CHAP = "CHAP"

        fun percentEncode(value: String): String = buildString {
            value.encodeToByteArray().forEach { byte ->
                val b = byte.toInt() and 0xFF
                val c = b.toChar()
                if (b < 0x80 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.~")) {
                    append(c)
                } else {
                    append('%')
                    val hex = "0123456789ABCDEF"
                    append(hex[(b shr 4) and 0xF])
                    append(hex[b and 0xF])
                }
            }
        }

        fun fromLink(link: String): Pair<String, SstpProfile>? {
            val trimmed = link.trim()
            if (!trimmed.startsWith("sstp://", ignoreCase = true)) return null
            return runCatching {
                val withoutScheme = trimmed.substring(7)
                val fragment = if (withoutScheme.contains('#')) withoutScheme.substringAfter('#') else ""
                val name = if (fragment.isNotEmpty()) runCatching { ConfigParser.percentDecode(fragment) }.getOrDefault(fragment) else ""
                val mainPart = withoutScheme.substringBefore('#')
                val userInfo = if (mainPart.contains('@')) mainPart.substringBefore('@') else ""
                val hostAndQuery = if (mainPart.contains('@')) mainPart.substringAfter('@') else mainPart
                val hostPort = hostAndQuery.substringBefore('?')
                val query = if (hostAndQuery.contains('?')) hostAndQuery.substringAfter('?') else ""

                val username = if (userInfo.contains(':')) {
                    runCatching { ConfigParser.percentDecode(userInfo.substringBefore(':')) }.getOrDefault(userInfo.substringBefore(':'))
                } else {
                    userInfo
                }
                val password = if (userInfo.contains(':')) {
                    runCatching { ConfigParser.percentDecode(userInfo.substringAfter(':')) }.getOrDefault(userInfo.substringAfter(':'))
                } else {
                    ""
                }

                val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
                val port = if (hostPort.contains(':')) hostPort.substringAfterLast(':').toIntOrNull() ?: 443 else 443

                var sni = ""
                var trustAll = true
                var mtu = 1400

                if (query.isNotEmpty()) {
                    query.split('&').forEach { param ->
                        val k = param.substringBefore('=').lowercase()
                        val v = param.substringAfter('=', "")
                        when (k) {
                            "sni", "host" -> sni = runCatching { ConfigParser.percentDecode(v) }.getOrDefault(v)
                            "trustall", "insecure", "allowinsecure" -> trustAll = v.toBooleanStrictOrNull() ?: true
                            "mtu" -> mtu = v.toIntOrNull() ?: 1400
                        }
                    }
                }

                name to SstpProfile(
                    server = host,
                    port = port,
                    username = username,
                    password = password,
                    sni = sni,
                    acceptAllCertificates = trustAll,
                    mtu = mtu,
                )
            }.getOrNull()
        }
    }
}
