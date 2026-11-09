package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormTextField

/**
 * SSH server editor. The SSH server is a first-class Xray outbound
 * (proxy/ssh in the forked core): one persistent SSH client, one
 * direct-tcpip channel per connection. The DNSTT (SlowDNS) fields
 * below are always shown — an SSH server is usually reached through
 * the DNSTT tunnel, which the core now dials in-process; leaving
 * them blank dials the SSH server directly.
 */
class ServerSshActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.SSH

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
            DnsttFields(uiState, showToggle = false)
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
}
