package com.v2ray.ang.fmt

import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.v2ray.ang.dto.VmessQRCode
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import java.util.Base64 as JavaBase64

/**
 * VMess share/import, including the DNSTT (SlowDNS) fields carried in
 * the legacy base64-JSON link and in the standard URI form.
 *
 * The same parsers serve every import entry point — clipboard, QR
 * code and file all funnel into AngConfigManager.parseConfig via
 * importBatchConfig (MainActivity.importClipboard / importQRcode /
 * importConfigLocal -> MainAction.ImportBatchConfig), so a link that
 * parses here imports identically from all three.
 */
class VmessFmtTest {

    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var mockLog: MockedStatic<Log>
    private lateinit var mockTextUtils: MockedStatic<TextUtils>

    @BeforeEach
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)

        mockBase64 = mockStatic(Base64::class.java)
        mockBase64.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as String
            val flags = invocation.arguments[1] as Int
            val isUrlSafe = (flags and Base64.URL_SAFE) != 0
            val decoder = if (isUrlSafe) JavaBase64.getUrlDecoder() else JavaBase64.getDecoder()
            decoder.decode(input)
        }
        mockBase64.`when`<String> {
            Base64.encodeToString(Mockito.any(ByteArray::class.java), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as ByteArray
            val flags = invocation.arguments[1] as Int
            val isUrlSafe = (flags and Base64.URL_SAFE) != 0
            val noPadding = (flags and Base64.NO_PADDING) != 0
            var encoder = if (isUrlSafe) JavaBase64.getUrlEncoder() else JavaBase64.getEncoder()
            if (noPadding) {
                encoder = encoder.withoutPadding()
            }
            encoder.encodeToString(input)
        }

        mockTextUtils = mockStatic(TextUtils::class.java)
        mockTextUtils.`when`<Boolean> {
            TextUtils.isEmpty(Mockito.anyString())
        }.thenAnswer { invocation ->
            (invocation.arguments[0] as? CharSequence).isNullOrEmpty()
        }
    }

    @AfterEach
    fun tearDown() {
        mockTextUtils.close()
        mockLog.close()
        mockBase64.close()
    }

    private fun vmessDnsttProfile() = ProfileItem(
        configType = EConfigType.VMESS,
        remarks = "vmess dns",
        server = "example.com",
        serverPort = "443",
        password = "11111111-2222-4333-8444-555555555555",
        method = "auto",
        network = "ws",
        headerType = "none",
        host = "example.com",
        path = "/vmess",
        security = "tls",
        sni = "example.com",
        dnsttEnabled = true,
        dnsttNS = "ns.example.com",
        dnsttPubkey = "a".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "null",
    )

    private fun assertDnsttRestored(config: ProfileItem?) {
        assertEquals(EConfigType.VMESS, config?.configType)
        assertEquals("example.com", config?.server)
        assertEquals("443", config?.serverPort)
        assertEquals("11111111-2222-4333-8444-555555555555", config?.password)
        assertEquals("ws", config?.network)
        assertEquals("tls", config?.security)
        assertEquals(true, config?.dnsttEnabled)
        assertEquals("ns.example.com", config?.dnsttNS)
        assertEquals("a".repeat(64), config?.dnsttPubkey)
        assertEquals("1.1.1.1:53", config?.dnsttResolver)
        assertEquals("null", config?.dnsttRecordType)
        assertTrue(config?.isDnsttEnabled() == true)
    }

    @Test
    fun roundTrip_vmessDnsttLegacyLink() {
        val uri = "vmess://" + VmessFmt.toUri(vmessDnsttProfile())

        val parsed = VmessFmt.parse(uri)

        assertDnsttRestored(parsed)
        assertEquals("vmess dns", parsed?.remarks)
    }

    @Test
    fun importFromQrOrFile_vmessDnsttLinkRestoresDnsttSettings() {
        // A literal link as it would arrive from a QR scan or a file,
        // built by hand (not via the exporter) so the format itself
        // is what the test proves.
        val qrCode = VmessQRCode(
            v = "2",
            ps = "vmess dns",
            add = "example.com",
            port = "443",
            id = "11111111-2222-4333-8444-555555555555",
            aid = "0",
            scy = "auto",
            net = "ws",
            type = "none",
            host = "example.com",
            path = "/vmess",
            tls = "tls",
            sni = "example.com",
            dnstt_ns = "ns.example.com",
            dnstt_pubkey = "a".repeat(64),
            dnstt_resolver = "1.1.1.1:53",
            dnstt_record_type = "null",
        )
        val link = "vmess://" + Utils.encode(JsonUtil.toJson(qrCode))

        val parsed = VmessFmt.parse(link)

        assertDnsttRestored(parsed)
        assertEquals("vmess dns", parsed?.remarks)
    }

    @Test
    fun importFromQrOrFile_vmessDnsttStdUriRestoresDnsttSettings() {
        val link = "vmess://11111111-2222-4333-8444-555555555555@example.com:443" +
            "?security=tls&type=ws&host=example.com&path=%2Fvmess&sni=example.com" +
            "&dnstt_ns=ns.example.com" +
            "&dnstt_pubkey=" + "a".repeat(64) +
            "&dnstt_resolver=1.1.1.1%3A53" +
            "&dnstt_record_type=null" +
            "#vmess%20dns"

        val parsed = VmessFmt.parse(link)

        assertDnsttRestored(parsed)
        assertEquals("vmess dns", parsed?.remarks)
    }

    @Test
    fun toUri_plainVmessExportsWithoutDnsttKeys() {
        val profile = vmessDnsttProfile().apply {
            dnsttEnabled = false
            dnsttNS = null
            dnsttPubkey = null
            dnsttResolver = null
            dnsttRecordType = null
        }

        val json = Utils.decode(VmessFmt.toUri(profile))

        assertFalse(json.contains("dnstt_"), "plain VMess export must not carry DNSTT keys: $json")
    }

    @Test
    fun parse_plainVmessLinkBackwardCompatible() {
        val plain = vmessDnsttProfile().apply {
            dnsttEnabled = false
            dnsttNS = null
            dnsttPubkey = null
            dnsttResolver = null
            dnsttRecordType = null
        }
        val uri = "vmess://" + VmessFmt.toUri(plain)

        val parsed = VmessFmt.parse(uri)

        assertEquals(EConfigType.VMESS, parsed?.configType)
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

    @Test
    fun parse_plainVmessStdUriBackwardCompatible() {
        val link = "vmess://11111111-2222-4333-8444-555555555555@example.com:443" +
            "?security=tls&type=ws&host=example.com&path=%2Fvmess&sni=example.com#Plain"

        val parsed = VmessFmt.parse(link)

        assertEquals(EConfigType.VMESS, parsed?.configType)
        assertEquals("example.com", parsed?.server)
        assertEquals("443", parsed?.serverPort)
        assertNull(parsed?.dnsttEnabled)
        assertNull(parsed?.dnsttNS)
        assertFalse(parsed?.isDnsttEnabled() == true)
    }
}
