package com.v2ray.ang.fmt

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * VLESS share/import, including the DNSTT (SlowDNS) fields carried
 * as query parameters.
 *
 * The same parsers serve every import entry point — clipboard, QR
 * code and file all funnel into AngConfigManager.parseConfig via
 * importBatchConfig (MainActivity.importClipboard / importQRcode /
 * importConfigLocal -> MainAction.ImportBatchConfig), so a link that
 * parses here imports identically from all three.
 */
class VlessFmtTest {

    private fun vlessDnsttProfile() = ProfileItem(
        configType = EConfigType.VLESS,
        remarks = "vless dns",
        server = "example.com",
        serverPort = "443",
        password = "11111111-2222-4333-8444-555555555555",
        method = "none",
        network = "ws",
        host = "example.com",
        path = "/vless",
        security = "tls",
        sni = "example.com",
        dnsttEnabled = true,
        dnsttNS = "ns.example.com",
        dnsttPubkey = "a".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "txt",
    )

    private fun assertDnsttRestored(config: ProfileItem?) {
        assertEquals(EConfigType.VLESS, config?.configType)
        assertEquals("example.com", config?.server)
        assertEquals("443", config?.serverPort)
        assertEquals("11111111-2222-4333-8444-555555555555", config?.password)
        assertEquals("ws", config?.network)
        assertEquals("tls", config?.security)
        assertEquals(true, config?.dnsttEnabled)
        assertEquals("ns.example.com", config?.dnsttNS)
        assertEquals("a".repeat(64), config?.dnsttPubkey)
        assertEquals("1.1.1.1:53", config?.dnsttResolver)
        assertEquals("txt", config?.dnsttRecordType)
        assertTrue(config?.isDnsttEnabled() == true)
    }

    @Test
    fun roundTrip_vlessDnsttLink() {
        val uri = "vless://" + VlessFmt.toUri(vlessDnsttProfile())

        val parsed = VlessFmt.parse(uri)

        assertDnsttRestored(parsed)
        assertEquals("vless dns", parsed?.remarks)
    }

    @Test
    fun importFromQrOrFile_vlessDnsttLinkRestoresDnsttSettings() {
        // A literal link as it would arrive from a QR scan or a file.
        val link = "vless://11111111-2222-4333-8444-555555555555@example.com:443" +
            "?encryption=none&security=tls&type=ws&host=example.com&path=%2Fvless&sni=example.com" +
            "&dnstt_ns=ns.example.com" +
            "&dnstt_pubkey=" + "a".repeat(64) +
            "&dnstt_resolver=1.1.1.1%3A53" +
            "&dnstt_record_type=txt" +
            "#vless%20dns"

        val parsed = VlessFmt.parse(link)

        assertDnsttRestored(parsed)
        assertEquals("vless dns", parsed?.remarks)
    }

    @Test
    fun toUri_plainVlessExportsWithoutDnsttParams() {
        val profile = vlessDnsttProfile().apply {
            dnsttEnabled = false
            dnsttNS = null
            dnsttPubkey = null
            dnsttResolver = null
            dnsttRecordType = null
        }

        val uri = VlessFmt.toUri(profile)

        assertFalse(uri.contains("dnstt_"), "plain VLESS export must not carry DNSTT params: $uri")
    }

    @Test
    fun parse_plainVlessLinkBackwardCompatible() {
        val plain = vlessDnsttProfile().apply {
            dnsttEnabled = false
            dnsttNS = null
            dnsttPubkey = null
            dnsttResolver = null
            dnsttRecordType = null
        }
        val uri = "vless://" + VlessFmt.toUri(plain)

        val parsed = VlessFmt.parse(uri)

        assertEquals(EConfigType.VLESS, parsed?.configType)
        assertEquals("example.com", parsed?.server)
        assertEquals("443", parsed?.serverPort)
        assertEquals("ws", parsed?.network)
        assertEquals("tls", parsed?.security)
        assertNull(parsed?.dnsttEnabled)
        assertNull(parsed?.dnsttNS)
        assertNull(parsed?.dnsttPubkey)
        assertNull(parsed?.dnsttResolver)
        assertNull(parsed?.dnsttRecordType)
        assertFalse(parsed?.isDnsttEnabled() == true)
    }
}
