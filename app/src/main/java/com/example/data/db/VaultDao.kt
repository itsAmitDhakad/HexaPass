package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    // Metadata
    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    fun getMetadata(): Flow<VaultMetadataEntity?>

    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun getMetadataDirect(): VaultMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: VaultMetadataEntity)

    @Query("UPDATE vault_metadata SET autoLockMinutes = :minutes WHERE id = 1")
    suspend fun updateAutoLock(minutes: Int)

    @Query("UPDATE vault_metadata SET clipboardClearSeconds = :seconds WHERE id = 1")
    suspend fun updateClipboardTimeout(seconds: Int)

    // Passwords
    @Query("SELECT * FROM password_entries ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllPasswords(): Flow<List<PasswordEntryEntity>>

    @Query("SELECT * FROM password_entries ORDER BY updatedAt DESC")
    suspend fun getAllPasswordsDirect(): List<PasswordEntryEntity>

    @Query("SELECT COUNT(*) FROM password_entries")
    suspend fun getPasswordCount(): Int

    @Query("SELECT * FROM password_entries WHERE id = :id LIMIT 1")
    suspend fun getPasswordById(id: Long): PasswordEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(entry: PasswordEntryEntity): Long

    @Update
    suspend fun updatePassword(entry: PasswordEntryEntity)

    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deletePasswordById(id: Long)

    @Query("UPDATE password_entries SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setPasswordFavorite(id: Long, isFavorite: Boolean)

    // Links
    @Query("SELECT * FROM link_entries ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllLinks(): Flow<List<LinkEntryEntity>>

    @Query("SELECT * FROM link_entries ORDER BY updatedAt DESC")
    suspend fun getAllLinksDirect(): List<LinkEntryEntity>

    @Query("SELECT COUNT(*) FROM link_entries")
    suspend fun getLinkCount(): Int

    @Query("SELECT * FROM link_entries WHERE id = :id LIMIT 1")
    suspend fun getLinkById(id: Long): LinkEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(entry: LinkEntryEntity): Long

    @Update
    suspend fun updateLink(entry: LinkEntryEntity)

    @Query("DELETE FROM link_entries WHERE id = :id")
    suspend fun deleteLinkById(id: Long)

    @Query("UPDATE link_entries SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setLinkFavorite(id: Long, isFavorite: Boolean)

    // Reset Vault
    @Query("DELETE FROM password_entries")
    suspend fun clearAllPasswords()

    @Query("DELETE FROM link_entries")
    suspend fun clearAllLinks()

    @Query("DELETE FROM vault_metadata")
    suspend fun clearMetadata()

    @androidx.room.Transaction
    suspend fun insertImportedVaultData(passwords: List<PasswordEntryEntity>, links: List<LinkEntryEntity>) {
        for (p in passwords) {
            insertPassword(p)
        }
        for (l in links) {
            insertLink(l)
        }
    }

    @androidx.room.Transaction
    suspend fun rotateMasterPassword(
        passwords: List<PasswordEntryEntity>,
        links: List<LinkEntryEntity>,
        metadata: VaultMetadataEntity
    ) {
        for (p in passwords) {
            updatePassword(p)
        }
        for (l in links) {
            updateLink(l)
        }
        setMetadata(metadata)
    }
}
