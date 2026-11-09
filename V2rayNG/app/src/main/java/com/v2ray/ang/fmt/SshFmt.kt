package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.Utils
import java.net.URI

object SshFmt : FmtBase() {

    /**
     * Parses an SSH URL into a ProfileItem object, including the DNSTT
     * (SlowDNS) settings and the transport method (HTTP Custom style)
     * carried as query parameters:
     * ssh://username:password@host:port?dnstt_ns=...&dnstt_pubkey=...&dnstt_resolver=...&dnstt_record_type=null&method=ws-tls&ws_path=/ssh&ws_host=...&sni=...&sni_version=tls1.2&insecure=1#remarks
     * A plain ssh:// link without DNSTT/method parameters imports as a
     * direct SSH server, exactly as before. The private key, when
     * present, is read from the "private_key" query parameter.
     *
     * @param str the SSH URL string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SSH)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isBlank()) return null

        // URI.getUserInfo() is percent-decoded: a ":" inside the
        // username or password was encoded as %3A by toUri, so the
        // FIRST ":" is the separator.
        val userInfo = uri.userInfo
        if (!userInfo.isNullOrEmpty()) {
            val idx = userInfo.indexOf(':')
            if (idx >= 0) {
                config.username = userInfo.substring(0, idx).ifEmpty { null }
                config.password = userInfo.substring(idx + 1).ifEmpty { null }
            } else {
                config.username = userInfo
            }
        }

        config.server = uri.idnHost
        // toOutboundSsh does serverPort.orEmpty().toInt(), so a
        // missing port must default to 22 instead of staying blank.
        config.serverPort = if (uri.port > 0) uri.port.toString() else "22"
        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)
            config.dnsttNS = queryParam["dnstt_ns"]?.nullIfBlank()
            config.dnsttPubkey = queryParam["dnstt_pubkey"]?.nullIfBlank()
            config.dnsttResolver = queryParam["dnstt_resolver"]?.nullIfBlank()
            config.dnsttRecordType = queryParam["dnstt_record_type"]?.nullIfBlank()
            config.sshPrivateKey = queryParam["private_key"]?.nullIfBlank()
            // Transport method (HTTP Custom style). A link without it
            // is a direct SSH server, as before.
            config.sshMethod = queryParam["method"]?.nullIfBlank()
            config.sshPayload = queryParam["payload"]?.nullIfBlank()
            config.sshWsPath = queryParam["ws_path"]?.nullIfBlank()
            config.sshWsHost = queryParam["ws_host"]?.nullIfBlank()
            config.sshSni = queryParam["sni"]?.nullIfBlank()
            config.sshSniVersion = queryParam["sni_version"]?.nullIfBlank()
            config.sshInsecure = when (queryParam["insecure"]?.lowercase()) {
                "1", "true" -> true
                else -> null
            }
        }

        return config
    }

    /**
     * Converts a ProfileItem object to an SSH URI string (without the
     * "ssh://" scheme prefix; shareConfig prepends it). The DNSTT
     * parameters are only included when the server is actually dialed
     * through SlowDNS, and the method parameters only when a method is
     * set, so sharing a plain SSH server yields the same URL as
     * before. The password lives in the userinfo, like the other
     * protocols; the private key is a URL-encoded query parameter,
     * only when present.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()
        if (config.isSshSlowDns()) {
            config.dnsttNS?.nullIfBlank()?.let { dicQuery["dnstt_ns"] = it }
            config.dnsttPubkey?.nullIfBlank()?.let { dicQuery["dnstt_pubkey"] = it }
            config.dnsttResolver?.nullIfBlank()?.let { dicQuery["dnstt_resolver"] = it }
            config.dnsttRecordType?.nullIfBlank()?.let { dicQuery["dnstt_record_type"] = it }
        }
        config.sshPrivateKey?.nullIfBlank()?.let { dicQuery["private_key"] = it }
        // Transport method (HTTP Custom style): only the parameters
        // of the selected method are included, so a direct SSH server
        // shares the same URL as before.
        val method = config.sshMethod?.nullIfBlank()
        method?.let { dicQuery["method"] = it }
        if (method == AppConfig.SSH_METHOD_PAYLOAD) {
            config.sshPayload?.nullIfBlank()?.let { dicQuery["payload"] = it }
        }
        if (method == AppConfig.SSH_METHOD_WS || method == AppConfig.SSH_METHOD_WS_TLS) {
            config.sshWsPath?.nullIfBlank()?.let { dicQuery["ws_path"] = it }
            config.sshWsHost?.nullIfBlank()?.let { dicQuery["ws_host"] = it }
        }
        if (method == AppConfig.SSH_METHOD_WS_TLS || method == AppConfig.SSH_METHOD_TLS) {
            config.sshSni?.nullIfBlank()?.let { dicQuery["sni"] = it }
            config.sshSniVersion?.nullIfBlank()?.let { dicQuery["sni_version"] = it }
            if (config.sshInsecure == true) dicQuery["insecure"] = "1"
        }

        val query = if (dicQuery.isNotEmpty())
            "?" + dicQuery.toList().joinToString(
                separator = "&",
                transform = { it.first + "=" + Utils.encodeURIComponent(it.second) })
        else ""

        // Encode the username and password separately: encoding the
        // combined "username:password" would turn the separator into
        // %3A and the password would be lost on import.
        val userInfo = buildString {
            config.username?.nullIfBlank()?.let { append(Utils.encodeURIComponent(it)) }
            config.password?.nullIfBlank()?.let { append(":").append(Utils.encodeURIComponent(it)) }
        }

        val url = String.format(
            "%s@%s:%s",
            userInfo,
            Utils.getIpv6Address(HttpUtil.toIdnDomain(config.server.orEmpty())),
            config.serverPort
        )

        return "${url}${query}#${Utils.encodeURIComponent(config.remarks)}"
    }
}
