package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the bundled dnstt-client (SlowDNS) helper process.
 *
 * The client is a standalone Go binary (assets/dnstt-client) that listens on
 * 127.0.0.1:18080 as a SOCKS5 server and forwards each connection through one
 * shared DNSTT session to the user's NS. It is started when the selected
 * server has DNSTT enabled and stopped when the core stops or the selected
 * server changes. The NS domain, server public key and resolver come from the
 * selected server's settings only: they are passed as process arguments and
 * never written to disk or logged.
 *
 * Lifetime: owned by [CoreServiceManager]. The client is started in
 * [CoreServiceManager.launchCore] before the core loop starts (so the SOCKS
 * listener is up before Xray dials through it) and stopped in
 * [CoreServiceManager.stopCoreLoop]. A network-handover reload re-enters
 * launchCore, which is idempotent for unchanged settings.
 */
object DnsttClientManager {
    private var process: Process? = null
    private var runningSettings: DnsttSettings? = null

    private data class DnsttSettings(val ns: String, val pubkey: String, val resolver: String)

    /**
     * Starts the client for [profile] if it has DNSTT enabled, or stops a
     * running client when it does not. Idempotent: a client already running
     * with the same settings is left alone; a settings change restarts it.
     *
     * Runs synchronously: the asset copy is small and bounded (one binary,
     * ~4 MB) and launchCore already performs synchronous blocking work on
     * this thread, and the client must be listening before the core starts.
     */
    @Synchronized
    fun ensureStarted(context: Context, profile: ProfileItem) {
        if (!profile.isDnsttEnabled()) {
            stop()
            return
        }
        val settings = DnsttSettings(
            ns = profile.dnsttNS!!.trim(),
            pubkey = profile.dnsttPubkey!!.trim(),
            resolver = profile.dnsttResolver?.trim().takeUnless { it.isNullOrBlank() }
                ?: AppConfig.DNSTT_DEFAULT_RESOLVER
        )
        if (runningSettings == settings && isAlive(process)) {
            return
        }
        stop()
        start(context, settings)
    }

    /** Stops the client if it is running. Safe to call when it is not. */
    @Synchronized
    fun stop() {
        runningSettings = null
        val proc = process
        process = null
        try {
            proc?.destroy()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "DNSTT: failed to stop client", e)
        }
    }

    private fun start(context: Context, settings: DnsttSettings) {
        val binary = try {
            installBinary(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "DNSTT: failed to install client binary", e)
            return
        }
        // Do not log the command: it carries the server public key.
        val cmd = listOf(
            binary.absolutePath,
            "--ns", settings.ns,
            "--pubkey", settings.pubkey,
            "--resolver", settings.resolver,
            "--listen", "${AppConfig.DNSTT_LISTEN_HOST}:${AppConfig.DNSTT_LISTEN_PORT}",
            "--kcp-window", AppConfig.DNSTT_DEFAULT_KCP_WINDOW.toString()
        )
        try {
            process = ProcessBuilder(cmd)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .start()
            runningSettings = settings
            LogUtil.i(AppConfig.TAG, "DNSTT: client started")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "DNSTT: failed to start client", e)
            process = null
            runningSettings = null
        }
    }

    /**
     * Copies the bundled binary from assets to the app files dir and makes it
     * executable (assets lose the exec bit). Always overwrites, so an app
     * update carrying a newer binary is picked up. Returns the installed file.
     */
    private fun installBinary(context: Context): File {
        val dest = File(context.filesDir, AppConfig.DNSTT_BIN_NAME)
        context.assets.open(AppConfig.DNSTT_ASSET_NAME).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest.setExecutable(true, true)
        return dest
    }

    /** Process.isAlive() is API 26; exitValue() polling covers API 24-25 too. */
    private fun isAlive(process: Process?): Boolean {
        if (process == null) return false
        return try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }
}
