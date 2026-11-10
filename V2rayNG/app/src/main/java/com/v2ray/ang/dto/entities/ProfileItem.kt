package com.v2ray.ang.dto.entities

import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.Utils

data class ProfileItem(
    val configVersion: Int = 4,
    val configType: EConfigType,
    var subscriptionId: String = "",
    var addedTime: Long = System.currentTimeMillis(),

    var remarks: String = "",
    var description: String? = null,
    var server: String? = null,
    var serverPort: String? = null,

    var password: String? = null,
    var method: String? = null,
    var flow: String? = null,
    var username: String? = null,

    var network: String? = null,
    var headerType: String? = null,
    var host: String? = null,
    var path: String? = null,
    var seed: String? = null,
    var kcpMtu: Int? = null,
    var kcpTti: Int? = null,

    var quicSecurity: String? = null,
    var quicKey: String? = null,
    var mode: String? = null,
    var serviceName: String? = null,
    var authority: String? = null,
    var xhttpMode: String? = null,
    var xhttpExtra: String? = null,
    var finalMask: String? = null,

    var security: String? = null,
    var sni: String? = null,
    var alpn: String? = null,
    var fingerPrint: String? = null,
    var insecure: Boolean? = null,
    var echConfigList: String? = null,
    var verifyPeerCertByName: String? = null,
    var pinnedCA256: String? = null,

    var publicKey: String? = null,
    var shortId: String? = null,
    var spiderX: String? = null,
    var mldsa65Verify: String? = null,

    var secretKey: String? = null,
    var preSharedKey: String? = null,
    var localAddress: String? = null,
    var reserved: String? = null,
    var mtu: Int? = null,
    var remoteDNS: String? = null,

    var obfsPassword: String? = null,
    var portHopping: String? = null,
    var portHoppingInterval: String? = null,
    @Deprecated("Use pinnedCA256")
    var pinSHA256: String? = null,
    var bandwidthDown: String? = null,
    var bandwidthUp: String? = null,

    var policyGroupType: String? = null,
    var policyGroupSubscriptionId: String? = null,
    var policyGroupFilter: String? = null,
    var policyGroupTestOutbounds: Boolean? = null,
    var policyGroupFallbackTag: String? = null,
    var proxyChainProfiles: String? = null,

    var browserDialerMode: String? = null,

    // DNSTT (SlowDNS): when enabled, this server's outbound dials
    // through the native "dnstt" outbound in the core (one shared
    // DNSTT session, in-process) instead of directly. dnsttNS is the
    // tunnel domain (the user's NS), dnsttPubkey the DNSTT server
    // public key (64 hex chars), dnsttResolver the DNS resolver
    // (host:port, default 1.1.1.1:53), dnsttRecordType the DNS record
    // type ("txt" default, "null" for servers that only answer NULL).
    // New fields only: absent in older configs, which Gson reads as
    // null (disabled / TXT default).
    var dnsttEnabled: Boolean? = null,
    var dnsttNS: String? = null,
    var dnsttPubkey: String? = null,
    var dnsttResolver: String? = null,
    var dnsttRecordType: String? = null,

    // SSH private key (PEM, key auth). Only used by EConfigType.SSH;
    // null for every other type. Like password, never logged.
    var sshPrivateKey: String? = null,

    // SSH transport layers (HTTP Custom style): the four methods are
    // independent, combinable on/off layers, not a single choice.
    // sshUsePayload sends the custom payload template ([host]/[port])
    // fire-and-forget; sshUseEnhanced sends it and then reads the
    // server's HTTP response headers; sshUseTls adds the TLS
    // handshake (sshSni, sshSniVersion "default" = auto, sshInsecure
    // skip-verify); sshUseSlowdns dials through DNSTT. sshRemoteProxy
    // is the remote HTTP CONNECT proxy ("[user:pass@]host:port"), the
    // first hop when set. No layer on = direct TCP dial (the behaviour
    // of older profiles). sshMethod is the legacy single method
    // ("" / "payload" / "ws" / "ws-tls" / "tls" / "slowdns"), kept
    // only so older profiles and links can migrate to the layers;
    // new saves write null. Only used by EConfigType.SSH. New fields
    // only: absent in older configs, which Gson reads as null.
    var sshMethod: String? = null,
    var sshUsePayload: Boolean? = null,
    var sshUseEnhanced: Boolean? = null,
    var sshUseTls: Boolean? = null,
    var sshUseSlowdns: Boolean? = null,
    var sshRemoteProxy: String? = null,
    var sshPayload: String? = null,
    var sshSni: String? = null,
    var sshSniVersion: String? = null,
    var sshInsecure: Boolean? = null,
) {

    /** True when DNSTT is enabled and has the NS domain and server public key it needs. */
    fun isDnsttEnabled(): Boolean =
        dnsttEnabled == true && !dnsttNS.isNullOrBlank() && !dnsttPubkey.isNullOrBlank()

    /**
     * True when this is an SSH server dialed through a DNSTT (SlowDNS)
     * tunnel: SSH keeps dnsttEnabled off (no dialerProxy — the tunnel
     * lives inside the core), so the SSH condition is type + NS +
     * pubkey, not the DNSTT toggle. The layers gate it: when any
     * layer flag is set, the slowdns flag alone decides (so turning
     * SlowDNS off in the editor stays off even with DNSTT fields
     * present); when no layer flag is set at all, the legacy rule
     * applies (blank/legacy-slowdns method + DNSTT fields set
     * implies SlowDNS), and any other legacy method wins over stale
     * DNSTT fields.
     */
    fun isSshSlowDns(): Boolean {
        if (configType != EConfigType.SSH) return false
        if (dnsttNS.isNullOrBlank() || dnsttPubkey.isNullOrBlank()) return false
        if (sshUsePayload != null || sshUseEnhanced != null || sshUseTls != null || sshUseSlowdns != null) {
            return sshUseSlowdns == true
        }
        return sshMethod.isNullOrBlank() || sshMethod == AppConfig.SSH_METHOD_SLOWDNS
    }

    /**
     * Effective layer flags: the layer flag wins; otherwise the
     * legacy single sshMethod maps to its layer. "ws"/"ws-tls" map
     * to no layer (direct): the WS dial is gone. These are used by
     * the editor (ServerUiState) and by the core builder
     * (CoreOutboundBuilder), so a profile stored by the previous
     * release keeps its method even if it was never re-opened in
     * the editor.
     */
    fun sshEffectiveUsePayload(): Boolean =
        sshUsePayload == true || sshMethod == AppConfig.SSH_METHOD_PAYLOAD

    fun sshEffectiveUseEnhanced(): Boolean =
        sshUseEnhanced == true || sshMethod == AppConfig.SSH_METHOD_ENHANCED

    fun sshEffectiveUseTls(): Boolean =
        sshUseTls == true || sshMethod == AppConfig.SSH_METHOD_TLS

    fun sshEffectiveUseSlowdns(): Boolean =
        sshUseSlowdns == true || sshMethod == AppConfig.SSH_METHOD_SLOWDNS || isSshSlowDns()

    companion object {
        fun create(configType: EConfigType): ProfileItem =
            ProfileItem(configType = configType)
    }

    fun getServerAddressAndPort(): String {
        if (server.isNullOrEmpty() && configType == EConfigType.CUSTOM) {
            return "${AppConfig.LOOPBACK}:${AppConfig.PORT_SOCKS}"
        }
        return "${Utils.getIpv6Address(server)}:$serverPort"
    }

    /**
     * Dedicated identity for "remove duplicate configurations".
     *
     * Ignores metadata that does not affect connection:
     * - configVersion
     * - subscriptionId
     * - addedTime
     * - remarks
     * - description
     *
     * All other fields, including configType, are included in the comparison.
     *
     * Returns a copy; the caller must not modify it further.
     */
    fun duplicateIdentity(): ProfileItem =
        copy(
            configVersion = 0,
            subscriptionId = "",
            addedTime = 0L,
            remarks = "",
            description = null
        )
}
