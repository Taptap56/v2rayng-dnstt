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
    ) = ProfileItem(
        configType = EConfigType.VMESS,
        dnsttEnabled = enabled,
        dnsttNS = ns,
        dnsttPubkey = pubkey,
        dnsttResolver = resolver,
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
        assertFalse(profile.isDnsttEnabled())
    }

    @Test
    fun dnsttSocksOutboundPointsAtLocalListener() {
        val outbound = CoreOutboundBuilder.createDnsttSocksOutbound()

        assertEquals(AppConfig.TAG_DNSTT_SOCKS, outbound.tag)
        assertEquals("socks", outbound.protocol)
        assertEquals(AppConfig.DNSTT_LISTEN_HOST, outbound.settings?.address)
        assertEquals(AppConfig.DNSTT_LISTEN_PORT, outbound.settings?.port)
        assertEquals(AppConfig.DEFAULT_LEVEL, outbound.settings?.level)
    }

    @Test
    fun dnsttDialerProxyIsSetOnOutbound() {
        val outbound = CoreOutboundBuilder.createInitOutbound(EConfigType.VMESS)!!
        outbound.ensureSockopt().dialerProxy = AppConfig.TAG_DNSTT_SOCKS

        assertEquals(AppConfig.TAG_DNSTT_SOCKS, outbound.streamSettings?.sockopt?.dialerProxy)
    }
}
