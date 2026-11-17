package com.v2ray.ang.ui.server

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Onering (dharak36/xray-onering, port TB v1.0.44 / TC v1.0.60):
 * enable toggle + bug-domain field, same as TB/TC's Xray-method
 * Onering chip + Bug column.
 */
class ServerUiStateOneringTest {

    private fun oneringProfile() = ProfileItem(
        configType = EConfigType.VMESS,
        sni = "leaseweb4.svrkencang.web.id",
        oneringEnabled = true,
        oneringBug = "chat.sociomile.com",
    )

    @Test
    fun oneringFieldsRoundTripThroughUiState() {
        val state = ServerUiState.fromProfileItem(oneringProfile())

        assertTrue(state.oneringEnabled)
        assertEquals("chat.sociomile.com", state.oneringBug)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertEquals(true, restored.oneringEnabled)
        assertEquals("chat.sociomile.com", restored.oneringBug)
    }

    @Test
    fun oneringOffByDefaultAndBlankBugDrops() {
        val state = ServerUiState.fromProfileItem(ProfileItem(configType = EConfigType.VMESS))

        assertFalse(state.oneringEnabled)
        assertEquals("", state.oneringBug)

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertEquals(false, restored.oneringEnabled)
        assertNull(restored.oneringBug)
    }

    @Test
    fun oneringBugBlankedWhenDisabled() {
        val state = ServerUiState.fromProfileItem(oneringProfile())
        state.oneringEnabled = false
        state.oneringBug = "  "

        val restored = state.toProfileItem(ProfileItem.create(EConfigType.VMESS))
        assertEquals(false, restored.oneringEnabled)
        assertNull(restored.oneringBug)
    }
}
