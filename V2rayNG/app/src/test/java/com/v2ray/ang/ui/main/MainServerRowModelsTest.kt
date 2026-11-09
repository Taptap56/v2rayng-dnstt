package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The server-list subtitle marks servers dialed through DNSTT
 * (SlowDNS) with a trailing "/dns", so a SlowDNS server is visible
 * at a glance instead of looking identical to a plain server.
 */
class MainServerRowModelsTest {

    private fun row(profile: ProfileItem) =
        buildServerRowUiModel(
            ServersCache(guid = "guid-1", profile = profile, testDelayMillis = 0L),
            subscriptionRemarks = "",
        )

    private fun vmessSlowDns() = ProfileItem(
        configType = EConfigType.VMESS,
        remarks = "vmess dns",
        server = "example.com",
        serverPort = "443",
        network = "ws",
        security = "tls",
        dnsttEnabled = true,
        dnsttNS = "ns.example.com",
        dnsttPubkey = "a".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "txt",
    )

    private fun sshSlowDns() = ProfileItem(
        configType = EConfigType.SSH,
        remarks = "ssh dns",
        server = "example.com",
        serverPort = "3128",
        username = "alice",
        dnsttNS = "ns.example.com",
        dnsttPubkey = "a".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "null",
    )

    @Test
    fun vmessSlowDnsSubtitleAppendsDns() {
        assertEquals("VMESS / ws / tls / dns", row(vmessSlowDns()).typeDescription)
    }

    @Test
    fun sshSlowDnsSubtitleAppendsDns() {
        assertEquals("SSH / dns", row(sshSlowDns()).typeDescription)
    }

    @Test
    fun vlessSlowDnsSubtitleAppendsDns() {
        val profile = vmessSlowDns().copy(
            configType = EConfigType.VLESS,
            network = "tcp",
            security = null,
        )
        assertEquals("VLESS / dns", row(profile).typeDescription)
    }

    @Test
    fun vmessWithoutSlowDnsSubtitleUnchanged() {
        val profile = vmessSlowDns().copy(
            dnsttEnabled = false,
            dnsttNS = null,
            dnsttPubkey = null,
            dnsttResolver = null,
            dnsttRecordType = null,
        )
        assertEquals("VMESS / ws / tls", row(profile).typeDescription)
    }

    @Test
    fun sshWithoutSlowDnsSubtitleUnchanged() {
        val profile = sshSlowDns().copy(dnsttNS = null, dnsttPubkey = null)
        assertEquals("SSH", row(profile).typeDescription)
    }

    @Test
    fun slowDnsFlagWithoutNsOrPubkeyDoesNotAppendDns() {
        // The toggle alone is not enough: without NS + pubkey the
        // server is not actually dialed through DNSTT.
        val profile = vmessSlowDns().copy(dnsttPubkey = null)
        assertEquals("VMESS / ws / tls", row(profile).typeDescription)
    }
}
