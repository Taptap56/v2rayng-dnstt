package com.v2ray.ang.ui.server

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun sshWsTlsProfile() = ProfileItem(
        configType = EConfigType.SSH,
        server = "ssh.example.com",
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
    fun sshMethodFieldsRoundTripThroughUiState() {
        val state = ServerUiState.fromProfileItem(sshWsTlsProfile())

        assertEquals("ws-tls", state.sshMethod)
        assertEquals("/ssh", state.sshWsPath)
        assertEquals("ws.example.com", state.sshWsHost)
        assertEquals("sni.example.com", state.sshSni)
        assertEquals("tls1.2", state.sshSniVersion)
        assertEquals(true, state.sshInsecure)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals("ws-tls", restored.sshMethod)
        assertEquals("/ssh", restored.sshWsPath)
        assertEquals("ws.example.com", restored.sshWsHost)
        assertEquals("sni.example.com", restored.sshSni)
        assertEquals("tls1.2", restored.sshSniVersion)
        assertEquals(true, restored.sshInsecure)
    }

    @Test
    fun sshPayloadMethodRoundTripThroughUiState() {
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

        assertEquals("payload", state.sshMethod)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", state.sshPayload)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals("payload", restored.sshMethod)
        assertEquals("GET / HTTP/1.1\nHost: [host]\n\n", restored.sshPayload)
    }

    @Test
    fun sshMethodDefaultsToDirectAndSniVersionToDefault() {
        val state = ServerUiState.fromProfileItem(ProfileItem.create(EConfigType.SSH))

        assertEquals("", state.sshMethod)
        assertEquals("", state.sshPayload)
        assertEquals("", state.sshWsPath)
        assertEquals("", state.sshWsHost)
        assertEquals("", state.sshSni)
        assertEquals("default", state.sshSniVersion)
        assertEquals(false, state.sshInsecure)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertNull(restored.sshMethod)
        assertNull(restored.sshPayload)
        assertNull(restored.sshWsPath)
        assertNull(restored.sshWsHost)
        assertNull(restored.sshSni)
        assertEquals("default", restored.sshSniVersion)
        assertEquals(false, restored.sshInsecure)
    }

    @Test
    fun oldSlowDnsProfileWithoutMethodShowsAsSlowDns() {
        // Older SlowDNS profiles have DNSTT fields but no method:
        // the editor shows them as SlowDNS (the core implies it).
        val state = ServerUiState.fromProfileItem(sshProfile())

        assertEquals("slowdns", state.sshMethod)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals("slowdns", restored.sshMethod)
        assertEquals("ns.example.com", restored.dnsttNS)
    }

    @Test
    fun sshMethodFieldsDroppedForNonSshTypes() {
        val state = ServerUiState.fromProfileItem(sshWsTlsProfile()).apply {
            configType = EConfigType.VMESS
        }

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertNull(restored.sshMethod)
        assertNull(restored.sshPayload)
        assertNull(restored.sshWsPath)
        assertNull(restored.sshWsHost)
        assertNull(restored.sshSni)
        assertNull(restored.sshSniVersion)
        assertNull(restored.sshInsecure)
    }
}
