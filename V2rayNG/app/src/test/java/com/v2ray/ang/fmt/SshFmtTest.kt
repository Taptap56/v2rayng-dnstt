package com.v2ray.ang.fmt

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Share/import format for SSH servers, including DNSTT (SlowDNS).
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
            "#ssh%20dns"

        val parsed = SshFmt.parse(link)

        assertDnsttRestored(parsed)
        assertEquals("ssh dns", parsed?.remarks)
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

    private fun sshWsTlsProfile() = ProfileItem(
        configType = EConfigType.SSH,
        remarks = "ssh ws-tls",
        server = "example.com",
        serverPort = "443",
        username = "alice",
        password = "secret",
        sshMethod = "ws-tls",
        sshWsPath = "/ssh",
        sshWsHost = "ws.example.com",
        sshSni = "sni.example.com",
        sshSniVersion = "tls1.2",
        sshInsecure = true,
    )

    @Test
    fun roundTrip_sshWsTlsMethod() {
        val uri = "ssh://" + SshFmt.toUri(sshWsTlsProfile())

        val parsed = SshFmt.parse(uri)

        assertEquals(EConfigType.SSH, parsed?.configType)
        assertEquals("example.com", parsed?.server)
        assertEquals("443", parsed?.serverPort)
        assertEquals("ws-tls", parsed?.sshMethod)
        assertEquals("/ssh", parsed?.sshWsPath)
        assertEquals("ws.example.com", parsed?.sshWsHost)
        assertEquals("sni.example.com", parsed?.sshSni)
        assertEquals("tls1.2", parsed?.sshSniVersion)
        assertEquals(true, parsed?.sshInsecure)
    }

    @Test
    fun roundTrip_sshPayloadMethod() {
        val profile = ProfileItem(
            configType = EConfigType.SSH,
            remarks = "ssh payload",
            server = "example.com",
            serverPort = "3128",
            username = "alice",
            password = "secret",
            sshMethod = "payload",
            sshPayload = "GET / HTTP/1.1\nHost: [host]\n\n",
        )
        val uri = "ssh://" + SshFmt.toUri(profile)

        val parsed = SshFmt.parse(uri)

        assertEquals("payload", parsed?.sshMethod)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", parsed?.sshPayload)
    }

    @Test
    fun roundTrip_sshTlsMethod() {
        val profile = ProfileItem(
            configType = EConfigType.SSH,
            remarks = "ssh tls",
            server = "example.com",
            serverPort = "443",
            username = "alice",
            password = "secret",
            sshMethod = "tls",
            sshSni = "sni.example.com",
            sshSniVersion = "tls1.3",
            sshInsecure = true,
        )
        val uri = "ssh://" + SshFmt.toUri(profile)

        val parsed = SshFmt.parse(uri)

        assertEquals("tls", parsed?.sshMethod)
        assertEquals("sni.example.com", parsed?.sshSni)
        assertEquals("tls1.3", parsed?.sshSniVersion)
        assertEquals(true, parsed?.sshInsecure)
        assertNull(parsed?.sshWsPath)
    }

    @Test
    fun importFromQrOrFile_sshMethodLinkRestoresMethod() {
        // A literal link as it would arrive from a QR scan or a file.
        val link = "ssh://alice:secret@example.com:443" +
            "?method=ws-tls" +
            "&ws_path=%2Fssh" +
            "&ws_host=ws.example.com" +
            "&sni=sni.example.com" +
            "&sni_version=tls1.2" +
            "&insecure=1" +
            "#ssh%20ws-tls"

        val parsed = SshFmt.parse(link)

        assertEquals("ws-tls", parsed?.sshMethod)
        assertEquals("/ssh", parsed?.sshWsPath)
        assertEquals("ws.example.com", parsed?.sshWsHost)
        assertEquals("sni.example.com", parsed?.sshSni)
        assertEquals("tls1.2", parsed?.sshSniVersion)
        assertEquals(true, parsed?.sshInsecure)
        assertEquals("ssh ws-tls", parsed?.remarks)
    }

    @Test
    fun toUri_directSshOmitsMethodParams() {
        val profile = sshDnsttProfile().apply {
            dnsttNS = null
            dnsttPubkey = null
            dnsttResolver = null
            dnsttRecordType = null
            // A stale SNI version must not leak into a direct link.
            sshSniVersion = "default"
            sshInsecure = true
        }

        val uri = SshFmt.toUri(profile)

        assertFalse(uri.contains("method="), "direct SSH export must not carry method params: $uri")
        assertFalse(uri.contains("ws_path="), uri)
        assertFalse(uri.contains("payload="), uri)
        assertFalse(uri.contains("sni_version="), uri)
        assertFalse(uri.contains("insecure="), uri)
    }

    @Test
    fun parse_plainSshLinkHasNoMethod() {
        val parsed = SshFmt.parse("ssh://alice:secret@example.com:22#Plain")

        assertNull(parsed?.sshMethod)
        assertNull(parsed?.sshPayload)
        assertNull(parsed?.sshWsPath)
        assertNull(parsed?.sshWsHost)
        assertNull(parsed?.sshSni)
        assertNull(parsed?.sshSniVersion)
        assertNull(parsed?.sshInsecure)
    }
}
