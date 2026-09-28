package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * SEC-LOW-02: External URL Security Manager
 *
 * Provides safe validation, sanitization, and launching of external web URLs stored in HexaPass.
 *
 * Security guarantees:
 * 1. Allowed schemes: strictly "https" and "http" (prepends "https://" to schemeless web domains).
 * 2. Unsupported schemes (javascript:, file:, content:, intent:, data:, etc.) are rejected safely.
 * 3. Malformed URLs (blank, missing/invalid host, invalid port, illegal characters) are rejected safely.
 * 4. Strips embedded userinfo (username/password credentials in URLs like https://user:pass@host/)
 *    to prevent credential leakage to external browsers and network logs.
 * 5. Preserves legitimate web features: host, port, path, query parameters, and fragments.
 * 6. Adds Intent.CATEGORY_BROWSABLE to restrict intent resolution to browser activities.
 * 7. Zero logging: never logs URLs, query parameters, fragments, userinfo, or error details.
 */
object UrlSecurityManager {

    private val ALLOWED_SCHEMES = setOf("https", "http")

    /**
     * Sanitizes and validates a candidate URL for external browser launch.
     *
     * @param rawUrl The raw URL string from user input or decrypted vault entry.
     * @return A sanitized Uri safe for external viewing, or null if rejected/malformed.
     */
    fun sanitizeUrl(rawUrl: String?): Uri? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null

        val candidateUri: Uri = try {
            val parsed = Uri.parse(trimmed)
            val scheme = parsed.scheme
            if (scheme != null) {
                val lowerScheme = scheme.lowercase()
                if (lowerScheme in ALLOWED_SCHEMES) {
                    parsed
                } else {
                    // Check if this is a schemeless host:port like "example.com:8080"
                    // where Uri.parse mistook "example.com" as a scheme.
                    // In a host:port, after the first colon must be digits (1-5 chars) followed by end, '/', '?', or '#'
                    val afterColon = trimmed.substring(scheme.length + 1)
                    val portDigits = afterColon.takeWhile { it.isDigit() }
                    if (portDigits.isNotEmpty() && portDigits.length <= 5 &&
                        (afterColon.length == portDigits.length || afterColon[portDigits.length] in charArrayOf('/', '?', '#'))
                    ) {
                        Uri.parse("https://$trimmed")
                    } else {
                        // Explicit unsupported scheme (e.g. javascript:, file:, content:, intent:, data:)
                        return null
                    }
                }
            } else {
                // No scheme present: verify there is no hidden scheme like "javascript:..."
                val firstColon = trimmed.indexOf(':')
                val firstSlash = trimmed.indexOf('/')
                val firstQuestion = trimmed.indexOf('?')
                val firstHash = trimmed.indexOf('#')

                val firstDelimiter = listOf(firstSlash, firstQuestion, firstHash)
                    .filter { it != -1 }
                    .minOrNull() ?: -1

                if (firstColon != -1 && (firstDelimiter == -1 || firstColon < firstDelimiter)) {
                    val afterColon = trimmed.substring(firstColon + 1)
                    val portDigits = afterColon.takeWhile { it.isDigit() }
                    if (portDigits.isEmpty() || portDigits.length > 5 ||
                        (afterColon.length > portDigits.length && afterColon[portDigits.length] !in charArrayOf('/', '?', '#'))
                    ) {
                        // Looks like an unsupported scheme
                        return null
                    }
                }

                // Normal schemeless web URL (e.g. "example.com/path") -> default to HTTPS
                Uri.parse("https://$trimmed")
            }
        } catch (_: Exception) {
            return null
        }

        // Validate scheme
        val finalScheme = candidateUri.scheme?.lowercase() ?: return null
        if (finalScheme !in ALLOWED_SCHEMES) {
            return null
        }

        // Validate host
        val host = candidateUri.host
        if (host.isNullOrBlank()) {
            return null
        }

        // Host must not contain whitespace or control characters
        if (host.any { it.isWhitespace() || it.isISOControl() }) {
            return null
        }

        // Validate port if present
        val port = candidateUri.port
        if (port != -1 && (port < 1 || port > 65535)) {
            return null
        }

        // Reconstruct URI with userinfo (credentials) completely stripped
        return try {
            val authority = if (port != -1) "$host:$port" else host
            Uri.Builder()
                .scheme(finalScheme)
                .encodedAuthority(authority)
                .encodedPath(candidateUri.encodedPath)
                .encodedQuery(candidateUri.encodedQuery)
                .encodedFragment(candidateUri.encodedFragment)
                .build()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Creates a safe Intent for launching an external browser with the sanitized URL.
     * Restricts the intent to browsable activities.
     *
     * @param rawUrl The URL string to sanitize and wrap in an intent.
     * @return Intent ready to be passed to context.startActivity(), or null if invalid.
     */
    fun createSafeViewIntent(rawUrl: String?): Intent? {
        val safeUri = sanitizeUrl(rawUrl) ?: return null
        return Intent(Intent.ACTION_VIEW, safeUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Sanitizes the given URL and launches it via the system browser.
     * Never leaks the URL or exceptions to system logs.
     *
     * @param context The Android context.
     * @param rawUrl The raw URL string.
     * @return true if successfully launched, false otherwise.
     */
    fun launchSafeUrl(context: Context, rawUrl: String?): Boolean {
        return try {
            val intent = createSafeViewIntent(rawUrl) ?: return false
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
