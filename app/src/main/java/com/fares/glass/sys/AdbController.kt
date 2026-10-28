package com.fares.glass.sys

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sun.security.x509.*
import java.io.File
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/** Keeps our own ADB key + certificate so pairing is only needed once. */
class GlassAdb private constructor(private val ctx: Context) : AbsAdbConnectionManager() {
    private lateinit var priv: PrivateKey
    private lateinit var cert: Certificate

    init { setApi(Build.VERSION.SDK_INT); loadOrCreateKeys() }

    override fun getPrivateKey() = priv
    override fun getCertificate() = cert
    override fun getDeviceName() = "Glass"

    private fun loadOrCreateKeys() {
        val kf = File(ctx.filesDir, "adb_key.pk8")
        val cf = File(ctx.filesDir, "adb_cert.der")
        if (kf.exists() && cf.exists()) {
            try {
                priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
                cert = CertificateFactory.getInstance("X.509").generateCertificate(cf.inputStream())
                return
            } catch (_: Exception) {}
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val alg = "SHA512withRSA"
        val name = X500Name("CN=Glass")
        val info = X509CertInfo().apply {
            set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
            set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(BigInteger.valueOf(System.nanoTime() and 0x7fffffff)))
            set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(AlgorithmId.get(alg)))
            set(X509CertInfo.SUBJECT, name)
            set(X509CertInfo.ISSUER, name)
            set(X509CertInfo.KEY, CertificateX509Key(kp.public))
            set(X509CertInfo.VALIDITY, CertificateValidity(Date(), Date(System.currentTimeMillis() + 3650L * 86_400_000L)))
        }
        val c = X509CertImpl(info).also { it.sign(kp.private, alg) }
        kf.writeBytes(kp.private.encoded); cf.writeBytes(c.encoded)
        priv = kp.private; cert = c
    }

    companion object {
        @Volatile private var inst: GlassAdb? = null
        fun get(c: Context) = inst ?: synchronized(this) { inst ?: GlassAdb(c.applicationContext).also { inst = it } }
    }
}

object AdbController {
    const val PKG = "com.fares.glass"
    const val CMD_WRITE_SECURE = "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
    const val CMD_MEDIA_LISTENER = "cmd notification allow_listener $PKG/$PKG.sys.MediaListener"

    data class Result(val ok: Boolean, val output: String)

    /** Finds the Wireless-debugging port by itself (mDNS), else tries classic port 5555. */
    suspend fun connect(ctx: Context): Result = withContext(Dispatchers.IO) {
        val m = GlassAdb.get(ctx)
        if (m.isConnected) return@withContext Result(true, "Connected")
        runCatching { m.autoConnect(ctx, 5000) }
        if (m.isConnected) return@withContext Result(true, "Connected")
        runCatching { m.connect("127.0.0.1", 5555) }
        if (m.isConnected) Result(true, "Connected")
        else Result(false, "Not paired yet. Turn on Wireless debugging, then pair.")
    }

    /** One-time pairing with the 6-digit code shown by Wireless debugging. */
    suspend fun pair(ctx: Context, port: Int, code: String): Result = withContext(Dispatchers.IO) {
        try {
            GlassAdb.get(ctx).pair("127.0.0.1", port, code)
            connect(ctx)
        } catch (e: Exception) { Result(false, "Pairing failed: ${e.message}") }
    }

    suspend fun run(ctx: Context, command: String): Result {
        if (!connect(ctx).ok) return Result(false, "Not connected")
        return withContext(Dispatchers.IO) {
            try {
                GlassAdb.get(ctx).openStream("shell:$command").use { s ->
                    Result(true, s.openInputStream().bufferedReader().readText().trim().ifEmpty { "Done" })
                }
            } catch (e: Exception) { Result(false, e.message ?: "Command failed") }
        }
    }
}
