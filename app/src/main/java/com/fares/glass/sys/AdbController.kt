package com.fares.glass.sys

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

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
            } catch (_: Throwable) {
                kf.delete(); cf.delete()
            }
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val c = CertUtil.selfSigned(kp)
        kf.writeBytes(kp.private.encoded); cf.writeBytes(c.encoded)
        priv = kp.private; cert = c
    }

    companion object {
        @Volatile private var inst: GlassAdb? = null
        @Volatile private var providerReady = false

        private fun ensureProvider() {
            if (providerReady) return
            runCatching { Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) }
            providerReady = true
        }

        fun get(c: Context): GlassAdb {
            ensureProvider()
            return inst ?: synchronized(this) {
                inst ?: GlassAdb(c.applicationContext).also { inst = it }
            }
        }
    }
}

object AdbController {
    const val PKG = "com.fares.glass"
    const val CMD_WRITE_SECURE = "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
    const val CMD_MEDIA_LISTENER = "cmd notification allow_listener $PKG/$PKG.sys.MediaListener"

    data class Result(val ok: Boolean, val output: String)

    private fun err(t: Throwable) = "ADB error: ${t.javaClass.simpleName}: ${t.message ?: ""}".trim()

    /** Finds the Wireless-debugging port by itself (mDNS), else tries classic port 5555. */
    suspend fun connect(ctx: Context): Result = withContext(Dispatchers.IO) {
        try {
            val m = GlassAdb.get(ctx)
            if (m.isConnected) return@withContext Result(true, "Connected")
            runCatching { m.autoConnect(ctx, 5000) }
            if (m.isConnected) return@withContext Result(true, "Connected")
            runCatching { m.connect("127.0.0.1", 5555) }
            if (m.isConnected) Result(true, "Connected")
            else Result(false, "Not paired yet. Turn on Wireless debugging, then pair.")
        } catch (t: Throwable) { Result(false, err(t)) }
    }

    /** One-time pairing with the 6-digit code shown by Wireless debugging. */
    suspend fun pair(ctx: Context, port: Int, code: String): Result = withContext(Dispatchers.IO) {
        try {
            GlassAdb.get(ctx).pair("127.0.0.1", port, code)
            connect(ctx)
        } catch (t: Throwable) { Result(false, "Pairing failed: ${err(t)}") }
    }

    suspend fun run(ctx: Context, command: String): Result {
        val c = connect(ctx)
        if (!c.ok) return Result(false, c.output)
        return withContext(Dispatchers.IO) {
            try {
                GlassAdb.get(ctx).openStream("shell:$command").use { s ->
                    Result(true, s.openInputStream().bufferedReader().readText().trim().ifEmpty { "Done" })
                }
            } catch (t: Throwable) { Result(false, err(t)) }
        }
    }
}
