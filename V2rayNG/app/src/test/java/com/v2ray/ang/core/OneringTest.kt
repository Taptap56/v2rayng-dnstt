package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Onering (dharak36/xray-onering, port TB v1.0.44 / TC v1.0.60):
 * the TLS ServerName sent to the core is "onering:<real>:<bug>".
 */
class OneringTest {

    private fun oneringProfile(
        sni: String? = "leaseweb4.svrkencang.web.id",
        enabled: Boolean? = true,
        bug: String? = "chat.sociomile.com",
    ) = ProfileItem(
        configType = EConfigType.VMESS,
        server = "support.zoom.us",
        sni = sni,
        security = AppConfig.TLS,
        oneringEnabled = enabled,
        oneringBug = bug,
    )

    @Test
    fun parseOneringSniSplitsRealAndBug() {
        val (real, bug) = AppConfig.parseOneringSni(
            "onering:leaseweb4.svrkencang.web.id:chat.sociomile.com"
        )!!
        assertEquals("leaseweb4.svrkencang.web.id", real)
        assertEquals("chat.sociomile.com", bug)
    }

    @Test
    fun parseOneringSniRejectsPlainAndMalformed() {
        assertNull(AppConfig.parseOneringSni("support.zoom.us"))
        assertNull(AppConfig.parseOneringSni(null))
        assertNull(AppConfig.parseOneringSni(""))
        assertNull(AppConfig.parseOneringSni("onering::chat.sociomile.com"))
        assertNull(AppConfig.parseOneringSni("onering:leaseweb4.svrkencang.web.id:"))
        assertNull(AppConfig.parseOneringSni("onering:only-real"))
    }

    @Test
    fun buildOneringSniCombinesRealAndBug() {
        assertEquals(
            "onering:leaseweb4.svrkencang.web.id:chat.sociomile.com",
            AppConfig.buildOneringSni("leaseweb4.svrkencang.web.id", "chat.sociomile.com")
        )
    }

    @Test
    fun buildOneringSniReturnsNullWithoutBugOrReal() {
        assertNull(AppConfig.buildOneringSni("leaseweb4.svrkencang.web.id", null))
        assertNull(AppConfig.buildOneringSni("leaseweb4.svrkencang.web.id", "  "))
        assertNull(AppConfig.buildOneringSni(null, "chat.sociomile.com"))
        assertNull(AppConfig.buildOneringSni("  ", "chat.sociomile.com"))
    }

    @Test
    fun effectiveSniIsOneringWhenEnabled() {
        assertEquals(
            "onering:leaseweb4.svrkencang.web.id:chat.sociomile.com",
            AppConfig.effectiveSni(oneringProfile())
        )
    }

    @Test
    fun effectiveSniIsPlainWhenDisabled() {
        assertEquals(
            "leaseweb4.svrkencang.web.id",
            AppConfig.effectiveSni(oneringProfile(enabled = false))
        )
        assertEquals(
            "leaseweb4.svrkencang.web.id",
            AppConfig.effectiveSni(oneringProfile(enabled = null))
        )
        assertEquals(
            "leaseweb4.svrkencang.web.id",
            AppConfig.effectiveSni(oneringProfile(bug = null))
        )
        assertEquals(
            "leaseweb4.svrkencang.web.id",
            AppConfig.effectiveSni(oneringProfile(bug = "  "))
        )
    }

    @Test
    fun populateTlsSettingsSendsOneringServerName() {
        val streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()
        CoreOutboundBuilder.populateTlsSettings(streamSettings, oneringProfile(), null)

        assertEquals(
            "onering:leaseweb4.svrkencang.web.id:chat.sociomile.com",
            streamSettings.tlsSettings?.serverName
        )
    }

    @Test
    fun populateTlsSettingsSendsPlainServerNameWhenOneringOff() {
        val streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()
        CoreOutboundBuilder.populateTlsSettings(
            streamSettings, oneringProfile(enabled = false), null
        )

        assertEquals("leaseweb4.svrkencang.web.id", streamSettings.tlsSettings?.serverName)
    }

    @Test
    fun oneringDisabledByDefault() {
        val profile = ProfileItem(configType = EConfigType.VMESS)
        assertNull(profile.oneringEnabled)
        assertNull(profile.oneringBug)
        assertFalse(profile.oneringEnabled == true)
    }
}
