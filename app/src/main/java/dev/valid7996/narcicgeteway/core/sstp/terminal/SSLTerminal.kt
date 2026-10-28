package dev.valid7996.narcicgeteway.core.sstp.terminal

import android.os.Build
import dev.valid7996.narcicgeteway.core.sstp.ControlMessage
import dev.valid7996.narcicgeteway.core.sstp.Result
import dev.valid7996.narcicgeteway.core.sstp.SharedBridge
import dev.valid7996.narcicgeteway.core.sstp.Where
import dev.valid7996.narcicgeteway.core.sstp.extension.capacityAfterLimit
import dev.valid7996.narcicgeteway.core.sstp.extension.slide
import dev.valid7996.narcicgeteway.core.sstp.extension.toIntAsUByte
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val HTTP_DELIMITER = "\r\n"
private const val HTTP_SUFFIX = "\r\n\r\n"

internal const val SSL_REQUEST_INTERVAL = 15_000L

internal class SSLTerminal(private val bridge: SharedBridge) {
    private val mutex = Mutex()

    private var socket: Socket? = null
    private lateinit var socketInputStream: InputStream
    private lateinit var socketOutputStream: OutputStream
    private lateinit var inboundBuffer: ByteBuffer
    private lateinit var outboundBuffer: ByteBuffer

    private lateinit var engine: SSLEngine

    private var jobInitialize: Job? = null

    private val sslHostname = bridge.HOME_HOSTNAME
    private val sslPort = bridge.SSL_PORT

    internal fun initialize() {
        jobInitialize = bridge.scope.launch(bridge.handler) {
            bridge.log("Initiating SSL connection to $sslHostname:$sslPort...")
            if (!establishSSL()) return@launch

            bridge.log("Sending SSTP HTTP handshake...")
            if (!establishHttp()) return@launch

            bridge.log("SSL and HTTP handshake established successfully.")
            bridge.controlMailbox.send(ControlMessage(Where.SSL, Result.PROCEEDED))
        }
    }

    private fun createTrustAllManagers(): Array<TrustManager> {
        return arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
    }

    private suspend fun establishSSL(): Boolean {
        try {
            val sslContext = if (!bridge.SSL_DO_VERIFY) {
                SSLContext.getInstance("TLS").apply {
                    init(null, createTrustAllManagers(), SecureRandom())
                }
            } else {
                SSLContext.getDefault()
            }

            engine = sslContext.createSSLEngine(sslHostname, sslPort)
            engine.useClientMode = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && bridge.SSL_DO_USE_CUSTOM_SNI) {
                engine.sslParameters = engine.sslParameters.apply {
                    serverNames = listOf(SNIHostName(bridge.SSL_CUSTOM_SNI))
                }
            }

            bridge.log("Connecting TCP socket to $sslHostname:$sslPort...")
            val s = Socket(sslHostname, sslPort).also {
                it.tcpNoDelay = true
                socketInputStream = it.getInputStream()
                socketOutputStream = it.getOutputStream()
            }
            socket = s

            inboundBuffer = ByteBuffer.allocate(engine.session.packetBufferSize).also { it.limit(0) }
            outboundBuffer = ByteBuffer.allocate(engine.session.packetBufferSize)

            bridge.log("Starting TLS handshake with server (SNI: ${bridge.SSL_CUSTOM_SNI})...")
            startSSLHandshake()

            if (bridge.SSL_DO_VERIFY) {
                val verified = HttpsURLConnection.getDefaultHostnameVerifier().verify(sslHostname, engine.session)
                if (!verified) {
                    bridge.logError("SSL hostname verification failed for $sslHostname")
                    bridge.controlMailbox.send(ControlMessage(Where.SSL, Result.ERR_VERIFICATION_FAILED))
                    return false
                }
            }

            bridge.log("TLS handshake finished. Protocol: ${engine.session.protocol}, Cipher: ${engine.session.cipherSuite}")
            return true
        } catch (e: Exception) {
            bridge.logError("TLS handshake error: ${e.message}")
            bridge.controlMailbox.send(ControlMessage(Where.SSL, Result.ERR_TIMEOUT, e.message))
            return false
        }
    }

    private suspend fun startSSLHandshake() {
        val tempBuffer = ByteBuffer.allocate(0)
        engine.beginHandshake()

        while (true) {
            yield()
            when (engine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    val result = send(tempBuffer)
                    if (result.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
                        break
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    val result = receive(tempBuffer)
                    if (result.handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED) {
                        break
                    }
                }
                else -> {
                    break
                }
            }
        }
    }

    private suspend fun establishHttp(): Boolean {
        return try {
            val buffer = ByteBuffer.allocate(getApplicationBufferSize())

            val request = arrayOf(
                "SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1",
                "Content-Length: 18446744073709551615",
                "Host: $sslHostname",
                "SSTPCORRELATIONID: {${bridge.guid}}",
            ).joinToString(separator = HTTP_DELIMITER, postfix = HTTP_SUFFIX).toByteArray(Charsets.US_ASCII)

            buffer.put(request)
            buffer.flip()
            send(buffer)

            buffer.position(0)
            buffer.limit(0)

            var response = ""
            outer@ while (true) {
                receive(buffer)

                for (i in 0 until buffer.remaining()) {
                    response += buffer.get().toIntAsUByte().toChar()
                    if (response.endsWith(HTTP_SUFFIX)) {
                        break@outer
                    }
                }
            }

            val statusLine = response.split(HTTP_DELIMITER).firstOrNull() ?: ""
            bridge.log("HTTP Response: $statusLine")

            if (!statusLine.contains("200")) {
                bridge.logError("HTTP Handshake failed with status: $statusLine")
                bridge.controlMailbox.send(ControlMessage(Where.SSL, Result.ERR_UNEXPECTED_MESSAGE, statusLine))
                return false
            }

            socket?.soTimeout = 1_000
            bridge.service.protect(socket)
            true
        } catch (e: Exception) {
            bridge.logError("establishHttp error: ${e.message}")
            bridge.controlMailbox.send(ControlMessage(Where.SSL, Result.ERR_UNEXPECTED_MESSAGE, e.message))
            false
        }
    }

    internal fun getSession(): SSLSession = engine.session

    internal fun getServerCertificate(): ByteArray = engine.session.peerCertificates[0].encoded

    internal fun getApplicationBufferSize(): Int = engine.session.applicationBufferSize

    internal fun receive(buffer: ByteBuffer): SSLEngineResult {
        var startPayload: Int
        var result: SSLEngineResult

        while (true) {
            startPayload = buffer.position()
            buffer.position(buffer.limit())
            buffer.limit(buffer.capacity())

            result = engine.unwrap(inboundBuffer, buffer)

            when (result.status) {
                SSLEngineResult.Status.OK -> break
                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                    buffer.limit(buffer.position())
                    buffer.position(startPayload)
                    buffer.slide()
                }
                SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                    buffer.limit(buffer.position())
                    buffer.position(startPayload)
                    inboundBuffer.slide()

                    try {
                        val readSize = socketInputStream.read(
                            inboundBuffer.array(),
                            inboundBuffer.limit(),
                            inboundBuffer.capacityAfterLimit,
                        )
                        if (readSize > 0) {
                            bridge.rxBytes.addAndGet(readSize.toLong())
                            inboundBuffer.limit(inboundBuffer.limit() + readSize)
                        }
                    } catch (_: SocketTimeoutException) { }
                }
                else -> throw NotImplementedError(result.status.name)
            }
        }

        buffer.limit(buffer.position())
        buffer.position(startPayload)
        return result
    }

    internal suspend fun send(buffer: ByteBuffer): SSLEngineResult {
        mutex.withLock {
            var result: SSLEngineResult

            while (true) {
                outboundBuffer.clear()
                result = engine.wrap(buffer, outboundBuffer)
                if (result.status != SSLEngineResult.Status.OK) {
                    throw NotImplementedError(result.status.name)
                }

                val bytesToSend = outboundBuffer.position()
                socketOutputStream.write(outboundBuffer.array(), 0, bytesToSend)
                if (bytesToSend > 0) {
                    bridge.txBytes.addAndGet(bytesToSend.toLong())
                }

                if (!buffer.hasRemaining()) {
                    socketOutputStream.flush()
                    break
                }
            }

            return result
        }
    }

    internal fun close() {
        jobInitialize?.cancel()
        runCatching { socket?.close() }
    }
}
