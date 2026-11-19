package com.v2ray.ang.ui.server

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pemicu fields round-trip through the entry editor state:
 * the fields (chip, domain, interval) must survive into the
 * saved profile, and defaults must match the app's constants.
 */
class ServerUiStatePemicuTest {

    @Test
    fun pemicuFieldsRoundTrip() {
        val profile = ProfileItem(
            configType = EConfigType.VMESS,
            pemicuEnabled = true,
            pemicuDomain = "support.zoom.us",
            pemicuInterval = 60,
            pemicuPort = 8443,
        )
        val state = ServerUiState.from(profile)
        assertEquals("8443", state.pemicuPort)
        val saved = state.toProfileItem(profile)
        assertEquals(true, saved.pemicuEnabled)
        assertEquals("support.zoom.us", saved.pemicuDomain)
        assertEquals(60, saved.pemicuInterval)
        assertEquals(8443, saved.pemicuPort)
    }

    @Test
    fun pemicuPortTextRoundTrip() {
        // The editor port is a String (text field); non-numeric
        // or blank text is stored as null (the manager then falls
        // back to 443), a valid number is stored as-is.
        val profile = ProfileItem(configType = EConfigType.VMESS)
        val state = ServerUiState.from(profile)
        state.pemicuPort = "abc"
        assertNull(state.toProfileItem(profile).pemicuPort)
        state.pemicuPort = ""
        assertNull(state.toProfileItem(profile).pemicuPort)
        state.pemicuPort = "8443"
        assertEquals(8443, state.toProfileItem(profile).pemicuPort)
        state.pemicuPort = "0"
        assertEquals(0, state.toProfileItem(profile).pemicuPort)
    }

    @Test
    fun pemicuDefaultsAreOffAndEmpty() {
        val state = ServerUiState.from(ProfileItem(configType = EConfigType.VMESS))
        assertEquals(false, state.pemicuEnabled)
        assertEquals("", state.pemicuDomain)
        // The editor interval is a String (text field); the
        // default is the constant rendered as a string.
        assertEquals(com.v2ray.ang.AppConfig.PEMICU_DEFAULT_INTERVAL.toString(), state.pemicuInterval)
        // Same for the port: the editor shows the default port.
        assertEquals(com.v2ray.ang.AppConfig.PEMICU_DEFAULT_PORT.toString(), state.pemicuPort)
    }

    @Test
    fun pemicuOffRoundTripKeepsSavedDomain() {
        // Chip off = not primed, but the domain stays saved
        // (same rule as the payload contents).
        val profile = ProfileItem(
            configType = EConfigType.VMESS,
            pemicuEnabled = false,
            pemicuDomain = "support.zoom.us",
            pemicuInterval = 60,
        )
        val saved = ServerUiState.from(profile).toProfileItem(profile)
        assertEquals(false, saved.pemicuEnabled)
        assertEquals("support.zoom.us", saved.pemicuDomain)
        assertEquals(60, saved.pemicuInterval)
    }

    @Test
    fun pemicuEmptyDomainRoundTripsAsNull() {
        // A blank domain is stored as null (no fallback to the
        // entry address), matching the backend's blank check.
        val profile = ProfileItem(
            configType = EConfigType.VMESS,
            pemicuEnabled = true,
            pemicuDomain = "   ",
        )
        val saved = ServerUiState.from(profile).toProfileItem(profile)
        assertNull(saved.pemicuDomain)
    }

    @Test
    fun legacyProfilesWithoutPemicuFieldsStayDisabled() {
        // A profile from before Pemicu existed has null fields;
        // the editor must show the chip off, not on.
        val profile = ProfileItem(configType = EConfigType.VMESS)
        val state = ServerUiState.from(profile)
        assertEquals(false, state.pemicuEnabled)
        assertEquals("", state.pemicuDomain)
        assertEquals(com.v2ray.ang.AppConfig.PEMICU_DEFAULT_PORT.toString(), state.pemicuPort)
        val saved = state.toProfileItem(profile)
        assertEquals(false, saved.pemicuEnabled)
        assertNull(saved.pemicuDomain)
        assertNull(saved.pemicuPort)
    }
}
