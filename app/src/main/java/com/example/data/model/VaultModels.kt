package com.example.data.model

import com.example.data.crypto.PasswordStrength

data class DecryptedPasswordItem(
    val id: Long,
    val websiteName: String,
    val url: String,
    val username: String,
    val password: String,
    val notes: String,
    val category: String,
    val tags: List<String>,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val strength: PasswordStrength
)

data class LinkItem(
    val id: Long,
    val name: String,
    val url: String,
    val description: String,
    val category: String,
    val tags: List<String>,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class SecurityReport(
    val overallScore: Int,
    val totalPasswords: Int,
    val strongCount: Int,
    val fairCount: Int,
    val weakCount: Int,
    val reusedCount: Int,
    val weakItems: List<DecryptedPasswordItem>,
    val reusedGroups: Map<String, List<DecryptedPasswordItem>>,
    val recommendations: List<String>
)

enum class VaultScreen(val title: String) {
    DASHBOARD("Dashboard"),
    PASSWORDS("Passwords"),
    LINKS("Links"),
    GENERATOR("Generator"),
    SECURITY("Security"),
    SETTINGS("Settings")
}

val PASSWORD_CATEGORIES = listOf(
    "All",
    "Login",
    "Finance",
    "Social",
    "Work",
    "Personal",
    "Entertainment",
    "Other"
)

val LINK_CATEGORIES = listOf(
    "All",
    "Work",
    "Personal",
    "Research",
    "Finance",
    "Dev",
    "Tools",
    "Other"
)
