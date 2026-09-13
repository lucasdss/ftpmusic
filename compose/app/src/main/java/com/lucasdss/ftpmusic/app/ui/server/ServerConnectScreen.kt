package com.lucasdss.ftpmusic.app.ui.server

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasdss.ftpmusic.app.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectScreen(onConnected: () -> Unit = {}, viewModel: ServerConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.connected) {
        if (state.connected) onConnected()
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Connect to Navidrome", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::onUrlChanged,
                label = { Text("Server URL") },
                placeholder = { Text("https://music.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isCleartextServerUrl(state.serverUrl)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "HTTP sends your music-server credentials without transport encryption. Use only on a trusted private network.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.connect(onConnected) },
                enabled = !state.isConnecting &&
                    state.serverUrl.isNotBlank() &&
                    state.username.isNotBlank() &&
                    state.password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(Modifier.size(iconSmall()), strokeWidth = 2.dp)
                } else {
                    Text("Connect")
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
