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

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.SSH))
        assertEquals("", restored.username)
        assertEquals("", restored.password)
        assertNull(restored.sshPrivateKey)
        assertNull(restored.dnsttNS)
        assertNull(restored.dnsttPubkey)
        assertNull(restored.dnsttResolver)
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
}
