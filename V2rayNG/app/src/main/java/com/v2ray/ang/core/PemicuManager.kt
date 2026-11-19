package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Pemicu (built-in primer, port TB v1.0.84 per-entry primer):
 * v2rayNG itself dials the prime domains of the profiles that have
 * the Pemicu chip on, to keep the operator window open — no root,
 * no separate primer APK.
 *
 * The dial is a plain in-app TLS handshake (Java SSLSocket, the
 * same dial the pemicu.apk / Hook use), done on a socket the
 * VpnService protects (VpnService.protect(), so the dial bypasses
 * the tunnel and goes direct). The app package is additionally
 * disallowed from the VPN (CoreVpnService adds it), so the dial
 * bypasses even if protect() returns false. The dial never goes
 * through the xray core and never touches a VMess config or the
 * proxy path: it only reads the profile's own Pemicu fields
 * (chip, interval, domain). The SNI of the dial is the prime
 * domain itself, never the entry's address — an empty prime
 * domain means the entry is not primed (no fallback).
 *
 * The loop is a 1 s tick owned by CoreServiceManager (started in
 * launchCore, stopped in stopCoreLoop). It rebuilds the target
 * list from all profiles on every tick, so editing a profile's
 * Pemicu fields takes effect without restarting the VPN. A host
 * is dialled when its interval has elapsed since its last prime;
 * a host already being dialled is skipped (no concurrent dials
 * to the same host). A dial (ok or failed) waits the full
 * interval before it is retried (last-prime is recorded at
 * launch).
 */
object PemicuManager {

    private const val TAG = "Pemicu"

    /** TCP connect timeout for a prime dial. */
    private const val TCP_TIMEOUT_MS = 5_000

    /** TLS handshake read timeout for a prime dial (startHandshake
     * must never run without a timeout — pemicu.apk v1.0.0 could
     * hang on a silent server). */
    private const val TLS_TIMEOUT_MS = 10_000

    /** Tick interval of the prime loop. */
    private const val TICK_MS = 1_000L

    /** One prime host + its interval (seconds). */
    data class PemicuTarget(val host: String, val port: Int, val intervalSec: Int) {
        fun key(): String = "${host.lowercase()}:$port"
    }

    /** Result of one prime dial. */
    sealed class PrimeResult {
        data class Ok(
            val target: PemicuTarget,
            val ip: String,
            val isV4: Boolean,
            val tcpMs: Long,
            val tlsProtocol: String,
            val totalMs: Long,
            val bypassOk: Boolean,
            val localAddr: String,
        ) : PrimeResult()

        data class Failed(val target: PemicuTarget, val error: String) : PrimeResult()
    }

    // --- Pure helpers (no Android dependency; unit-tested) ---

    /** Clamp a prime interval to 5..300 s (default 15 when unset). */
    fun clampInterval(v: Int?): Int = when {
        v == null -> AppConfig.PEMICU_DEFAULT_INTERVAL
        v < AppConfig.PEMICU_MIN_INTERVAL -> AppConfig.PEMICU_MIN_INTERVAL
        v > AppConfig.PEMICU_MAX_INTERVAL -> AppConfig.PEMICU_MAX_INTERVAL
        else -> v
    }

    /** The prime target of one profile, or null when the profile
     * must not be primed (chip off, or the prime domain blank).
     * The entry's address/port are never read for priming. */
    fun targetFromProfile(profile: ProfileItem): PemicuTarget? {
        if (profile.pemicuEnabled != true) return null
        val host = profile.pemicuDomain?.trim().orEmpty()
        if (host.isEmpty()) return null
        return PemicuTarget(
            host = host,
            port = AppConfig.PEMICU_DEFAULT_PORT,
            intervalSec = clampInterval(profile.pemicuInterval),
        )
    }

    /** The merged prime list: every enabled profile's target,
     * deduplicated by host:port (first profile wins). */
    fun buildTargets(profiles: List<ProfileItem>): List<PemicuTarget> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<PemicuTarget>()
        for (profile in profiles) {
            val target = targetFromProfile(profile) ?: continue
            if (!seen.add(target.key())) continue
            out.add(target)
        }
        return out
    }

    /** The targets whose interval has elapsed since their last
     * prime (a host never primed is due at once). */
    fun dueTargets(
        targets: List<PemicuTarget>,
        lastPrime: Map<String, Long>,
        now: Long,
    ): List<PemicuTarget> = targets.filter { target ->
        now - (lastPrime[target.key()] ?: 0L) >= target.intervalSec * 1_000L
    }

    /** The log line for a prime result, same shape as the
     * pemicu.apk / Hook log so the two can be read side by side. */
    fun formatPrimeResult(result: PrimeResult): String = when (result) {
        is PrimeResult.Ok -> "PRIME OK: ${result.target.host}:${result.target.port} " +
            "[${result.ip} ${if (result.isV4) "v4" else "v6"}] " +
            "TCP ${result.tcpMs}ms + TLS OK (${result.tlsProtocol}) " +
            "total ${result.totalMs}ms " +
            "[bypass ${if (result.bypassOk) "OK" else "GAGAL"}] " +
            "[routing OK: ${result.localAddr}]"
        is PrimeResult.Failed -> "PRIME GAGAL: ${result.target.host}:${result.target.port} " +
            "(${result.error})"
    }

    // --- Loop lifecycle (owned by CoreServiceManager) ---

    private var primeJob: Job? = null
    private var primeScope: CoroutineScope? = null
    private val lastPrime = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Sockets of the dials currently in flight. Closing them
     * from stop() interrupts a blocking connect/handshake at once
     * (a coroutine cancel alone cannot interrupt blocking I/O). */
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    /** Start the prime loop in the given scope (a child of the
     * scope CoreServiceManager owns). Idempotent: a second call
     * while the loop runs is a no-op, so a core reload does not
     * spawn a duplicate loop. */
    fun start(scope: CoroutineScope, protect: (Socket) -> Boolean) {
        if (primeJob?.isActive == true) return
        primeScope = scope
        primeJob = scope.launch(Dispatchers.IO) {
            LogUtil.i(TAG, "Pemicu: prime loop started")
            try {
                primeLoop(protect)
            } finally {
                LogUtil.i(TAG, "Pemicu: prime loop stopped")
            }
        }
    }

    /** Stop the prime loop and its in-flight dials. Idempotent.
     * Closes the in-flight sockets first (interrupts blocking
     * dials), then cancels the loop job. Safe to call from a
     * non-suspend teardown path. */
    fun stop() {
        val job = primeJob
        primeJob = null
        primeScope = null
        for (socket in activeSockets) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
        activeSockets.clear()
        job?.cancel()
        inFlight.clear()
        lastPrime.clear()
    }

    /** Read every profile (all subscriptions) from MMKV. */
    private fun readAllProfiles(): List<ProfileItem> {
        val out = mutableListOf<ProfileItem>()
        for (guid in MmkvManager.decodeAllServerList()) {
            MmkvManager.decodeServerConfig(guid)?.let { out.add(it) }
        }
        return out
    }

    private suspend fun primeLoop(protect: (Socket) -> Boolean) {
        while (coroutineContext.isActive) {
            val targets = try {
                buildTargets(readAllProfiles())
            } catch (e: Exception) {
                LogUtil.w(TAG, "Pemicu: failed to read profiles", e)
                emptyList()
            }
            val now = System.currentTimeMillis()
            val scope = primeScope
            val due = dueTargets(targets, lastPrime, now)
                .filter { it.key() !in inFlight }
            for (target in due) {
                if (!inFlight.add(target.key())) continue
                // Record the prime at launch: a dial (ok or failed)
                // waits the full interval before it is retried.
                // Written here (loop only) so a dial finishing
                // after stop() cannot re-populate lastPrime.
                lastPrime[target.key()] = now
                val launched = scope?.launch(Dispatchers.IO) {
                    try {
                        val result = primeDial(target, protect)
                        LogUtil.i(TAG, formatPrimeResult(result))
                    } catch (e: Exception) {
                        LogUtil.w(TAG, formatPrimeResult(PrimeResult.Failed(target, e.message ?: e.javaClass.simpleName)))
                    } finally {
                        inFlight.remove(target.key())
                    }
                }
                if (launched == null) {
                    // Scope went away (stop raced the tick): do not
                    // leave the host stuck in-flight or marked primed.
                    inFlight.remove(target.key())
                    lastPrime.remove(target.key())
                }
            }
            delay(TICK_MS)
            coroutineContext.ensureActive()
        }
    }

    /**
     * One prime dial: plain in-app TLS handshake to the prime
     * domain (SNI = the prime domain, never the entry's address),
     * IPv4 first then IPv6. The socket is protected before it
     * connects so the dial bypasses the tunnel. Returns the first
     * address that completes a handshake, or the last error.
     */
    fun primeDial(target: PemicuTarget, protect: (Socket) -> Boolean): PrimeResult {
        val started = System.currentTimeMillis()
        val addresses = try {
            InetAddress.getAllByName(target.host)
                .sortedBy { if (it is Inet4Address) 0 else 1 }
        } catch (e: Exception) {
            return PrimeResult.Failed(target, "resolve: ${e.message ?: e.javaClass.simpleName}")
        }
        var lastError = "no address"
        for (address in addresses) {
            val isV4 = address is Inet4Address
            val socket = Socket()
            activeSockets.add(socket)
            try {
                // Protect before connecting: a connected socket is
                // already routed, so protect() would be too late.
                val bypassOk = try {
                    protect(socket)
                } catch (_: Exception) {
                    false
                }
                val tcpStart = System.currentTimeMillis()
                socket.connect(InetSocketAddress(address, target.port), TCP_TIMEOUT_MS)
                val tcpMs = System.currentTimeMillis() - tcpStart
                // getDefault() returns SocketFactory: cast the factory
                // so the createSocket(Socket, String, Int, Boolean)
                // overload resolves; cast the result to SSLSocket
                // for the TLS session (protocol) and handshake.
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = factory
                    .createSocket(socket, target.host, target.port, true) as SSLSocket
                sslSocket.soTimeout = TLS_TIMEOUT_MS
                sslSocket.startHandshake()
                val protocol = sslSocket.session.protocol ?: "TLS?"
                val localAddr = socket.localAddress?.hostAddress ?: "-"
                val totalMs = System.currentTimeMillis() - started
                val result = PrimeResult.Ok(
                    target = target,
                    ip = address.hostAddress ?: "-",
                    isV4 = isV4,
                    tcpMs = tcpMs,
                    tlsProtocol = protocol,
                    totalMs = totalMs,
                    bypassOk = bypassOk,
                    localAddr = localAddr,
                )
                try {
                    sslSocket.close()
                } catch (_: Exception) {
                }
                return result
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            } finally {
                activeSockets.remove(socket)
            }
        }
        return PrimeResult.Failed(target, lastError)
    }
}
