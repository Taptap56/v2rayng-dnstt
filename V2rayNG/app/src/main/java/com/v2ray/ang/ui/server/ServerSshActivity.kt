package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.SettingsSwitchItem

/**
 * SSH server editor. The SSH server is a first-class Xray outbound
 * (proxy/ssh in the forked core): one persistent SSH client, one
 * direct-tcpip channel per connection. The transport is built from
 * HTTP Custom style method layers that can be combined (Payload,
 * Enhanced, TLS, SlowDNS — all four can be on at once, like HTTP
 * Custom): a remote HTTP CONNECT proxy as the first hop, a custom
 * payload, an enhanced payload (payload + read the server's HTTP
 * response headers), a TLS handshake with SNI, and the DNSTT
 * (SlowDNS) dial. No layer on means a direct TCP dial. The DNSTT
 * fields are shown only for the SlowDNS layer.
 */
class ServerSshActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.SSH

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        // Layer requirements (the core enforces the same).
        if (config.sshEffectiveUsePayload() || config.sshEffectiveUseEnhanced()) {
            if (config.sshPayload.isNullOrBlank()) return false
        }
        if (config.sshEffectiveUseSlowdns()) {
            if (config.dnsttNS.isNullOrBlank() || config.dnsttPubkey.isNullOrBlank()) return false
        }
        if (!isValidRemoteProxy(config.sshRemoteProxy.orEmpty())) return false
        return true
    }

    @Composable
    override fun ScreenContent() {
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            ).apply {
                configType = serverConfigType
                // New SSH profiles default to port 22, not 443.
                if (initialConfig.serverPort == null) {
                    port = AppConfig.DEFAULT_SSH_PORT.toString()
                }
            }
        }.apply {
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            SshProtocolFields(uiState)
            SshMethodFields(uiState)
        }
    }

    @Composable
    private fun SshProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_ssh_username),
            state.username,
            { state.username = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_ssh_password),
            state.password,
            { state.password = it },
            isError = state.isPasswordError
        )
        FormTextField(
            stringResource(R.string.server_lab_ssh_private_key),
            state.sshPrivateKey,
            { state.sshPrivateKey = it }
        )
    }

    @Composable
    private fun SshMethodFields(state: ServerUiState) {
        // HC-style multi-select chips: the four methods are
        // independent layers, several may be on at once. The labels
        // are protocol names, so they are not localized.
        Text(
            text = stringResource(R.string.server_lab_ssh_method),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.sshUsePayload,
                onClick = { state.sshUsePayload = !state.sshUsePayload },
                label = { Text("Payload") }
            )
            FilterChip(
                selected = state.sshUseEnhanced,
                onClick = { state.sshUseEnhanced = !state.sshUseEnhanced },
                label = { Text("Enhanced") }
            )
            FilterChip(
                selected = state.sshUseTls,
                onClick = { state.sshUseTls = !state.sshUseTls },
                label = { Text("TLS") }
            )
            FilterChip(
                selected = state.sshUseSlowdns,
                onClick = { state.sshUseSlowdns = !state.sshUseSlowdns },
                label = { Text("SlowDNS") }
            )
        }
        val showPayloadSection = state.sshUsePayload || state.sshUseEnhanced
        if (showPayloadSection) {
            FormTextField(
                stringResource(R.string.server_lab_ssh_payload),
                state.sshPayload,
                { state.sshPayload = it },
                supportingText = stringResource(R.string.server_hint_ssh_payload),
            )
            FormTextField(
                stringResource(R.string.server_lab_ssh_remote_proxy),
                state.sshRemoteProxy,
                { state.sshRemoteProxy = it },
                placeholder = stringResource(R.string.server_hint_ssh_remote_proxy),
            )
            // SNI fields live in the payload section when TLS is
            // also on (like HTTP Custom).
            if (state.sshUseTls) {
                SshTlsFields(state)
            }
        } else if (state.sshUseTls) {
            // TLS on without Payload: its own section.
            SshTlsFields(state)
        }
        if (state.sshUseSlowdns) {
            DnsttFields(state, showToggle = false)
        }
        // No layer on: direct TCP, no extra fields.
    }

    @Composable
    private fun SshTlsFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_ssh_sni),
            state.sshSni,
            { state.sshSni = it }
        )
        FormDropdownField(
            stringResource(R.string.server_lab_ssh_sni_version),
            sshSniVersionLabel(state.sshSniVersion),
            listOf("Default", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"),
            { state.sshSniVersion = sshSniVersionValue(it) }
        )
        SettingsSwitchItem(
            title = stringResource(R.string.server_lab_ssh_insecure),
            summary = stringResource(R.string.server_desc_ssh_insecure),
            checked = state.sshInsecure,
            onCheckedChange = { state.sshInsecure = it }
        )
    }
}

/**
 * True when the remote proxy value is a valid
 * "[user:pass@]host:port" (IPv6 bracket form allowed). Blank is
 * valid (no proxy). Mirrors the core's parseRemoteProxy.
 */
private fun isValidRemoteProxy(value: String): Boolean {
    val s = value.trim()
    if (s.isEmpty()) return true
    val hostPort = s.substringAfterLast('@', s)
    val (host, portStr) = if (hostPort.startsWith("[")) {
        val close = hostPort.indexOf(']')
        if (close < 0) return false
        if (close + 1 >= hostPort.length || hostPort[close + 1] != ':') return false
        hostPort.substring(1, close) to hostPort.substring(close + 2)
    } else {
        // Unbracketed: exactly one colon (an unbracketed IPv6
        // address is ambiguous here, like in the core).
        if (hostPort.count { it == ':' } != 1) return false
        val colon = hostPort.indexOf(':')
        if (colon <= 0) return false
        hostPort.substring(0, colon) to hostPort.substring(colon + 1)
    }
    if (host.isEmpty()) return false
    val port = portStr.toIntOrNull() ?: return false
    return port in 1..65535
}

/** SNI version dropdown labels ("Default" = auto, no forced version). */
private fun sshSniVersionLabel(value: String): String = when (value) {
    AppConfig.SSH_SNI_VERSION_TLS10 -> "TLSv1"
    AppConfig.SSH_SNI_VERSION_TLS11 -> "TLSv1.1"
    AppConfig.SSH_SNI_VERSION_TLS12 -> "TLSv1.2"
    AppConfig.SSH_SNI_VERSION_TLS13 -> "TLSv1.3"
    else -> "Default"
}

private fun sshSniVersionValue(label: String): String = when (label) {
    "TLSv1" -> AppConfig.SSH_SNI_VERSION_TLS10
    "TLSv1.1" -> AppConfig.SSH_SNI_VERSION_TLS11
    "TLSv1.2" -> AppConfig.SSH_SNI_VERSION_TLS12
    "TLSv1.3" -> AppConfig.SSH_SNI_VERSION_TLS13
    else -> AppConfig.SSH_SNI_VERSION_DEFAULT
}
