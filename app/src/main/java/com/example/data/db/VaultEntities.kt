package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_metadata")
data class VaultMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val saltBase64: String,
    val verifierCipherBase64: String,
    val autoLockMinutes: Int = 5,
    val clipboardClearSeconds: Int = 30,
    val isConfigured: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "password_entries")
data class PasswordEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val websiteName: String,
    val url: String,
    val category: String,
    val tags: String = "",
    val isFavorite: Boolean = false,
    val encryptedUsername: String,
    val encryptedPassword: String,
    val encryptedNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "link_entries")
data class LinkEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val description: String = "",
    val category: String = "General",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
