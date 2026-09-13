package com.lucasdss.ftpmusic.app.ui.server

import java.net.URI

data class ServerConnectionInput(
    val url: String,
    val username: String,
    val password: String,
    val usesCleartext: Boolean,
)

fun validateServerConnectionInput(
    rawUrl: String,
    rawUsername: String,
    rawPassword: String,
): Result<ServerConnectionInput> {
    val url = rawUrl.trim().trimEnd('/')
    val username = rawUsername.trim()
    if (url.isEmpty()) return Result.failure(IllegalArgumentException("Server URL is required"))
    if (username.isEmpty()) return Result.failure(IllegalArgumentException("Username is required"))
    if (rawPassword.isEmpty()) return Result.failure(IllegalArgumentException("Password is required"))

    val uri = try {
        URI(url)
    } catch (_: Exception) {
        return Result.failure(IllegalArgumentException("Enter a valid server URL"))
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") {
        return Result.failure(IllegalArgumentException("Server URL must use HTTPS or HTTP"))
    }
    val host = uri.host
        ?: return Result.failure(IllegalArgumentException("Enter a complete server URL"))
    if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
        return Result.failure(IllegalArgumentException("Server URL must not contain credentials, query, or fragment"))
    }
    if (scheme == "http" && !isPrivateServerHost(host)) {
        return Result.failure(
            IllegalArgumentException("HTTP is allowed only for private local-network servers"),
        )
    }
    return Result.success(
        ServerConnectionInput(
            url = url,
            username = username,
            password = rawPassword,
            usesCleartext = scheme == "http",
        ),
    )
}

fun isCleartextServerUrl(rawUrl: String): Boolean =
    runCatching { URI(rawUrl.trim()).scheme.equals("http", ignoreCase = true) }.getOrDefault(false)

internal fun isPrivateServerHost(rawHost: String): Boolean {
    val host = rawHost.lowercase().removePrefix("[").removeSuffix("]")
    if (host == "localhost" || host.endsWith(".local")) return true
    if (host == "::1" ||
        (
            host.contains(':') &&
                (
                    host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe8") ||
                        host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")
                    )
            )
    ) {
        return true
    }

    val octets = host.split('.').map { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return false
    val first = octets[0]!!
    val second = octets[1]!!
    return (first == 10) ||
        (first == 127) ||
        (first == 192 && second == 168) ||
        (first == 172 && second in 16..31) ||
        (first == 169 && second == 254)
}
