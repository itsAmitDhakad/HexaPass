package com.example.data.repository

import android.util.Base64
import com.example.data.crypto.PasswordStrength
import com.example.data.crypto.StrengthLevel
import com.example.data.crypto.VaultCrypto
import com.example.data.db.LinkEntryEntity
import com.example.data.db.PasswordEntryEntity
import com.example.data.db.VaultDao
import com.example.data.db.VaultMetadataEntity
import com.example.data.model.DecryptedPasswordItem
import com.example.data.model.LinkItem
import com.example.data.model.SecurityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class VaultRepository(private val vaultDao: VaultDao) {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked = _isUnlocked.asStateFlow()

    private var activeKey: SecretKey? = null

    val metadataFlow: Flow<VaultMetadataEntity?> = vaultDao.getMetadata()

    suspend fun isVaultConfigured(): Boolean = withContext(Dispatchers.IO) {
        val meta = vaultDao.getMetadataDirect()
        meta != null && meta.isConfigured
    }

    suspend fun setupMasterPassword(password: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val salt = VaultCrypto.generateRandomSalt()
            val key = VaultCrypto.deriveKey(password, salt)
            val verifierCipher = VaultCrypto.encrypt(VaultCrypto.VERIFICATION_SENTINEL, key)

            val meta = VaultMetadataEntity(
                id = 1,
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                verifierCipherBase64 = verifierCipher,
                autoLockMinutes = 5,
                clipboardClearSeconds = 30,
                isConfigured = true
            )
            vaultDao.setMetadata(meta)
            activeKey = key
            _isUnlocked.value = true
            migratePlaintextMetadataIfNeeded(key)
            true
        } catch (_: Exception) {
            false
        } finally {
            VaultCrypto.wipeMemory(password)
        }
    }

    suspend fun unlockVault(password: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val meta = vaultDao.getMetadataDirect() ?: return@withContext false
            val key = VaultCrypto.verifyMasterPassword(
                password,
                meta.saltBase64,
                meta.verifierCipherBase64
            )
            if (key != null) {
                activeKey = key
                _isUnlocked.value = true
                migratePlaintextMetadataIfNeeded(key)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            VaultCrypto.wipeMemory(password)
        }
    }

    fun getActiveKey(): SecretKey? = activeKey

    suspend fun unlockWithVaultKey(key: SecretKey): Boolean = withContext(Dispatchers.IO) {
        try {
            val meta = vaultDao.getMetadataDirect() ?: return@withContext false
            val decryptedSentinel = VaultCrypto.decrypt(meta.verifierCipherBase64, key)
            if (decryptedSentinel == VaultCrypto.VERIFICATION_SENTINEL) {
                activeKey = key
                _isUnlocked.value = true
                migratePlaintextMetadataIfNeeded(key)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isCiphertext(value: String, key: SecretKey): Boolean {
        if (value.isBlank()) return false
        return try {
            VaultCrypto.decrypt(value, key)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun migratePlaintextMetadataIfNeeded(key: SecretKey): Int = withContext(Dispatchers.IO) {
        var migratedCount = 0
        try {
            // 1. Check and migrate passwords metadata
            val passwords = vaultDao.getAllPasswordsDirect()
            for (entry in passwords) {
                var changed = false
                var newWebsiteName = entry.websiteName
                var newUrl = entry.url
                var newCategory = entry.category
                var newTags = entry.tags

                if (!isCiphertext(entry.websiteName, key)) {
                    newWebsiteName = VaultCrypto.encrypt(entry.websiteName, key)
                    changed = true
                }
                if (!isCiphertext(entry.url, key)) {
                    newUrl = VaultCrypto.encrypt(entry.url, key)
                    changed = true
                }
                if (!isCiphertext(entry.category, key)) {
                    newCategory = VaultCrypto.encrypt(entry.category, key)
                    changed = true
                }
                if (!isCiphertext(entry.tags, key)) {
                    newTags = VaultCrypto.encrypt(entry.tags, key)
                    changed = true
                }

                if (changed) {
                    vaultDao.updatePassword(
                        entry.copy(
                            websiteName = newWebsiteName,
                            url = newUrl,
                            category = newCategory,
                            tags = newTags,
                            updatedAt = entry.updatedAt
                        )
                    )
                    migratedCount++
                }
            }

            // 2. Check and migrate links metadata
            val links = vaultDao.getAllLinksDirect()
            for (entry in links) {
                var changed = false
                var newName = entry.name
                var newUrl = entry.url
                var newDesc = entry.description
                var newCategory = entry.category
                var newTags = entry.tags

                if (!isCiphertext(entry.name, key)) {
                    newName = VaultCrypto.encrypt(entry.name, key)
                    changed = true
                }
                if (!isCiphertext(entry.category, key)) {
                    newCategory = VaultCrypto.encrypt(entry.category, key)
                    changed = true
                }
                if (!isCiphertext(entry.url, key)) {
                    newUrl = VaultCrypto.encrypt(entry.url, key)
                    changed = true
                }
                if (entry.description.isNotBlank() && !isCiphertext(entry.description, key)) {
                    newDesc = VaultCrypto.encrypt(entry.description, key)
                    changed = true
                }
                if (entry.tags.isNotBlank() && !isCiphertext(entry.tags, key)) {
                    newTags = VaultCrypto.encrypt(entry.tags, key)
                    changed = true
                }

                if (changed) {
                    vaultDao.updateLink(
                        entry.copy(
                            name = newName,
                            url = newUrl,
                            description = newDesc,
                            category = newCategory,
                            tags = newTags,
                            updatedAt = entry.updatedAt
                        )
                    )
                    migratedCount++
                }
            }
        } catch (_: Exception) {
            // Error during migration: fail safely without crashing or leaking details
        }
        migratedCount
    }

    private fun safeDecrypt(payload: String, key: SecretKey): String {
        if (payload.isBlank()) return ""
        return try {
            VaultCrypto.decrypt(payload, key)
        } catch (e: Exception) {
            // Fallback to payload directly if it's a legacy unencrypted string
            payload
        }
    }

    fun lockVault() {
        activeKey = null
        _isUnlocked.value = false
    }

    suspend fun changeMasterPassword(oldPassword: CharArray, newPassword: CharArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val meta = vaultDao.getMetadataDirect() ?: return@withContext false
                val verifiedOldKey = VaultCrypto.verifyMasterPassword(
                    oldPassword,
                    meta.saltBase64,
                    meta.verifierCipherBase64
                ) ?: return@withContext false

                // Decrypt all existing password entries with old key
                val oldEntries = vaultDao.getAllPasswordsDirect()
                val decryptedList = mutableListOf<DecryptedPasswordItem>()
                for (entry in oldEntries) {
                    val websiteName = safeDecrypt(entry.websiteName, verifiedOldKey)
                    val url = safeDecrypt(entry.url, verifiedOldKey)
                    val category = safeDecrypt(entry.category, verifiedOldKey)
                    val tags = safeDecrypt(entry.tags, verifiedOldKey)
                    val user = VaultCrypto.decrypt(entry.encryptedUsername, verifiedOldKey)
                    val pass = VaultCrypto.decrypt(entry.encryptedPassword, verifiedOldKey)
                    val notes = if (entry.encryptedNotes.isNotEmpty()) {
                        VaultCrypto.decrypt(entry.encryptedNotes, verifiedOldKey)
                    } else ""
                    decryptedList.add(
                        DecryptedPasswordItem(
                            id = entry.id,
                            websiteName = websiteName,
                            url = url,
                            username = user,
                            password = pass,
                            notes = notes,
                            category = category,
                            tags = tags.split(",").filter { it.isNotBlank() },
                            isFavorite = entry.isFavorite,
                            createdAt = entry.createdAt,
                            updatedAt = entry.updatedAt,
                            strength = VaultCrypto.evaluateStrength(pass)
                        )
                    )
                }

                // Decrypt all existing link entries with old key
                val oldLinks = vaultDao.getAllLinksDirect()
                val decryptedLinks = oldLinks.map { entity ->
                    entity.copy(
                        name = safeDecrypt(entity.name, verifiedOldKey),
                        url = safeDecrypt(entity.url, verifiedOldKey),
                        description = if (entity.description.isNotBlank()) safeDecrypt(entity.description, verifiedOldKey) else "",
                        category = safeDecrypt(entity.category, verifiedOldKey),
                        tags = if (entity.tags.isNotBlank()) safeDecrypt(entity.tags, verifiedOldKey) else ""
                    )
                }

                // Generate new salt and derive new key
                val newSalt = VaultCrypto.generateRandomSalt()
                val newKey = VaultCrypto.deriveKey(newPassword, newSalt)
                val newVerifier = VaultCrypto.encrypt(VaultCrypto.VERIFICATION_SENTINEL, newKey)

                // Re-encrypt all password items with new key in memory
                val reEncryptedPasswords = decryptedList.map { item ->
                    val encWebsiteName = VaultCrypto.encrypt(item.websiteName, newKey)
                    val encUrl = VaultCrypto.encrypt(item.url, newKey)
                    val encCategory = VaultCrypto.encrypt(item.category, newKey)
                    val encTags = VaultCrypto.encrypt(item.tags.joinToString(","), newKey)
                    val encUser = VaultCrypto.encrypt(item.username, newKey)
                    val encPass = VaultCrypto.encrypt(item.password, newKey)
                    val encNotes = if (item.notes.isNotEmpty()) VaultCrypto.encrypt(item.notes, newKey) else ""
                    PasswordEntryEntity(
                        id = item.id,
                        websiteName = encWebsiteName,
                        url = encUrl,
                        category = encCategory,
                        tags = encTags,
                        isFavorite = item.isFavorite,
                        encryptedUsername = encUser,
                        encryptedPassword = encPass,
                        encryptedNotes = encNotes,
                        createdAt = item.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                }

                // Re-encrypt all link items with new key in memory
                val reEncryptedLinks = decryptedLinks.map { link ->
                    val reEncName = VaultCrypto.encrypt(link.name, newKey)
                    val reEncUrl = VaultCrypto.encrypt(link.url, newKey)
                    val reEncDesc = if (link.description.isNotBlank()) VaultCrypto.encrypt(link.description, newKey) else ""
                    val reEncCat = VaultCrypto.encrypt(link.category, newKey)
                    val reEncTags = if (link.tags.isNotBlank()) VaultCrypto.encrypt(link.tags, newKey) else ""
                    link.copy(
                        name = reEncName,
                        url = reEncUrl,
                        description = reEncDesc,
                        category = reEncCat,
                        tags = reEncTags,
                        updatedAt = System.currentTimeMillis()
                    )
                }

                // Update metadata in memory
                val updatedMeta = meta.copy(
                    saltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP),
                    verifierCipherBase64 = newVerifier
                )

                // Atomically commit all re-encrypted records and metadata in a single Room transaction
                vaultDao.rotateMasterPassword(reEncryptedPasswords, reEncryptedLinks, updatedMeta)

                // Only activate the new key after the transaction has successfully committed
                activeKey = newKey
                true
            } catch (e: Exception) {
                // If anything fails, transaction rolls back and activeKey remains the old key
                false
            } finally {
                VaultCrypto.wipeMemory(oldPassword)
                VaultCrypto.wipeMemory(newPassword)
            }
        }

    fun observeDecryptedPasswords(): Flow<List<DecryptedPasswordItem>> {
        return combine(vaultDao.getAllPasswords(), _isUnlocked) { entities, unlocked ->
            val key = activeKey
            if (!unlocked || key == null) {
                emptyList()
            } else {
                entities.mapNotNull { entity ->
                    try {
                        val websiteName = safeDecrypt(entity.websiteName, key)
                        val url = safeDecrypt(entity.url, key)
                        val category = safeDecrypt(entity.category, key)
                        val tags = safeDecrypt(entity.tags, key)
                        val user = VaultCrypto.decrypt(entity.encryptedUsername, key)
                        val pass = VaultCrypto.decrypt(entity.encryptedPassword, key)
                        val notes = if (entity.encryptedNotes.isNotBlank()) {
                            safeDecrypt(entity.encryptedNotes, key)
                        } else ""
                        val strength = VaultCrypto.evaluateStrength(pass)
                        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        DecryptedPasswordItem(
                            id = entity.id,
                            websiteName = websiteName,
                            url = url,
                            username = user,
                            password = pass,
                            notes = notes,
                            category = category,
                            tags = tagList,
                            isFavorite = entity.isFavorite,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt,
                            strength = strength
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun getPasswordCount(): Int = withContext(Dispatchers.IO) {
        vaultDao.getPasswordCount()
    }

    suspend fun getLinkCount(): Int = withContext(Dispatchers.IO) {
        vaultDao.getLinkCount()
    }

    suspend fun savePassword(
        id: Long = 0,
        websiteName: String,
        url: String,
        username: String,
        password: String,
        notes: String,
        category: String,
        tags: List<String>,
        isFavorite: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val key = activeKey ?: return@withContext false
        if (id == 0L && vaultDao.getPasswordCount() >= 10) {
            return@withContext false
        }
        try {
            val encWebsiteName = VaultCrypto.encrypt(websiteName, key)
            val encUrl = VaultCrypto.encrypt(url, key)
            val encCategory = VaultCrypto.encrypt(category, key)
            val tagsString = tags.joinToString(",")
            val encTags = VaultCrypto.encrypt(tagsString, key)
            val encUsername = VaultCrypto.encrypt(username, key)
            val encPassword = VaultCrypto.encrypt(password, key)
            val encNotes = if (notes.isNotBlank()) VaultCrypto.encrypt(notes, key) else ""

            val now = System.currentTimeMillis()
            if (id == 0L) {
                val entity = PasswordEntryEntity(
                    websiteName = encWebsiteName,
                    url = encUrl,
                    category = encCategory,
                    tags = encTags,
                    isFavorite = isFavorite,
                    encryptedUsername = encUsername,
                    encryptedPassword = encPassword,
                    encryptedNotes = encNotes,
                    createdAt = now,
                    updatedAt = now
                )
                vaultDao.insertPassword(entity)
            } else {
                val existing = vaultDao.getPasswordById(id)
                val createdTime = existing?.createdAt ?: now
                val entity = PasswordEntryEntity(
                    id = id,
                    websiteName = encWebsiteName,
                    url = encUrl,
                    category = encCategory,
                    tags = encTags,
                    isFavorite = isFavorite,
                    encryptedUsername = encUsername,
                    encryptedPassword = encPassword,
                    encryptedNotes = encNotes,
                    createdAt = createdTime,
                    updatedAt = now
                )
                vaultDao.updatePassword(entity)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deletePassword(id: Long) = withContext(Dispatchers.IO) {
        vaultDao.deletePasswordById(id)
    }

    suspend fun togglePasswordFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        vaultDao.setPasswordFavorite(id, isFavorite)
    }

    // Links
    fun observeLinks(): Flow<List<LinkItem>> {
        return combine(vaultDao.getAllLinks(), _isUnlocked) { entities, unlocked ->
            val key = activeKey
            if (!unlocked || key == null) {
                emptyList()
            } else {
                entities.mapNotNull { entity ->
                    try {
                        val decryptedName = safeDecrypt(entity.name, key)
                        val decryptedUrl = safeDecrypt(entity.url, key)
                        val decryptedDesc = if (entity.description.isNotBlank()) safeDecrypt(entity.description, key) else ""
                        val decryptedCategory = safeDecrypt(entity.category, key)
                        val decryptedTags = if (entity.tags.isNotBlank()) safeDecrypt(entity.tags, key) else ""
                        val tagList = decryptedTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        LinkItem(
                            id = entity.id,
                            name = decryptedName,
                            url = decryptedUrl,
                            description = decryptedDesc,
                            category = decryptedCategory,
                            tags = tagList,
                            isFavorite = entity.isFavorite,
                            createdAt = entity.createdAt,
                            updatedAt = entity.updatedAt
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun saveLink(
        id: Long = 0,
        name: String,
        url: String,
        description: String,
        category: String,
        tags: List<String>,
        isFavorite: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val key = activeKey ?: return@withContext false
        if (id == 0L && vaultDao.getLinkCount() >= 10) {
            return@withContext false
        }
        try {
            val now = System.currentTimeMillis()
            val tagsString = tags.joinToString(",")
            val encName = VaultCrypto.encrypt(name, key)
            val encUrl = VaultCrypto.encrypt(url, key)
            val encDesc = if (description.isNotBlank()) VaultCrypto.encrypt(description, key) else ""
            val encCategory = VaultCrypto.encrypt(category, key)
            val encTags = VaultCrypto.encrypt(tagsString, key)

            if (id == 0L) {
                val entity = LinkEntryEntity(
                    name = encName,
                    url = encUrl,
                    description = encDesc,
                    category = encCategory,
                    tags = encTags,
                    isFavorite = isFavorite,
                    createdAt = now,
                    updatedAt = now
                )
                vaultDao.insertLink(entity)
            } else {
                val existing = vaultDao.getLinkById(id)
                val createdTime = existing?.createdAt ?: now
                val entity = LinkEntryEntity(
                    id = id,
                    name = encName,
                    url = encUrl,
                    description = encDesc,
                    category = encCategory,
                    tags = encTags,
                    isFavorite = isFavorite,
                    createdAt = createdTime,
                    updatedAt = now
                )
                vaultDao.updateLink(entity)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteLink(id: Long) = withContext(Dispatchers.IO) {
        vaultDao.deleteLinkById(id)
    }

    suspend fun toggleLinkFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        vaultDao.setLinkFavorite(id, isFavorite)
    }

    suspend fun updateAutoLock(minutes: Int) = withContext(Dispatchers.IO) {
        vaultDao.updateAutoLock(minutes)
    }

    suspend fun updateClipboardTimeout(seconds: Int) = withContext(Dispatchers.IO) {
        vaultDao.updateClipboardTimeout(seconds)
    }

    suspend fun wipeVault() = withContext(Dispatchers.IO) {
        vaultDao.clearAllPasswords()
        vaultDao.clearAllLinks()
        vaultDao.clearMetadata()
        activeKey = null
        _isUnlocked.value = false
    }

    fun computeSecurityReport(passwords: List<DecryptedPasswordItem>): SecurityReport {
        if (passwords.isEmpty()) {
            return SecurityReport(
                overallScore = 100,
                totalPasswords = 0,
                strongCount = 0,
                fairCount = 0,
                weakCount = 0,
                reusedCount = 0,
                weakItems = emptyList(),
                reusedGroups = emptyMap(),
                recommendations = listOf("No passwords saved yet. Add passwords to inspect vault health.")
            )
        }

        var strongCount = 0
        var fairCount = 0
        var weakCount = 0
        val weakItems = mutableListOf<DecryptedPasswordItem>()

        val passwordsBySecret = mutableMapOf<String, MutableList<DecryptedPasswordItem>>()

        for (item in passwords) {
            when (item.strength.level) {
                StrengthLevel.STRONG, StrengthLevel.VERY_STRONG -> strongCount++
                StrengthLevel.FAIR -> fairCount++
                StrengthLevel.WEAK, StrengthLevel.VERY_WEAK -> {
                    weakCount++
                    weakItems.add(item)
                }
            }

            if (item.password.isNotBlank()) {
                val secretHash = VaultCrypto.sha256Hex(item.password)
                val list = passwordsBySecret.getOrPut(secretHash) { mutableListOf() }
                list.add(item)
            }
        }

        val reusedGroups = passwordsBySecret.filter { it.value.size > 1 }
        val reusedCount = reusedGroups.values.sumOf { it.size }

        // Compute score: starts at 100
        // -15 per weak password (capped at -50)
        // -10 per reused password (capped at -40)
        var deduction = 0
        deduction += (weakCount * 15).coerceAtMost(50)
        deduction += (reusedCount * 10).coerceAtMost(40)
        val score = (100 - deduction).coerceIn(10, 100)

        val recommendations = mutableListOf<String>()
        if (weakCount > 0) {
            recommendations.add("$weakCount password(s) have weak entropy. Upgrade to 16+ characters with symbols.")
        }
        if (reusedCount > 0) {
            recommendations.add("$reusedCount password(s) are shared across multiple services. Use unique passwords.")
        }
        if (weakCount == 0 && reusedCount == 0) {
            recommendations.add("Excellent! All passwords meet strong entropy and uniqueness standards.")
        }

        return SecurityReport(
            overallScore = score,
            totalPasswords = passwords.size,
            strongCount = strongCount,
            fairCount = fairCount,
            weakCount = weakCount,
            reusedCount = reusedCount,
            weakItems = weakItems,
            reusedGroups = reusedGroups,
            recommendations = recommendations
        )
    }

    private fun reEncryptField(fieldValue: String, sourceKey: SecretKey?, targetKey: SecretKey): String {
        if (fieldValue.isBlank()) return ""
        if (isCiphertext(fieldValue, targetKey)) {
            return fieldValue
        }
        if (sourceKey != null) {
            try {
                val plaintext = VaultCrypto.decrypt(fieldValue, sourceKey)
                return VaultCrypto.encrypt(plaintext, targetKey)
            } catch (_: Exception) {}
        }
        return VaultCrypto.encrypt(fieldValue, targetKey)
    }

    suspend fun exportVaultBackup(password: CharArray): String? = withContext(Dispatchers.IO) {
        val curKey = activeKey ?: return@withContext null
        try {
            val meta = vaultDao.getMetadataDirect() ?: return@withContext null
            val verifiedKey = VaultCrypto.verifyMasterPassword(password, meta.saltBase64, meta.verifierCipherBase64)
                ?: return@withContext null

            val passwords = vaultDao.getAllPasswordsDirect()
            val links = vaultDao.getAllLinksDirect()

            val payload = JSONObject()
            payload.put("vaultApp", VaultCrypto.BACKUP_APP_ID)
            payload.put("payloadVersion", VaultCrypto.BACKUP_CONTAINER_VERSION)
            payload.put("exportTime", System.currentTimeMillis())
            payload.put("sourceSaltBase64", meta.saltBase64)
            payload.put("verifierCipherBase64", meta.verifierCipherBase64)

            val passArray = JSONArray()
            for (p in passwords) {
                val obj = JSONObject()
                obj.put("websiteName", p.websiteName)
                obj.put("url", p.url)
                obj.put("category", p.category)
                obj.put("tags", p.tags)
                obj.put("isFavorite", p.isFavorite)
                obj.put("encryptedUsername", p.encryptedUsername)
                obj.put("encryptedPassword", p.encryptedPassword)
                obj.put("encryptedNotes", p.encryptedNotes)
                obj.put("createdAt", p.createdAt)
                obj.put("updatedAt", p.updatedAt)
                passArray.put(obj)
            }
            payload.put("passwords", passArray)

            val linkArray = JSONArray()
            for (l in links) {
                val obj = JSONObject()
                obj.put("name", l.name)
                obj.put("url", l.url)
                obj.put("description", l.description)
                obj.put("category", l.category)
                obj.put("tags", l.tags)
                obj.put("isFavorite", l.isFavorite)
                obj.put("createdAt", l.createdAt)
                obj.put("updatedAt", l.updatedAt)
                linkArray.put(obj)
            }
            payload.put("links", linkArray)

            val payloadStr = payload.toString()

            val exportSalt = VaultCrypto.generateRandomSalt(32)
            val saltBase64 = Base64.encodeToString(exportSalt, Base64.NO_WRAP)

            val exportKey = VaultCrypto.deriveKey(password, exportSalt, VaultCrypto.PBKDF2_ITERATIONS)

            val exportIv = VaultCrypto.generateRandomIv()
            val ivBase64 = Base64.encodeToString(exportIv, Base64.NO_WRAP)

            val aadStr = "${VaultCrypto.BACKUP_APP_ID}|${VaultCrypto.BACKUP_CONTAINER_VERSION}|PBKDF2WithHmacSHA256|${VaultCrypto.PBKDF2_ITERATIONS}|256|$saltBase64|AES-256-GCM|128|$ivBase64"
            val aad = aadStr.toByteArray(java.nio.charset.StandardCharsets.UTF_8)

            val ciphertextBase64 = VaultCrypto.encryptContainer(payloadStr, exportKey, exportIv, aad)

            val container = JSONObject()
            container.put("app", VaultCrypto.BACKUP_APP_ID)
            container.put("version", VaultCrypto.BACKUP_CONTAINER_VERSION)
            container.put("format", "authenticated-vault-backup")
            container.put("exportedAt", System.currentTimeMillis())

            val kdfObj = JSONObject()
            kdfObj.put("algorithm", "PBKDF2WithHmacSHA256")
            kdfObj.put("iterations", VaultCrypto.PBKDF2_ITERATIONS)
            kdfObj.put("keyLengthBits", 256)
            kdfObj.put("saltBase64", saltBase64)
            container.put("kdf", kdfObj)

            val encObj = JSONObject()
            encObj.put("cipher", "AES-256-GCM")
            encObj.put("tagLengthBits", 128)
            encObj.put("ivBase64", ivBase64)
            container.put("encryption", encObj)

            container.put("ciphertext", ciphertextBase64)

            container.toString(2)
        } catch (e: Exception) {
            null
        } finally {
            VaultCrypto.wipeMemory(password)
        }
    }

    suspend fun exportVaultJson(password: CharArray): String? = exportVaultBackup(password)

    suspend fun exportVaultJson(): String? = withContext(Dispatchers.IO) {
        null
    }

    suspend fun importVaultBackup(jsonString: String, password: CharArray): Boolean = withContext(Dispatchers.IO) {
        val targetKey = activeKey ?: return@withContext false
        try {
            if (jsonString.length > 50 * 1024 * 1024 || jsonString.isBlank()) {
                return@withContext false
            }

            val root = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                return@withContext false
            }

            if (!root.has("app") || root.getString("app") != VaultCrypto.BACKUP_APP_ID) {
                return@withContext false
            }

            val version = root.optInt("version", -1)

            if (version == VaultCrypto.BACKUP_CONTAINER_VERSION) {
                val kdf = root.optJSONObject("kdf") ?: return@withContext false
                if (kdf.optString("algorithm") != "PBKDF2WithHmacSHA256") return@withContext false
                val iterations = kdf.optInt("iterations", -1)
                if (iterations < 10_000 || iterations > 1_000_000) return@withContext false
                val keyLengthBits = kdf.optInt("keyLengthBits", -1)
                if (keyLengthBits != 256) return@withContext false
                val saltBase64 = kdf.optString("saltBase64", "")
                val saltBytes = try {
                    Base64.decode(saltBase64, Base64.NO_WRAP)
                } catch (_: Exception) {
                    return@withContext false
                }
                if (saltBytes.size < 16 || saltBytes.size > 64) return@withContext false

                val enc = root.optJSONObject("encryption") ?: return@withContext false
                if (enc.optString("cipher") != "AES-256-GCM") return@withContext false
                val tagLengthBits = enc.optInt("tagLengthBits", -1)
                if (tagLengthBits != 128) return@withContext false
                val ivBase64 = enc.optString("ivBase64", "")
                val ivBytes = try {
                    Base64.decode(ivBase64, Base64.NO_WRAP)
                } catch (_: Exception) {
                    return@withContext false
                }
                if (ivBytes.size != VaultCrypto.GCM_IV_LENGTH_BYTES) return@withContext false

                val ciphertextBase64 = root.optString("ciphertext", "")
                if (ciphertextBase64.isBlank()) return@withContext false
                try {
                    val decodedCipher = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
                    if (decodedCipher.size < 16) return@withContext false
                } catch (_: Exception) {
                    return@withContext false
                }

                val aadStr = "${VaultCrypto.BACKUP_APP_ID}|$version|PBKDF2WithHmacSHA256|$iterations|$keyLengthBits|$saltBase64|AES-256-GCM|128|$ivBase64"
                val aad = aadStr.toByteArray(java.nio.charset.StandardCharsets.UTF_8)

                val importKey = VaultCrypto.deriveKey(password, saltBytes, iterations)

                val decryptedPayloadStr = try {
                    VaultCrypto.decryptContainer(ciphertextBase64, importKey, ivBytes, aad)
                } catch (e: Exception) {
                    return@withContext false
                }

                val payloadObj = try {
                    JSONObject(decryptedPayloadStr)
                } catch (e: Exception) {
                    return@withContext false
                }

                if (payloadObj.optString("vaultApp") != VaultCrypto.BACKUP_APP_ID) return@withContext false
                if (payloadObj.optInt("payloadVersion") != VaultCrypto.BACKUP_CONTAINER_VERSION) return@withContext false

                val sourceSaltBase64 = payloadObj.optString("sourceSaltBase64", "")
                val sourceKey = if (sourceSaltBase64.isNotEmpty()) {
                    try {
                        val srcSalt = Base64.decode(sourceSaltBase64, Base64.NO_WRAP)
                        VaultCrypto.deriveKey(password, srcSalt, VaultCrypto.PBKDF2_ITERATIONS)
                    } catch (_: Exception) {
                        null
                    }
                } else null

                val passwordsToInsert = mutableListOf<PasswordEntryEntity>()
                if (payloadObj.has("passwords")) {
                    val passArray = try {
                        payloadObj.getJSONArray("passwords")
                    } catch (_: Exception) {
                        return@withContext false
                    }
                    for (i in 0 until passArray.length()) {
                        val obj = try {
                            passArray.getJSONObject(i)
                        } catch (_: Exception) {
                            return@withContext false
                        }
                        val rawWebsite = obj.optString("websiteName", "Untitled")
                        val rawUrl = obj.optString("url", "")
                        val rawCat = obj.optString("category", "Login")
                        val rawTags = obj.optString("tags", "")
                        val rawUsername = obj.optString("encryptedUsername", "")
                        val rawPassword = obj.optString("encryptedPassword", "")
                        val rawNotes = obj.optString("encryptedNotes", "")

                        passwordsToInsert.add(
                            PasswordEntryEntity(
                                websiteName = reEncryptField(rawWebsite, sourceKey, targetKey),
                                url = reEncryptField(rawUrl, sourceKey, targetKey),
                                category = reEncryptField(rawCat, sourceKey, targetKey),
                                tags = reEncryptField(rawTags, sourceKey, targetKey),
                                isFavorite = obj.optBoolean("isFavorite", false),
                                encryptedUsername = reEncryptField(rawUsername, sourceKey, targetKey),
                                encryptedPassword = reEncryptField(rawPassword, sourceKey, targetKey),
                                encryptedNotes = reEncryptField(rawNotes, sourceKey, targetKey),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val linksToInsert = mutableListOf<LinkEntryEntity>()
                if (payloadObj.has("links")) {
                    val linkArray = try {
                        payloadObj.getJSONArray("links")
                    } catch (_: Exception) {
                        return@withContext false
                    }
                    for (i in 0 until linkArray.length()) {
                        val obj = try {
                            linkArray.getJSONObject(i)
                        } catch (_: Exception) {
                            return@withContext false
                        }
                        val rawName = obj.optString("name", "Untitled Link")
                        val rawUrl = obj.optString("url", "")
                        val rawDesc = obj.optString("description", "")
                        val rawCat = obj.optString("category", "General")
                        val rawTags = obj.optString("tags", "")

                        linksToInsert.add(
                            LinkEntryEntity(
                                name = reEncryptField(rawName, sourceKey, targetKey),
                                url = reEncryptField(rawUrl, sourceKey, targetKey),
                                description = reEncryptField(rawDesc, sourceKey, targetKey),
                                category = reEncryptField(rawCat, sourceKey, targetKey),
                                tags = reEncryptField(rawTags, sourceKey, targetKey),
                                isFavorite = obj.optBoolean("isFavorite", false),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val currentPassCount = vaultDao.getPasswordCount()
                val currentLinkCount = vaultDao.getLinkCount()
                if (currentPassCount + passwordsToInsert.size > 10 || currentLinkCount + linksToInsert.size > 10) {
                    return@withContext false
                }

                vaultDao.insertImportedVaultData(passwordsToInsert, linksToInsert)
                return@withContext true
            } else if (version == 1 && root.has("passwords")) {
                val saltBase64 = root.optString("saltBase64", "")
                val verifierCipherBase64 = root.optString("verifierCipherBase64", "")
                var sourceKey: SecretKey? = null
                if (saltBase64.isNotEmpty() && verifierCipherBase64.isNotEmpty() && password.isNotEmpty()) {
                    sourceKey = VaultCrypto.verifyMasterPassword(password, saltBase64, verifierCipherBase64)
                    if (sourceKey == null) {
                        return@withContext false
                    }
                }

                val passwordsToInsert = mutableListOf<PasswordEntryEntity>()
                val passArray = root.getJSONArray("passwords")
                for (i in 0 until passArray.length()) {
                    val obj = passArray.getJSONObject(i)
                    val rawWebsite = obj.optString("websiteName", "Untitled")
                    val rawUrl = obj.optString("url", "")
                    val rawCat = obj.optString("category", "Login")
                    val rawTags = obj.optString("tags", "")
                    val rawUsername = obj.optString("encryptedUsername", "")
                    val rawPassword = obj.optString("encryptedPassword", "")
                    val rawNotes = obj.optString("encryptedNotes", "")

                    passwordsToInsert.add(
                        PasswordEntryEntity(
                            websiteName = reEncryptField(rawWebsite, sourceKey, targetKey),
                            url = reEncryptField(rawUrl, sourceKey, targetKey),
                            category = reEncryptField(rawCat, sourceKey, targetKey),
                            tags = reEncryptField(rawTags, sourceKey, targetKey),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            encryptedUsername = reEncryptField(rawUsername, sourceKey, targetKey),
                            encryptedPassword = reEncryptField(rawPassword, sourceKey, targetKey),
                            encryptedNotes = reEncryptField(rawNotes, sourceKey, targetKey),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }

                val linksToInsert = mutableListOf<LinkEntryEntity>()
                if (root.has("links")) {
                    val linkArray = root.getJSONArray("links")
                    for (i in 0 until linkArray.length()) {
                        val obj = linkArray.getJSONObject(i)
                        val rawName = obj.optString("name", "Untitled Link")
                        val rawUrl = obj.optString("url", "")
                        val rawDesc = obj.optString("description", "")
                        val rawCat = obj.optString("category", "General")
                        val rawTags = obj.optString("tags", "")

                        linksToInsert.add(
                            LinkEntryEntity(
                                name = reEncryptField(rawName, sourceKey, targetKey),
                                url = reEncryptField(rawUrl, sourceKey, targetKey),
                                description = reEncryptField(rawDesc, sourceKey, targetKey),
                                category = reEncryptField(rawCat, sourceKey, targetKey),
                                tags = reEncryptField(rawTags, sourceKey, targetKey),
                                isFavorite = obj.optBoolean("isFavorite", false),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val currentPassCount = vaultDao.getPasswordCount()
                val currentLinkCount = vaultDao.getLinkCount()
                if (currentPassCount + passwordsToInsert.size > 10 || currentLinkCount + linksToInsert.size > 10) {
                    return@withContext false
                }

                vaultDao.insertImportedVaultData(passwordsToInsert, linksToInsert)
                return@withContext true
            } else {
                return@withContext false
            }
        } catch (e: Exception) {
            return@withContext false
        } finally {
            VaultCrypto.wipeMemory(password)
        }
    }

    suspend fun importVaultJson(jsonString: String, password: CharArray? = null): Boolean {
        return if (password != null) {
            importVaultBackup(jsonString, password)
        } else {
            importVaultBackup(jsonString, CharArray(0))
        }
    }
}
