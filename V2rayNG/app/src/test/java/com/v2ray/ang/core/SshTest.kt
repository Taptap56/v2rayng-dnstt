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
        recordType: String? = null,
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
        dnsttRecordType = recordType,
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
        assertNull(profile.dnsttRecordType)
        assertFalse(profile.isSshSlowDns())
        // SSH does not use the DNSTT toggle: no dialerProxy — the
        // tunnel lives inside the core.
        assertFalse(profile.isDnsttEnabled())
    }

    @Test
    fun sshOutboundHasSshProtocolAndSettings() {
        val outbound = CoreOutboundBuilder.toOutboundSsh(sshProfile(recordType = "null"))!!

        assertEquals("ssh", outbound.protocol)
        assertEquals("10.0.0.1", outbound.settings?.address)
        assertEquals(22, outbound.settings?.port)
        assertEquals("alice", outbound.settings?.username)
        assertEquals("secret", outbound.settings?.password)
        assertEquals("ns.example.com", outbound.settings?.dnsttNs)
        assertEquals("a".repeat(64), outbound.settings?.dnsttPubkey)
        assertNull(outbound.settings?.dnsttResolver)
        assertEquals("null", outbound.settings?.dnsttRecordType)
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

    @Test
    fun sshOutboundPassesLayerFlagsAndRemoteProxy() {
        val outbound = CoreOutboundBuilder.toOutboundSsh(
            sshProfile().copy(
                sshUsePayload = true,
                sshUseEnhanced = true,
                sshUseTls = true,
                sshUseSlowdns = true,
                sshRemoteProxy = "user:pass@proxy.example.com:8080",
                sshPayload = "GET / HTTP/1.1\nHost: [host]\n\n",
                sshSni = "sni.example.com",
                sshSniVersion = "tls1.2",
                sshInsecure = true,
            )
        )!!

        assertEquals(true, outbound.settings?.usePayload)
        assertEquals(true, outbound.settings?.useEnhanced)
        assertEquals(true, outbound.settings?.useTls)
        assertEquals(true, outbound.settings?.useSlowdns)
        assertEquals("user:pass@proxy.example.com:8080", outbound.settings?.remoteProxy)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", outbound.settings?.payload)
        assertEquals("sni.example.com", outbound.settings?.sni)
        assertEquals("tls1.2", outbound.settings?.sniVersion)
        assertEquals(true, outbound.settings?.insecure)
    }

    @Test
    fun sshOutboundMigratesLegacyMethodToLayers() {
        // A profile stored by the previous release (single method,
        // no layer flags) keeps its method even if it was never
        // re-opened in the editor.
        val outbound = CoreOutboundBuilder.toOutboundSsh(
            sshProfile(ns = null, pubkey = null).copy(
                sshMethod = "payload",
                sshPayload = "GET / HTTP/1.1\nHost: [host]\n\n",
            )
        )!!

        assertEquals(true, outbound.settings?.usePayload)
        assertEquals(false, outbound.settings?.useTls)
        assertEquals(false, outbound.settings?.useSlowdns)
    }

    @Test
    fun sshOutboundMigratesLegacyWsTlsMethodToDirect() {
        val outbound = CoreOutboundBuilder.toOutboundSsh(
            sshProfile(ns = null, pubkey = null).copy(sshMethod = "ws-tls")
        )!!

        assertEquals(false, outbound.settings?.usePayload)
        assertEquals(false, outbound.settings?.useEnhanced)
        assertEquals(false, outbound.settings?.useTls)
        assertEquals(false, outbound.settings?.useSlowdns)
    }

    @Test
    fun sshOutboundOmitsDnsttFieldsWhenSlowDnsExplicitlyOff() {
        // All layers explicitly off + stale DNSTT fields: the
        // outbound must not carry the tunnel settings, or the core
        // would imply SlowDNS from the set dnsttNs (proto3 bools
        // cannot tell "explicitly off" from "unset").
        val outbound = CoreOutboundBuilder.toOutboundSsh(
            sshProfile().copy(
                sshUsePayload = false,
                sshUseEnhanced = false,
                sshUseTls = false,
                sshUseSlowdns = false,
            )
        )!!

        assertEquals(false, outbound.settings?.useSlowdns)
        assertNull(outbound.settings?.dnsttNs)
        assertNull(outbound.settings?.dnsttPubkey)
        assertNull(outbound.settings?.dnsttResolver)
        assertNull(outbound.settings?.dnsttRecordType)
        assertNull(outbound.settings?.dnsttKcpWindow)
    }

    @Test
    fun sshSlowDnsWithLayers() {
        // SlowDNS layer on + NS + pubkey: SlowDNS.
        assertTrue(sshProfile().copy(sshUseSlowdns = true).isSshSlowDns())
        // TLS layer on (without the slowdns layer) + NS + pubkey:
        // the layer wins over the stale DNSTT fields.
        assertFalse(sshProfile().copy(sshUseTls = true).isSshSlowDns())
        // No layer on + NS + pubkey: the legacy rule (blank method
        // implies SlowDNS).
        assertTrue(sshProfile().isSshSlowDns())
        // All layers explicitly OFF + NS + pubkey: NOT SlowDNS. The
        // user turned it off in the editor; the legacy implication
        // must not re-enable it (it only applies when no layer flag
        // is set at all).
        assertFalse(
            sshProfile().copy(
                sshUsePayload = false,
                sshUseEnhanced = false,
                sshUseTls = false,
                sshUseSlowdns = false,
            ).isSshSlowDns()
        )
    }
}
