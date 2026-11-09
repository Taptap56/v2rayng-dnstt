package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Native SSH outbound (proxy/ssh in the forked core) + DNSTT dialer.
 * Only pure builders are tested here: convert() reads MMKV and cannot
 * run on plain JVM tests, so the SSH builder is internal for tests.
 */
class SshTest {

    private fun sshProfile(
        server: String? = "10.0.0.1",
        serverPort: String? = "22",
        username: String? = "alice",
        password: String? = "secret",
        sshPrivateKey: String? = null,
        ns: String? = "ns.example.com",
        pubkey: String? = "a".repeat(64),
        resolver: String? = null,
    ) = ProfileItem(
        configType = EConfigType.SSH,
        server = server,
        serverPort = serverPort,
        username = username,
        password = password,
        sshPrivateKey = sshPrivateKey,
        dnsttNS = ns,
        dnsttPubkey = pubkey,
        dnsttResolver = resolver,
    )

    @Test
    fun sshSlowDnsWhenTypeAndNsAndPubkeyPresent() {
        assertTrue(sshProfile().isSshSlowDns())
    }

    @Test
    fun sshSlowDnsFalseWhenNsOrPubkeyMissing() {
        assertFalse(sshProfile(ns = null).isSshSlowDns())
        assertFalse(sshProfile(ns = "  ").isSshSlowDns())
        assertFalse(sshProfile(pubkey = null).isSshSlowDns())
        assertFalse(sshProfile(pubkey = "").isSshSlowDns())
    }

    @Test
    fun sshSlowDnsFalseForOtherTypes() {
        val vmess = sshProfile().copy(configType = EConfigType.VMESS)
        assertFalse(vmess.isSshSlowDns())
    }

    @Test
    fun sshSlowDnsFalseByDefault() {
        val profile = ProfileItem(configType = EConfigType.SSH)
        assertNull(profile.dnsttNS)
        assertNull(profile.dnsttPubkey)
        assertFalse(profile.isSshSlowDns())
        // SSH does not use the DNSTT toggle: no dnstt-client process,
        // no dialerProxy — the tunnel lives inside the core.
        assertFalse(profile.isDnsttEnabled())
    }

    @Test
    fun sshOutboundHasSshProtocolAndSettings() {
        val outbound = CoreOutboundBuilder.toOutboundSsh(sshProfile())!!

        assertEquals("ssh", outbound.protocol)
        assertEquals("10.0.0.1", outbound.settings?.address)
        assertEquals(22, outbound.settings?.port)
        assertEquals("alice", outbound.settings?.username)
        assertEquals("secret", outbound.settings?.password)
        assertEquals("ns.example.com", outbound.settings?.dnsttNs)
        assertEquals("a".repeat(64), outbound.settings?.dnsttPubkey)
        assertNull(outbound.settings?.dnsttResolver)
        assertEquals(AppConfig.DNSTT_DEFAULT_KCP_WINDOW, outbound.settings?.dnsttKcpWindow)
        // Native SSH: no dialerProxy, no SOCKS hop.
        assertNull(outbound.streamSettings?.sockopt?.dialerProxy)
    }

    @Test
    fun sshOutboundWithoutDnsttOmitsTunnelSettings() {
        val outbound = CoreOutboundBuilder.toOutboundSsh(
            sshProfile(ns = null, pubkey = null)
        )!!

        assertEquals("ssh", outbound.protocol)
        assertNull(outbound.settings?.dnsttNs)
        assertNull(outbound.settings?.dnsttPubkey)
        assertNull(outbound.settings?.dnsttResolver)
        assertNull(outbound.settings?.dnsttKcpWindow)
    }

    @Test
    fun sshOutboundKeepsPrivateKeyWhenSet() {
        val outbound = CoreOutboundBuilder.toOutboundSsh(
            sshProfile(password = null, sshPrivateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----")
        )!!

        assertNull(outbound.settings?.password)
        assertEquals(
            "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----",
            outbound.settings?.privateKey
        )
    }

    @Test
    fun sshInitOutboundUsesSshProtocol() {
        val outbound = CoreOutboundBuilder.createInitOutbound(EConfigType.SSH)!!
        assertEquals("ssh", outbound.protocol)
    }
}
