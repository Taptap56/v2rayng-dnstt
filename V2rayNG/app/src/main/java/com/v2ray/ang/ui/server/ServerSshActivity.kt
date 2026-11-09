package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
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
 * direct-tcpip channel per connection. The transport method (HTTP
 * Custom style) selects how the server is reached: direct TCP,
 * a custom payload, WebSocket, WebSocket over TLS, direct TLS with
 * SNI only, or SlowDNS (DNSTT). The DNSTT fields are shown only for
 * the SlowDNS method; picking another method dials the SSH server
 * without the tunnel.
 */
class ServerSshActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.SSH

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        // Method-specific requirements (the core enforces the same).
        return when (config.sshMethod) {
            AppConfig.SSH_METHOD_PAYLOAD -> !config.sshPayload.isNullOrBlank()
            AppConfig.SSH_METHOD_SLOWDNS -> !config.dnsttNS.isNullOrBlank() && !config.dnsttPubkey.isNullOrBlank()
            else -> true
        }
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
        FormDropdownField(
            stringResource(R.string.server_lab_ssh_method),
            sshMethodLabel(state.sshMethod),
            listOf("Direct", "Payload", "WS", "WS+TLS", "TLS", "SlowDNS"),
            { state.sshMethod = sshMethodValue(it) }
        )
        when (state.sshMethod) {
            AppConfig.SSH_METHOD_PAYLOAD -> {
                FormTextField(
                    stringResource(R.string.server_lab_ssh_payload),
                    state.sshPayload,
                    { state.sshPayload = it },
                    supportingText = stringResource(R.string.server_hint_ssh_payload),
                )
            }
            AppConfig.SSH_METHOD_WS -> {
                FormTextField(
                    stringResource(R.string.server_lab_ssh_ws_path),
                    state.sshWsPath,
                    { state.sshWsPath = it },
                    placeholder = stringResource(R.string.server_hint_ssh_ws_path),
                )
                FormTextField(
                    stringResource(R.string.server_lab_ssh_ws_host),
                    state.sshWsHost,
                    { state.sshWsHost = it }
                )
            }
            AppConfig.SSH_METHOD_WS_TLS -> {
                FormTextField(
                    stringResource(R.string.server_lab_ssh_ws_path),
                    state.sshWsPath,
                    { state.sshWsPath = it },
                    placeholder = stringResource(R.string.server_hint_ssh_ws_path),
                )
                FormTextField(
                    stringResource(R.string.server_lab_ssh_ws_host),
                    state.sshWsHost,
                    { state.sshWsHost = it }
                )
                SshTlsFields(state)
            }
            AppConfig.SSH_METHOD_TLS -> {
                SshTlsFields(state)
            }
            AppConfig.SSH_METHOD_SLOWDNS -> {
                DnsttFields(state, showToggle = false)
            }
            // Direct: no extra fields.
            else -> Unit
        }
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
 * Method dropdown labels. The labels are protocol names (like the
 * DNSTT record-type dropdown's TXT/NULL), so they are not localized;
 * the stored values are the AppConfig constants ("" for Direct).
 */
private fun sshMethodLabel(value: String): String = when (value) {
    AppConfig.SSH_METHOD_PAYLOAD -> "Payload"
    AppConfig.SSH_METHOD_WS -> "WS"
    AppConfig.SSH_METHOD_WS_TLS -> "WS+TLS"
    AppConfig.SSH_METHOD_TLS -> "TLS"
    AppConfig.SSH_METHOD_SLOWDNS -> "SlowDNS"
    else -> "Direct"
}

private fun sshMethodValue(label: String): String = when (label) {
    "Payload" -> AppConfig.SSH_METHOD_PAYLOAD
    "WS" -> AppConfig.SSH_METHOD_WS
    "WS+TLS" -> AppConfig.SSH_METHOD_WS_TLS
    "TLS" -> AppConfig.SSH_METHOD_TLS
    "SlowDNS" -> AppConfig.SSH_METHOD_SLOWDNS
    else -> ""
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
