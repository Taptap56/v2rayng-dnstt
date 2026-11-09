package com.v2ray.ang.ui.server

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerUiStateDnsttTest {

    private fun dnsttProfile() = ProfileItem(
        configType = EConfigType.VMESS,
        dnsttEnabled = true,
        dnsttNS = "ns.example.com",
        dnsttPubkey = "b".repeat(64),
        dnsttResolver = "1.1.1.1:53",
        dnsttRecordType = "null",
    )

    @Test
    fun dnsttFieldsRoundTripThroughUiState() {
        val state = ServerUiState.fromProfileItem(dnsttProfile())

        assertTrue(state.dnsttEnabled)
        assertEquals("ns.example.com", state.dnsttNS)
        assertEquals("b".repeat(64), state.dnsttPubkey)
        assertEquals("1.1.1.1:53", state.dnsttResolver)
        assertEquals("null", state.dnsttRecordType)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertEquals(true, restored.dnsttEnabled)
        assertEquals("ns.example.com", restored.dnsttNS)
        assertEquals("b".repeat(64), restored.dnsttPubkey)
        assertEquals("1.1.1.1:53", restored.dnsttResolver)
        assertEquals("null", restored.dnsttRecordType)
    }

    @Test
    fun dnsttFieldsDefaultToDisabledAndEmpty() {
        val state = ServerUiState.fromProfileItem(ProfileItem.create(EConfigType.VMESS))

        assertFalse(state.dnsttEnabled)
        assertEquals("", state.dnsttNS)
        assertEquals("", state.dnsttPubkey)
        assertEquals("", state.dnsttResolver)
        // The record type defaults to TXT (the core default) even
        // when the profile never stored one.
        assertEquals("txt", state.dnsttRecordType)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertEquals(false, restored.dnsttEnabled)
        assertNull(restored.dnsttNS)
        assertNull(restored.dnsttPubkey)
        assertNull(restored.dnsttResolver)
        assertEquals("txt", restored.dnsttRecordType)
    }

    @Test
    fun blankDnsttTextIsStoredAsNull() {
        val state = ServerUiState.fromProfileItem(dnsttProfile()).apply {
            dnsttNS = "   "
            dnsttPubkey = ""
            dnsttResolver = "  "
        }

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertNull(restored.dnsttNS)
        assertNull(restored.dnsttPubkey)
        assertNull(restored.dnsttResolver)
    }
}
