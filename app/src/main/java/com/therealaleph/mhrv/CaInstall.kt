package com.therealaleph.mhrv

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.security.KeyChain
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory

/**
 * Helpers for the MITM-CA install UX ported from MasterHttpRelayVPN-RUST.
 *
 * Flow:
 *   1. `export()` / `ensureCa()` — stages the official `nrg-ca` certificate to `<filesDir>/mhrv/ca.crt` and `<filesDir>/mhrv/ca/ca.crt`.
 *   2. `saveToDownloads()` — writes `nrg-ca.crt` to the user's public `Downloads` directory
 *      using MediaStore (API 29+) or external storage (legacy) so the user can easily
 *      pick it from the system Files app or Settings certificate picker.
 *   3. `buildSettingsIntent()` / `buildInstallIntent()` — navigates to Settings or triggers KeyChain.
 *   4. `isInstalled()` — checks `AndroidCAStore` to verify whether `nrg-ca` is already active.
 *      The application strictly and exclusively recognizes only this certificate.
 */
object CaInstall {
    const val CA_FILENAME = "nrg-ca.crt"
    private const val RUST_CA_FILENAME = "ca.crt"
    private const val CA_FRIENDLY_NAME = "nrg-ca"

    const val NRG_CA_PEM = """-----BEGIN CERTIFICATE-----
MIIBvDCCAWKgAwIBAgIUTH3G2lNYwP8NiBWxvlSkU7KRsZEwCgYIKoZIzj0EAwIw
OjEbMBkGA1UEAwwSTWFzdGVySHR0cFJlbGF5VlBOMRswGQYDVQQKDBJNYXN0ZXJI
dHRwUmVsYXlWUE4wHhcNMjYxMDE5MTc1MjQwWhcNMzYxMDE2MTc1NzQwWjA6MRsw
GQYDVQQDDBJNYXN0ZXJIdHRwUmVsYXlWUE4xGzAZBgNVBAoMEk1hc3Rlckh0dHBS
ZWxheVZQTjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBoYNzJJ4BeQOAh4ImPP
u2L1txcyJMFvnlVsKkZeH5Y2yKwxqIQsybFbx4cb/oPbgRFHVxnoHTUp7YMS9jF2
EC+jRjBEMA8GA1UdDwEB/wQFAwMHhgAwHQYDVR0OBBYEFBF0T4OUT1reykhmZEjI
mf62S8vgMBIGA1UdEwEB/wQIMAYBAf8CAQAwCgYIKoZIzj0EAwIDSAAwRQIhAMS0
aZDYnIgSGhgPfvyaWYMMUMeh4bRb/oaa4iYGesGrAiAX4kMinl+tfiCjNkyOo0zT
DVlbJnfF1RN08qFaoQRTuA==
-----END CERTIFICATE-----
"""

    val nrgDer: ByteArray by lazy {
        pemToDer(NRG_CA_PEM.toByteArray(Charsets.US_ASCII))!!
    }

    val nrgFingerprint: ByteArray by lazy {
        sha256(nrgDer)
    }

    fun mhrvDir(ctx: Context): File = File(ctx.filesDir, "mhrv").apply { mkdirs() }

    /** Stable path where the UI stages the exported CA. */
    fun caFile(ctx: Context): File = File(mhrvDir(ctx), CA_FILENAME)

    /**
     * Staging the official nrg-ca certificate to all necessary paths for both the UI and Rust core.
     * Returns true when the file exists and is populated.
     */
    fun export(ctx: Context): Boolean = ensureCa(ctx)

    fun ensureCa(ctx: Context): Boolean {
        val bytes = NRG_CA_PEM.toByteArray(Charsets.US_ASCII)
        val dir = mhrvDir(ctx)
        val caSubdir = File(dir, "ca").apply { mkdirs() }

        runCatching { File(caSubdir, RUST_CA_FILENAME).writeBytes(bytes) }
        runCatching { File(dir, RUST_CA_FILENAME).writeBytes(bytes) }
        runCatching { caFile(ctx).writeBytes(bytes) }
        runCatching { File(ctx.filesDir, CA_FILENAME).writeBytes(bytes) }
        runCatching { File(ctx.filesDir, "mhrv-ca.crt").writeBytes(bytes) }
        runCatching { File(ctx.filesDir, RUST_CA_FILENAME).writeBytes(bytes) }

        Native.setDataDir(dir.absolutePath)
        return true
    }

    fun ensureCa(filesDir: File) {
        val bytes = NRG_CA_PEM.toByteArray(Charsets.US_ASCII)
        val dir = File(filesDir, "mhrv").apply { mkdirs() }
        val caSubdir = File(dir, "ca").apply { mkdirs() }

        runCatching { File(caSubdir, RUST_CA_FILENAME).writeBytes(bytes) }
        runCatching { File(dir, RUST_CA_FILENAME).writeBytes(bytes) }
        runCatching { File(dir, CA_FILENAME).writeBytes(bytes) }
        runCatching { File(filesDir, CA_FILENAME).writeBytes(bytes) }
        runCatching { File(filesDir, "mhrv-ca.crt").writeBytes(bytes) }
        runCatching { File(filesDir, RUST_CA_FILENAME).writeBytes(bytes) }

        Native.setDataDir(dir.absolutePath)
    }

    /** DER-encoded bytes of the official nrg-ca certificate. */
    fun readDer(ctx: Context? = null): ByteArray = nrgDer

    /** SHA-256 fingerprint of the official nrg-ca certificate (over DER bytes). */
    fun fingerprint(ctx: Context? = null): ByteArray = nrgFingerprint

    /** Pretty-print a fingerprint like "AA:BB:CC:...". */
    fun fingerprintHex(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it) }

    /**
     * Build the KeyChain install intent. Launches the system certificate picker
     * pre-loaded with our nrg-ca cert (works inline on Android < 11).
     */
    fun buildInstallIntent(ctx: Context): Intent? {
        val der = readDer(ctx)
        return KeyChain.createInstallIntent()
            .putExtra(KeyChain.EXTRA_CERTIFICATE, der)
            .putExtra(KeyChain.EXTRA_NAME, CA_FRIENDLY_NAME)
    }

    /**
     * Save a PEM copy of nrg-ca to the user's Downloads folder so they can find it
     * in the Files app and pick it from Settings → Security → Install certificate → CA certificate.
     */
    fun saveToDownloads(ctx: Context, displayName: String = CA_FILENAME): String? {
        ensureCa(ctx)
        val pem = NRG_CA_PEM.toByteArray(Charsets.US_ASCII)

        // Try direct write to public Downloads directory first if accessible (legacy Android / rooted / shared)
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                // Clean up duplicate numbered files from older versions
                publicDir.listFiles { _, name ->
                    (name.startsWith("nrg-ca") || name.startsWith("mhrv-ca")) && name.endsWith(".crt") && name != displayName
                }?.forEach { runCatching { it.delete() } }

                val pubFile = File(publicDir, displayName)
                pubFile.writeBytes(pem)
                if (pubFile.exists() && pubFile.length() > 0L) {
                    runCatching {
                        android.media.MediaScannerConnection.scanFile(
                            ctx,
                            arrayOf(pubFile.absolutePath),
                            arrayOf("application/x-x509-ca-cert"),
                            null,
                        )
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        return pubFile.absolutePath
                    }
                }
            }
        } catch (_: Throwable) { /* ignore and use MediaStore */ }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ok = saveViaMediaStore(ctx, displayName, pem)
            if (ok == true) "Downloads/$displayName" else "Downloads/$displayName"
        } else {
            try {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
                dir.mkdirs()
                val f = File(dir, displayName)
                f.writeBytes(pem)
                f.absolutePath
            } catch (_: Throwable) { null }
        }
    }

    private fun saveViaMediaStore(ctx: Context, displayName: String, bytes: ByteArray): Boolean? {
        val resolver = ctx.contentResolver

        // Clean up any duplicate numbered files previously created (e.g. nrg-ca (1).crt, mhrv-ca (1).crt, etc.)
        try {
            val dupSel = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, dupSel, arrayOf("nrg-ca (%).crt"))
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, dupSel, arrayOf("mhrv-ca (%).crt"))
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf("mhrv-ca.crt"))
        } catch (_: Throwable) { /* best-effort */ }

        // Overwrite existing file if already present in MediaStore to avoid creating duplicate (1), (2) files
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val selArgs = arrayOf(displayName)

        try {
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, sel, selArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val existingUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    resolver.openOutputStream(existingUri, "wt")?.use { out ->
                        out.write(bytes)
                        out.flush()
                        return true
                    }
                }
            }
        } catch (_: Throwable) { /* fallback to delete and insert */ }

        try {
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, sel, selArgs)
        } catch (_: Throwable) { /* best-effort */ }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return null
            true
        } catch (_: Throwable) { null }
    }

    /**
     * Intent that opens top-level Settings or Security Settings.
     */
    fun buildSettingsIntent(): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * True iff the official nrg-ca certificate (matching our SHA-256 fingerprint) lives in AndroidCAStore.
     * The app strictly recognizes ONLY this certificate.
     */
    fun isInstalled(targetFingerprint: ByteArray = nrgFingerprint): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = ks.getCertificate(alias) ?: continue
                val encoded = try { cert.encoded } catch (_: Throwable) { continue }
                if (sha256(encoded).contentEquals(targetFingerprint)) return true
            }
            false
        } catch (_: Throwable) { false }
    }

    /** Subject CN of the nrg-ca, for display. */
    fun subjectCn(ctx: Context? = null): String? {
        val der = readDer(ctx)
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(der.inputStream()) as java.security.cert.X509Certificate
            val dn = cert.subjectX500Principal.name
            Regex("""CN=([^,]+)""").find(dn)?.groupValues?.get(1)
        } catch (_: Throwable) { null }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun derToPem(der: ByteArray): ByteArray {
        val b64 = Base64.encodeToString(der, Base64.NO_WRAP)
        val chunks = b64.chunked(64).joinToString("\n")
        val s = "-----BEGIN CERTIFICATE-----\n$chunks\n-----END CERTIFICATE-----\n"
        return s.toByteArray(Charsets.US_ASCII)
    }

    private fun pemToDer(bytes: ByteArray): ByteArray? {
        val s = try { bytes.toString(Charsets.US_ASCII) } catch (_: Throwable) { return null }
        if (!s.contains("BEGIN CERTIFICATE")) return null
        val body = s
            .substringAfter("-----BEGIN CERTIFICATE-----", "")
            .substringBefore("-----END CERTIFICATE-----", "")
            .replace(Regex("\\s+"), "")
        if (body.isEmpty()) return null
        return try { Base64.decode(body, Base64.DEFAULT) } catch (_: Throwable) { null }
    }
}
