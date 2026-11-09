package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsttTest {

    private fun dnsttProfile(
        enabled: Boolean? = true,
        ns: String? = "ns.example.com",
        pubkey: String? = "a".repeat(64),
        resolver: String? = null,
        recordType: String? = null,
    ) = ProfileItem(
        configType = EConfigType.VMESS,
        dnsttEnabled = enabled,
        dnsttNS = ns,
        dnsttPubkey = pubkey,
        dnsttResolver = resolver,
        dnsttRecordType = recordType,
    )

    @Test
    fun dnsttEnabledWhenFlagAndNsAndPubkeyPresent() {
        assertTrue(dnsttProfile().isDnsttEnabled())
    }

    @Test
    fun dnsttDisabledWhenFlagOff() {
        assertFalse(dnsttProfile(enabled = false).isDnsttEnabled())
        assertFalse(dnsttProfile(enabled = null).isDnsttEnabled())
    }

    @Test
    fun dnsttDisabledWhenNsOrPubkeyMissing() {
        assertFalse(dnsttProfile(ns = null).isDnsttEnabled())
        assertFalse(dnsttProfile(ns = "  ").isDnsttEnabled())
        assertFalse(dnsttProfile(pubkey = null).isDnsttEnabled())
        assertFalse(dnsttProfile(pubkey = "").isDnsttEnabled())
    }

    @Test
    fun dnsttDisabledByDefault() {
        val profile = ProfileItem(configType = EConfigType.VMESS)
        assertNull(profile.dnsttEnabled)
        assertNull(profile.dnsttNS)
        assertNull(profile.dnsttPubkey)
        assertNull(profile.dnsttResolver)
        assertNull(profile.dnsttRecordType)
        assertFalse(profile.isDnsttEnabled())
    }

    @Test
    fun dnsttOutboundIsNativeWithTunnelSettings() {
        val outbound = CoreOutboundBuilder.createDnsttOutbound(
            dnsttProfile(resolver = "1.1.1.1:53", recordType = "null")
        )

        assertEquals(AppConfig.TAG_DNSTT, outbound.tag)
        assertEquals("dnstt", outbound.protocol)
        assertEquals("ns.example.com", outbound.settings?.dnsttNs)
        assertEquals("a".repeat(64), outbound.settings?.dnsttPubkey)
        assertEquals("1.1.1.1:53", outbound.settings?.dnsttResolver)
        assertEquals(AppConfig.DNSTT_DEFAULT_KCP_WINDOW, outbound.settings?.dnsttKcpWindow)
        assertEquals("null", outbound.settings?.dnsttRecordType)
    }

    @Test
    fun dnsttOutboundOmitsRecordTypeWhenUnset() {
        val outbound = CoreOutboundBuilder.createDnsttOutbound(dnsttProfile())

        // The core defaults an empty record type to TXT.
        assertNull(outbound.settings?.dnsttRecordType)
    }

    @Test
    fun dnsttDialerProxyIsSetOnOutbound() {
        val outbound = CoreOutboundBuilder.createInitOutbound(EConfigType.VMESS)!!
        outbound.ensureSockopt().dialerProxy = AppConfig.TAG_DNSTT

        assertEquals(AppConfig.TAG_DNSTT, outbound.streamSettings?.sockopt?.dialerProxy)
    }
}
