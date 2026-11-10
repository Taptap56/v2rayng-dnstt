package com.v2ray.ang.ui.server

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerUiStateSshTest {

    private fun sshProfile() = ProfileItem(
        configType = EConfigType.SSH,
        server = "ssh.example.com",
        serverPort = "2222",
        username = "alice",
        password = "secret",
        sshPrivateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----",
        dnsttNS = "ns.example.com",
        dnsttPubkey = "b".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "null",
    )

    @Test
    fun sshFieldsRoundTripThroughUiState() {
        val state = ServerUiState.fromProfileItem(sshProfile())

        assertEquals("ssh.example.com", state.address)
        assertEquals("2222", state.port)
        assertEquals("alice", state.username)
        assertEquals("secret", state.password)
        assertEquals(
            "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----",
            state.sshPrivateKey
        )
        assertEquals("ns.example.com", state.dnsttNS)
        assertEquals("b".repeat(64), state.dnsttPubkey)
        assertEquals("1.1.1.1:53", state.dnsttResolver)
        assertEquals("null", state.dnsttRecordType)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals("ssh.example.com", restored.server)
        assertEquals("2222", restored.serverPort)
        assertEquals("alice", restored.username)
        assertEquals("secret", restored.password)
        assertEquals(
            "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----",
            restored.sshPrivateKey
        )
        assertEquals("ns.example.com", restored.dnsttNS)
        assertEquals("b".repeat(64), restored.dnsttPubkey)
        assertEquals("1.1.1.1:53", restored.dnsttResolver)
        assertEquals("null", restored.dnsttRecordType)
    }

    @Test
    fun sshFieldsDefaultToEmpty() {
        val state = ServerUiState.fromProfileItem(ProfileItem.create(EConfigType.SSH))

        assertEquals("", state.username)
        assertEquals("", state.password)
        assertEquals("", state.sshPrivateKey)
        assertEquals("", state.dnsttNS)
        assertEquals("", state.dnsttPubkey)
        assertEquals("", state.dnsttResolver)
        assertEquals("txt", state.dnsttRecordType)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals("", restored.username)
        assertEquals("", restored.password)
        assertNull(restored.sshPrivateKey)
        assertNull(restored.dnsttNS)
        assertNull(restored.dnsttPubkey)
        assertNull(restored.dnsttResolver)
        assertEquals("txt", restored.dnsttRecordType)
    }

    @Test
    fun blankSshPrivateKeyIsStoredAsNull() {
        val state = ServerUiState.fromProfileItem(sshProfile()).apply {
            sshPrivateKey = "   "
        }

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertNull(restored.sshPrivateKey)
    }

    @Test
    fun sshPrivateKeyIsDroppedForNonSshTypes() {
        val state = ServerUiState.fromProfileItem(sshProfile()).apply {
            configType = EConfigType.VMESS
        }

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertNull(restored.sshPrivateKey)
    }

    private fun sshLayeredProfile() = ProfileItem(
        configType = EConfigType.SSH,
        server = "ssh.example.com",
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
        dnsttPubkey = "b".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "null",
    )

    @Test
    fun sshLayerFieldsRoundTripThroughUiState() {
        val state = ServerUiState.fromProfileItem(sshLayeredProfile())

        assertEquals(true, state.sshUsePayload)
        assertEquals(true, state.sshUseEnhanced)
        assertEquals(true, state.sshUseTls)
        assertEquals(true, state.sshUseSlowdns)
        assertEquals("user:pass@proxy.example.com:8080", state.sshRemoteProxy)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", state.sshPayload)
        assertEquals("sni.example.com", state.sshSni)
        assertEquals("tls1.2", state.sshSniVersion)
        assertEquals(true, state.sshInsecure)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals(true, restored.sshUsePayload)
        assertEquals(true, restored.sshUseEnhanced)
        assertEquals(true, restored.sshUseTls)
        assertEquals(true, restored.sshUseSlowdns)
        assertEquals("user:pass@proxy.example.com:8080", restored.sshRemoteProxy)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", restored.sshPayload)
        assertEquals("sni.example.com", restored.sshSni)
        assertEquals("tls1.2", restored.sshSniVersion)
        assertEquals(true, restored.sshInsecure)
        // The legacy single method is no longer written.
        assertNull(restored.sshMethod)
    }

    @Test
    fun sshPayloadLayerRoundTripThroughUiState() {
        val profile = ProfileItem(
            configType = EConfigType.SSH,
            server = "ssh.example.com",
            serverPort = "3128",
            username = "alice",
            password = "secret",
            sshUsePayload = true,
            sshPayload = "GET / HTTP/1.1\nHost: [host]\n\n",
        )
        val state = ServerUiState.fromProfileItem(profile)

        assertEquals(true, state.sshUsePayload)
        assertEquals(false, state.sshUseEnhanced)
        assertEquals(false, state.sshUseTls)
        assertEquals(false, state.sshUseSlowdns)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", state.sshPayload)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals(true, restored.sshUsePayload)
        assertEquals(false, restored.sshUseEnhanced)
        assertEquals(false, restored.sshUseTls)
        assertEquals(false, restored.sshUseSlowdns)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", restored.sshPayload)
    }

    @Test
    fun sshLayersDefaultToOffAndSniVersionToDefault() {
        val state = ServerUiState.fromProfileItem(ProfileItem.create(EConfigType.SSH))

        assertEquals(false, state.sshUsePayload)
        assertEquals(false, state.sshUseEnhanced)
        assertEquals(false, state.sshUseTls)
        assertEquals(false, state.sshUseSlowdns)
        assertEquals("", state.sshRemoteProxy)
        assertEquals("", state.sshPayload)
        assertEquals("", state.sshSni)
        assertEquals("default", state.sshSniVersion)
        assertEquals(false, state.sshInsecure)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals(false, restored.sshUsePayload)
        assertEquals(false, restored.sshUseEnhanced)
        assertEquals(false, restored.sshUseTls)
        assertEquals(false, restored.sshUseSlowdns)
        assertNull(restored.sshRemoteProxy)
        assertNull(restored.sshPayload)
        assertNull(restored.sshSni)
        assertEquals("default", restored.sshSniVersion)
        assertEquals(false, restored.sshInsecure)
    }

    @Test
    fun oldSlowDnsProfileWithoutMethodShowsSlowDnsLayer() {
        // Older SlowDNS profiles have DNSTT fields but no method:
        // the editor shows the SlowDNS layer on (the core implies it).
        val state = ServerUiState.fromProfileItem(sshProfile())

        assertEquals(true, state.sshUseSlowdns)
        assertEquals(false, state.sshUsePayload)
        assertEquals(false, state.sshUseTls)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals(true, restored.sshUseSlowdns)
        assertEquals("ns.example.com", restored.dnsttNS)
    }

    @Test
    fun legacyMethodMigratesToLayerFlags() {
        val profile = ProfileItem(
            configType = EConfigType.SSH,
            server = "ssh.example.com",
            serverPort = "3128",
            username = "alice",
            password = "secret",
            sshMethod = "payload",
            sshPayload = "GET / HTTP/1.1\nHost: [host]\n\n",
        )
        val state = ServerUiState.fromProfileItem(profile)

        assertEquals(true, state.sshUsePayload)
        assertEquals(false, state.sshUseTls)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals(true, restored.sshUsePayload)
        assertNull(restored.sshMethod)
    }

    @Test
    fun legacyWsTlsMethodMigratesToDirect() {
        // A profile from the previous release with method "ws-tls":
        // the WS dial is gone, so no layer is on (direct).
        val profile = ProfileItem(
            configType = EConfigType.SSH,
            server = "ssh.example.com",
            serverPort = "443",
            username = "alice",
            password = "secret",
            sshMethod = "ws-tls",
            sshSni = "sni.example.com",
            sshSniVersion = "tls1.2",
            sshInsecure = true,
        )
        val state = ServerUiState.fromProfileItem(profile)

        assertEquals(false, state.sshUsePayload)
        assertEquals(false, state.sshUseEnhanced)
        assertEquals(false, state.sshUseTls)
        assertEquals(false, state.sshUseSlowdns)
        assertEquals("sni.example.com", state.sshSni)
        assertEquals("tls1.2", state.sshSniVersion)
        assertEquals(true, state.sshInsecure)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals(false, restored.sshUsePayload)
        assertEquals(false, restored.sshUseTls)
        assertNull(restored.sshMethod)
    }

    @Test
    fun sshLayerFieldsDroppedForNonSshTypes() {
        val state = ServerUiState.fromProfileItem(sshLayeredProfile()).apply {
            configType = EConfigType.VMESS
        }

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertNull(restored.sshUsePayload)
        assertNull(restored.sshUseEnhanced)
        assertNull(restored.sshUseTls)
        assertNull(restored.sshUseSlowdns)
        assertNull(restored.sshRemoteProxy)
        assertNull(restored.sshPayload)
        assertNull(restored.sshSni)
        assertNull(restored.sshSniVersion)
        assertNull(restored.sshInsecure)
    }
}
