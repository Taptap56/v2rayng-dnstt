package com.v2ray.ang.fmt

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Share/import format for SSH servers, including DNSTT (SlowDNS)
 * and the HC-style transport layers.
 *
 * The same parsers serve every import entry point — clipboard, QR
 * code and file all funnel into AngConfigManager.parseConfig via
 * importBatchConfig (MainActivity.importClipboard / importQRcode /
 * importConfigLocal -> MainAction.ImportBatchConfig), so a link that
 * parses here imports identically from all three.
 */
class SshFmtTest {

    private fun sshDnsttProfile() = ProfileItem(
        configType = EConfigType.SSH,
        remarks = "ssh dns",
        server = "example.com",
        serverPort = "3128",
        username = "alice",
        password = "secret",
        dnsttNS = "ns.example.com",
        dnsttPubkey = "a".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "null",
        sshUseSlowdns = true,
    )

    private fun assertDnsttRestored(config: ProfileItem?) {
        assertEquals(EConfigType.SSH, config?.configType)
        assertEquals("example.com", config?.server)
        assertEquals("3128", config?.serverPort)
        assertEquals("alice", config?.username)
        assertEquals("secret", config?.password)
        assertEquals("ns.example.com", config?.dnsttNS)
        assertEquals("a".repeat(64), config?.dnsttPubkey)
        assertEquals("1.1.1.1:53", config?.dnsttResolver)
        assertEquals("null", config?.dnsttRecordType)
        assertTrue(config?.isSshSlowDns() == true)
    }

    @Test
    fun roundTrip_sshDnsttNullPlusResolver() {
        val uri = "ssh://" + SshFmt.toUri(sshDnsttProfile())

        val parsed = SshFmt.parse(uri)

        assertDnsttRestored(parsed)
        assertEquals("ssh dns", parsed?.remarks)
        assertEquals(true, parsed?.sshUseSlowdns)
    }

    @Test
    fun importFromQrOrFile_sshDnsttLinkRestoresDnsttSettings() {
        // A literal link as it would arrive from a QR scan or a file:
        // it must parse without going through the exporter first.
        val link = "ssh://alice:secret@example.com:3128" +
            "?dnstt_ns=ns.example.com" +
            "&dnstt_pubkey=" + "a".repeat(64) +
            "&dnstt_resolver=1.1.1.1%3A53" +
            "&dnstt_record_type=null" +
            "&methods=slowdns" +
            "#ssh%20dns"

        val parsed = SshFmt.parse(link)

        assertDnsttRestored(parsed)
        assertEquals("ssh dns", parsed?.remarks)
        assertEquals(true, parsed?.sshUseSlowdns)
    }

    @Test
    fun roundTrip_sshDnsttWithPrivateKey() {
        val profile = sshDnsttProfile().apply {
            password = null
            sshPrivateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----"
        }
        val uri = "ssh://" + SshFmt.toUri(profile)

        val parsed = SshFmt.parse(uri)

        assertNull(parsed?.password)
        assertEquals(profile.sshPrivateKey, parsed?.sshPrivateKey)
        assertEquals("ns.example.com", parsed?.dnsttNS)
        assertTrue(parsed?.isSshSlowDns() == true)
    }

    @Test
    fun roundTrip_passwordWithColonSurvives() {
        val profile = sshDnsttProfile().apply { password = "p:a:ss" }
        val uri = "ssh://" + SshFmt.toUri(profile)

        val parsed = SshFmt.parse(uri)

        assertEquals("alice", parsed?.username)
        assertEquals("p:a:ss", parsed?.password)
        assertTrue(parsed?.isSshSlowDns() == true)
    }

    @Test
    fun toUri_plainSshOmitsDnsttParams() {
        val profile = sshDnsttProfile().apply {
            dnsttNS = null
            dnsttPubkey = null
            dnsttResolver = null
            dnsttRecordType = null
            sshUseSlowdns = false
        }

        val uri = SshFmt.toUri(profile)

        assertFalse(uri.contains("dnstt_"), "plain SSH export must not carry DNSTT params: $uri")
        assertTrue(uri.startsWith("alice:secret@example.com:3128"))
    }

    @Test
    fun parse_plainSshLinkBackwardCompatible() {
        val parsed = SshFmt.parse("ssh://alice:secret@example.com:22#Plain")

        assertEquals(EConfigType.SSH, parsed?.configType)
        assertEquals("alice", parsed?.username)
        assertEquals("secret", parsed?.password)
        assertEquals("example.com", parsed?.server)
        assertEquals("22", parsed?.serverPort)
        assertEquals("Plain", parsed?.remarks)
        assertNull(parsed?.dnsttNS)
        assertNull(parsed?.dnsttPubkey)
        assertNull(parsed?.dnsttResolver)
        assertNull(parsed?.dnsttRecordType)
        assertFalse(parsed?.isSshSlowDns() == true)
    }

    @Test
    fun parse_plainSshLinkWithoutPortDefaultsTo22() {
        val parsed = SshFmt.parse("ssh://alice@example.com#NoPort")

        assertEquals("22", parsed?.serverPort)
        assertEquals("alice", parsed?.username)
        assertNull(parsed?.password)
    }

    @Test
    fun parse_bareSshSchemeReturnsNull() {
        assertNull(SshFmt.parse("ssh://"))
    }

    private fun sshLayeredProfile() = ProfileItem(
        configType = EConfigType.SSH,
        remarks = "ssh layers",
        server = "example.com",
        serverPort = "443",
        username = "alice",
        password = "secret",
        sshUsePayload = true,
        sshUseEnhanced = true,
        sshUseTls = true,
        sshUseSlowdns = true,
        sshRemoteProxy = "user:pass@proxy.example.com:8080",
        sshPayload = "GET / HTTP/1.1\nHost: [host]\n\n",
        sshSni = "sni.example.com",
        sshSniVersion = "tls1.2",
        sshInsecure = true,
        dnsttNS = "ns.example.com",
        dnsttPubkey = "a".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "null",
    )

    @Test
    fun roundTrip_sshLayers() {
        val uri = "ssh://" + SshFmt.toUri(sshLayeredProfile())

        val parsed = SshFmt.parse(uri)

        assertEquals(EConfigType.SSH, parsed?.configType)
        assertEquals("example.com", parsed?.server)
        assertEquals("443", parsed?.serverPort)
        assertEquals(true, parsed?.sshUsePayload)
        assertEquals(true, parsed?.sshUseEnhanced)
        assertEquals(true, parsed?.sshUseTls)
        assertEquals(true, parsed?.sshUseSlowdns)
        assertEquals("user:pass@proxy.example.com:8080", parsed?.sshRemoteProxy)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", parsed?.sshPayload)
        assertEquals("sni.example.com", parsed?.sshSni)
        assertEquals("tls1.2", parsed?.sshSniVersion)
        assertEquals(true, parsed?.sshInsecure)
        assertTrue(parsed?.isSshSlowDns() == true)
    }

    @Test
    fun roundTrip_sshPayloadLayer() {
        val profile = ProfileItem(
            configType = EConfigType.SSH,
            remarks = "ssh payload",
            server = "example.com",
            serverPort = "3128",
            username = "alice",
            password = "secret",
            sshUsePayload = true,
            sshPayload = "GET / HTTP/1.1\nHost: [host]\n\n",
        )
        val uri = "ssh://" + SshFmt.toUri(profile)

        val parsed = SshFmt.parse(uri)

        assertEquals(true, parsed?.sshUsePayload)
        assertEquals(false, parsed?.sshUseEnhanced)
        assertEquals(false, parsed?.sshUseTls)
        assertEquals(false, parsed?.sshUseSlowdns)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", parsed?.sshPayload)
    }

    @Test
    fun roundTrip_sshTlsLayer() {
        val profile = ProfileItem(
            configType = EConfigType.SSH,
            remarks = "ssh tls",
            server = "example.com",
            serverPort = "443",
            username = "alice",
            password = "secret",
            sshUseTls = true,
            sshSni = "sni.example.com",
            sshSniVersion = "tls1.3",
            sshInsecure = true,
        )
        val uri = "ssh://" + SshFmt.toUri(profile)

        val parsed = SshFmt.parse(uri)

        assertEquals(true, parsed?.sshUseTls)
        assertEquals("sni.example.com", parsed?.sshSni)
        assertEquals("tls1.3", parsed?.sshSniVersion)
        assertEquals(true, parsed?.sshInsecure)
        assertNull(parsed?.sshRemoteProxy)
    }

    @Test
    fun importFromQrOrFile_sshMethodsLinkRestoresLayers() {
        // A literal link as it would arrive from a QR scan or a file.
        val link = "ssh://alice:secret@example.com:443" +
            "?methods=payload%2Ctls" +
            "&remote_proxy=user%3Apass%40proxy.example.com%3A8080" +
            "&payload=GET%20%2F%20HTTP%2F1.1%0AHost%3A%20%5Bhost%5D%0A%0A" +
            "&sni=sni.example.com" +
            "&sni_version=tls1.2" +
            "&insecure=1" +
            "#ssh%20layers"

        val parsed = SshFmt.parse(link)

        assertEquals(true, parsed?.sshUsePayload)
        assertEquals(false, parsed?.sshUseEnhanced)
        assertEquals(true, parsed?.sshUseTls)
        assertEquals(false, parsed?.sshUseSlowdns)
        assertEquals("user:pass@proxy.example.com:8080", parsed?.sshRemoteProxy)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", parsed?.sshPayload)
        assertEquals("sni.example.com", parsed?.sshSni)
        assertEquals("tls1.2", parsed?.sshSniVersion)
        assertEquals(true, parsed?.sshInsecure)
        assertEquals("ssh layers", parsed?.remarks)
    }

    @Test
    fun importLegacy_sshMethodPayloadMapsToLayer() {
        val link = "ssh://alice:secret@example.com:3128" +
            "?method=payload" +
            "&payload=GET%20%2F%20HTTP%2F1.1%0AHost%3A%20%5Bhost%5D%0A%0A" +
            "#ssh%20payload"

        val parsed = SshFmt.parse(link)

        assertEquals(true, parsed?.sshUsePayload)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", parsed?.sshPayload)
    }

    @Test
    fun importLegacy_sshMethodWsTlsMapsToDirect() {
        // A link from the previous release with method=ws-tls: the
        // WS dial is gone, so it imports as a direct SSH server.
        // The SNI parameters are still parsed (they are valid for
        // the TLS layer the user can turn on).
        val link = "ssh://alice:secret@example.com:443" +
            "?method=ws-tls" +
            "&ws_path=%2Fssh" +
            "&ws_host=ws.example.com" +
            "&sni=sni.example.com" +
            "&sni_version=tls1.2" +
            "&insecure=1" +
            "#ssh%20ws-tls"

        val parsed = SshFmt.parse(link)

        // No layer is set for a legacy WS link (the flags stay null);
        // the effective layers are all off (direct).
        assertEquals(false, parsed?.sshEffectiveUsePayload())
        assertEquals(false, parsed?.sshEffectiveUseEnhanced())
        assertEquals(false, parsed?.sshEffectiveUseTls())
        assertEquals(false, parsed?.sshEffectiveUseSlowdns())
        assertEquals("sni.example.com", parsed?.sshSni)
        assertEquals("tls1.2", parsed?.sshSniVersion)
        assertEquals(true, parsed?.sshInsecure)
        assertEquals("ssh ws-tls", parsed?.remarks)
    }

    @Test
    fun importLegacy_sshMethodSlowdnsMapsToLayer() {
        val link = "ssh://alice:secret@example.com:3128" +
            "?method=slowdns" +
            "&dnstt_ns=ns.example.com" +
            "&dnstt_pubkey=" + "a".repeat(64) +
            "#ssh%20slowdns"

        val parsed = SshFmt.parse(link)

        assertEquals(true, parsed?.sshUseSlowdns)
        assertTrue(parsed?.isSshSlowDns() == true)
    }

    @Test
    fun parse_methodsWinsOverLegacyMethod() {
        val link = "ssh://alice:secret@example.com:443" +
            "?methods=tls" +
            "&method=payload" +
            "#both"

        val parsed = SshFmt.parse(link)

        assertEquals(false, parsed?.sshUsePayload)
        assertEquals(true, parsed?.sshUseTls)
    }

    @Test
    fun toUri_directSshOmitsLayerParams() {
        val profile = sshDnsttProfile().apply {
            dnsttNS = null
            dnsttPubkey = null
            dnsttResolver = null
            dnsttRecordType = null
            sshUseSlowdns = false
            // A stale SNI version must not leak into a direct link.
            sshSniVersion = "default"
            sshInsecure = true
        }

        val uri = SshFmt.toUri(profile)

        assertFalse(uri.contains("methods="), "direct SSH export must not carry layer params: $uri")
        assertFalse(uri.contains("method="), uri)
        assertFalse(uri.contains("remote_proxy="), uri)
        assertFalse(uri.contains("payload="), uri)
        assertFalse(uri.contains("sni_version="), uri)
        assertFalse(uri.contains("insecure="), uri)
    }

    @Test
    fun parse_plainSshLinkHasNoLayers() {
        val parsed = SshFmt.parse("ssh://alice:secret@example.com:22#Plain")

        // A plain link sets no layer flags (they stay null); the
        // effective layers are all off (direct).
        assertEquals(false, parsed?.sshEffectiveUsePayload())
        assertEquals(false, parsed?.sshEffectiveUseEnhanced())
        assertEquals(false, parsed?.sshEffectiveUseTls())
        assertEquals(false, parsed?.sshEffectiveUseSlowdns())
        assertNull(parsed?.sshRemoteProxy)
        assertNull(parsed?.sshPayload)
        assertNull(parsed?.sshSni)
        assertNull(parsed?.sshSniVersion)
        assertNull(parsed?.sshInsecure)
    }
}
