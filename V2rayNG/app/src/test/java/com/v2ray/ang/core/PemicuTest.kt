package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.PemicuManager.PemicuTarget
import com.v2ray.ang.core.PemicuManager.PrimeResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pemicu (built-in primer, port TB v1.0.84 per-entry primer):
 * per-entry prime domain + interval; an empty domain means the
 * entry is not primed (no fallback to the entry's address).
 */
class PemicuTest {

    private fun pemicuProfile(
        domain: String? = "support.zoom.us",
        enabled: Boolean? = true,
        interval: Int? = 15,
        port: Int? = null,
        server: String? = "leaseweb4.svrkencang.web.id",
        serverPort: String? = null,
    ) = ProfileItem(
        configType = EConfigType.VMESS,
        server = server,
        serverPort = serverPort,
        pemicuEnabled = enabled,
        pemicuDomain = domain,
        pemicuInterval = interval,
        pemicuPort = port,
    )

    @Test
    fun clampIntervalDefaultsAndClamps() {
        assertEquals(15, PemicuManager.clampInterval(null))
        assertEquals(15, PemicuManager.clampInterval(15))
        assertEquals(5, PemicuManager.clampInterval(0))
        assertEquals(5, PemicuManager.clampInterval(4))
        assertEquals(5, PemicuManager.clampInterval(-10))
        assertEquals(300, PemicuManager.clampInterval(301))
        assertEquals(300, PemicuManager.clampInterval(10_000))
        assertEquals(60, PemicuManager.clampInterval(60))
    }

    @Test
    fun clampPortDefaultsAndClamps() {
        assertEquals(443, PemicuManager.clampPort(null))
        assertEquals(443, PemicuManager.clampPort(443))
        assertEquals(8443, PemicuManager.clampPort(8443))
        assertEquals(1, PemicuManager.clampPort(1))
        assertEquals(65535, PemicuManager.clampPort(65535))
        assertEquals(443, PemicuManager.clampPort(0))
        assertEquals(443, PemicuManager.clampPort(-5))
        assertEquals(443, PemicuManager.clampPort(65536))
        assertEquals(443, PemicuManager.clampPort(99999))
    }

    @Test
    fun targetFromProfileUsesPortWhenValid() {
        val target = PemicuManager.targetFromProfile(pemicuProfile(port = 8443))!!
        assertEquals("support.zoom.us", target.host)
        assertEquals(8443, target.port)
    }

    @Test
    fun targetFromProfilePortFallsBackTo443() {
        // Empty (null), zero, negative, and out-of-range ports all
        // fall back to 443 — the prime port never comes from the
        // entry, and never breaks the dial.
        for (port in listOf<Int?>(null, 0, -5, 65536, 99999)) {
            val target = PemicuManager.targetFromProfile(pemicuProfile(port = port))!!
            assertEquals(AppConfig.PEMICU_DEFAULT_PORT, target.port)
        }
    }

    @Test
    fun targetFromProfileUsesDomainAndClampedInterval() {
        val target = PemicuManager.targetFromProfile(pemicuProfile())!!
        assertEquals("support.zoom.us", target.host)
        assertEquals(AppConfig.PEMICU_DEFAULT_PORT, target.port)
        assertEquals(15, target.intervalSec)
    }

    @Test
    fun targetFromProfileTrimsDomainAndClampsInterval() {
        val target = PemicuManager.targetFromProfile(
            pemicuProfile(domain = "  chat.sociomile.com  ", interval = 999)
        )!!
        assertEquals("chat.sociomile.com", target.host)
        assertEquals(300, target.intervalSec)
    }

    @Test
    fun targetFromProfileReturnsNullWhenDisabledOrDomainBlank() {
        assertNull(PemicuManager.targetFromProfile(pemicuProfile(enabled = false)))
        assertNull(PemicuManager.targetFromProfile(pemicuProfile(enabled = null)))
        assertNull(PemicuManager.targetFromProfile(pemicuProfile(domain = null)))
        assertNull(PemicuManager.targetFromProfile(pemicuProfile(domain = "")))
        assertNull(PemicuManager.targetFromProfile(pemicuProfile(domain = "   ")))
    }

    @Test
    fun targetFromProfileNeverReadsEntryAddress() {
        // The prime host comes only from the prime domain, even
        // when the entry's address is a different domain.
        val target = PemicuManager.targetFromProfile(
            pemicuProfile(domain = "live.iflix.com", server = "leaseweb4.svrkencang.web.id")
        )!!
        assertEquals("live.iflix.com", target.host)
        assertFalse(target.host == "leaseweb4.svrkencang.web.id")
    }

    @Test
    fun targetFromProfileNeverReadsEntryPort() {
        // The prime port comes only from the prime port field, even
        // when the entry's port is different — both ways: an entry
        // on 8443 with no prime port still primes on 443, and a
        // prime port of 8443 is used even when the entry is on 443.
        val defaultPort = PemicuManager.targetFromProfile(
            pemicuProfile(serverPort = "8443")
        )!!
        assertEquals(AppConfig.PEMICU_DEFAULT_PORT, defaultPort.port)
        val customPort = PemicuManager.targetFromProfile(
            pemicuProfile(port = 8443, serverPort = "443")
        )!!
        assertEquals(8443, customPort.port)
    }

    @Test
    fun buildTargetsDedupsByHostPortFirstWins() {
        val targets = PemicuManager.buildTargets(
            listOf(
                pemicuProfile(domain = "support.zoom.us", interval = 15),
                pemicuProfile(domain = "SUPPORT.ZOOM.US", interval = 60),
                pemicuProfile(domain = "chat.sociomile.com", interval = 30),
                pemicuProfile(domain = "", interval = 15),
                pemicuProfile(domain = "live.iflix.com", enabled = false),
            )
        )
        assertEquals(2, targets.size)
        assertEquals("support.zoom.us", targets[0].host)
        assertEquals(15, targets[0].intervalSec)
        assertEquals("chat.sociomile.com", targets[1].host)
        assertEquals(30, targets[1].intervalSec)
    }

    @Test
    fun dueTargetsRespectsInterval() {
        val targets = listOf(
            PemicuTarget("a.example.com", 443, 15),
            PemicuTarget("b.example.com", 443, 60),
        )
        val lastPrime = mapOf("a.example.com:443" to 1_000L)
        // a: 16 s elapsed (>= 15 s) -> due; b: 17 s elapsed (< 60 s) -> not due.
        val dueA = PemicuManager.dueTargets(targets, lastPrime, 17_000L)
        assertEquals(1, dueA.size)
        assertEquals("a.example.com", dueA[0].host)
        // a: 10 s elapsed (< 15 s) -> not due; b: 11 s (< 60 s) -> not due.
        val dueNone = PemicuManager.dueTargets(targets, lastPrime, 11_000L)
        assertTrue(dueNone.isEmpty())
        // a: 69 s elapsed -> due; b: 70 s elapsed (>= 60 s) -> due.
        val dueBoth = PemicuManager.dueTargets(targets, lastPrime, 70_000L)
        assertEquals(2, dueBoth.size)
    }

    @Test
    fun formatPrimeResultMatchesPemicuLogShape() {
        val line = PemicuManager.formatPrimeResult(
            PrimeResult.Ok(
                target = PemicuTarget("live.iflix.com", 443, 15),
                ip = "43.156.222.16",
                isV4 = true,
                tcpMs = 249,
                tlsProtocol = "TLSv1.2",
                totalMs = 460,
                bypassOk = true,
                localAddr = "10.203.57.10",
            )
        )
        assertEquals(
            "PRIME OK: live.iflix.com:443 [43.156.222.16 v4] TCP 249ms + " +
                "TLS OK (TLSv1.2) total 460ms [bypass OK] [routing OK: 10.203.57.10]",
            line
        )
    }

    @Test
    fun formatPrimeResultFailure() {
        val line = PemicuManager.formatPrimeResult(
            PrimeResult.Failed(PemicuTarget("live.iflix.com", 443, 15), "connect timed out")
        )
        assertEquals("PRIME GAGAL: live.iflix.com:443 (connect timed out)", line)
    }

    @Test
    fun pemicuDisabledByDefault() {
        val profile = ProfileItem(configType = EConfigType.VMESS)
        assertNull(profile.pemicuEnabled)
        assertNull(profile.pemicuDomain)
        assertNull(profile.pemicuInterval)
        assertNull(profile.pemicuPort)
        assertFalse(profile.pemicuEnabled == true)
        assertNull(PemicuManager.targetFromProfile(profile))
    }
}
