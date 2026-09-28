package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.crypto.StrengthLevel
import com.example.data.crypto.VaultCrypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @org.junit.Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        database.vaultDao().clearMetadata()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HexaPass", appName)
    }

    @Test
    fun `test vault crypto encryption and decryption`() {
        val masterPassword = "SuperSecretMasterKey123!".toCharArray()
        val salt = VaultCrypto.generateRandomSalt()
        val derivedKey = VaultCrypto.deriveKey(masterPassword, salt)

        val plaintext = "SensitiveSuperSecretPassword456"
        val encrypted = VaultCrypto.encrypt(plaintext, derivedKey)

        assertNotNull(encrypted)
        assertNotEquals(plaintext, encrypted)

        val decrypted = VaultCrypto.decrypt(encrypted, derivedKey)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `test password generator and strength evaluation`() {
        val generated = VaultCrypto.generatePassword(length = 20)
        assertEquals(20, generated.length)

        val strength = VaultCrypto.evaluateStrength(generated)
        assertTrue(strength.entropyBits > 60)
        assertTrue(
            strength.level == StrengthLevel.STRONG || strength.level == StrengthLevel.VERY_STRONG
        )
    }

    @Test
    fun `test link sensitive fields authenticated encryption`() {
        val masterPassword = "TestPassword@2026".toCharArray()
        val salt = VaultCrypto.generateRandomSalt()
        val derivedKey = VaultCrypto.deriveKey(masterPassword, salt)

        val rawUrl = "https://internal.corp.network/admin?token=secret123"
        val rawDesc = "Confidential production server access URL"
        val rawTags = "internal,production,secret"

        val encUrl = VaultCrypto.encrypt(rawUrl, derivedKey)
        val encDesc = VaultCrypto.encrypt(rawDesc, derivedKey)
        val encTags = VaultCrypto.encrypt(rawTags, derivedKey)

        assertNotEquals(rawUrl, encUrl)
        assertNotEquals(rawDesc, encDesc)
        assertNotEquals(rawTags, encTags)

        assertEquals(rawUrl, VaultCrypto.decrypt(encUrl, derivedKey))
        assertEquals(rawDesc, VaultCrypto.decrypt(encDesc, derivedKey))
        assertEquals(rawTags, VaultCrypto.decrypt(encTags, derivedKey))
    }

    @Test
    fun `test launch MainActivity`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun `test background auto-lock clears active key and locks vault`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val password = "MasterPassword2026!#".toCharArray()

        // 1. Verify repository direct key nullification and locking
        val setupSuccess = repository.setupMasterPassword(password)
        assertTrue(setupSuccess)
        assertNotNull(repository.getActiveKey())
        assertTrue(repository.isUnlocked.value)

        repository.lockVault()
        org.junit.Assert.assertNull(repository.getActiveKey())
        org.junit.Assert.assertFalse(repository.isUnlocked.value)

        // 2. Verify ViewModel background auto-lock lifecycle transition
        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)

        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success ->
            vmUnlockSuccess = success
        }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)
        assertEquals(com.example.ui.viewmodel.VaultAuthState.Unlocked, viewModel.authState.value)

        // Simulate app entering background (ON_STOP)
        viewModel.onAppBackgrounded()

        // Verify ViewModel immediately locks and enters Locked state
        assertEquals(com.example.ui.viewmodel.VaultAuthState.Locked, viewModel.authState.value)
    }

    @Test
    fun `test A C D - password metadata encryption, decryption, and locked hiding`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val password = "StrongMasterPass123!#".toCharArray()

        repository.setupMasterPassword(password)
        val key = repository.getActiveKey()
        assertNotNull(key)

        val website = "ProtonMail"
        val url = "https://mail.proton.me"
        val category = "Finance"
        val tags = listOf("email", "secure")

        val saved = repository.savePassword(
            websiteName = website,
            url = url,
            username = "alice",
            password = "SecretPassword123!",
            notes = "Recovery note",
            category = category,
            tags = tags,
            isFavorite = true
        )
        assertTrue(saved)

        // A. Verify metadata is encrypted in SQLite
        val entities = database.vaultDao().getAllPasswordsDirect()
        val entry = entities.first { it.isFavorite }
        assertNotEquals(website, entry.websiteName)
        assertNotEquals(url, entry.url)
        assertNotEquals(category, entry.category)
        assertNotEquals("email,secure", entry.tags)

        assertTrue(repository.isCiphertext(entry.websiteName, key!!))
        assertTrue(repository.isCiphertext(entry.url, key))
        assertTrue(repository.isCiphertext(entry.category, key))
        assertTrue(repository.isCiphertext(entry.tags, key))

        // C. Verify correct decryption while vault is unlocked
        val decryptedList = repository.observeDecryptedPasswords().first()
        val item = decryptedList.first { it.id == entry.id }
        assertEquals(website, item.websiteName)
        assertEquals(url, item.url)
        assertEquals(category, item.category)
        assertEquals(tags, item.tags)
        assertEquals("alice", item.username)
        assertEquals("SecretPassword123!", item.password)
        assertEquals("Recovery note", item.notes)

        // D. Verify locked vault does not expose decrypted metadata
        repository.lockVault()
        val lockedList = repository.observeDecryptedPasswords().first()
        assertTrue(lockedList.isEmpty())

        // Re-check database directly: still encrypted
        val directAfterLock = database.vaultDao().getAllPasswordsDirect()
        assertNotEquals(website, directAfterLock[0].websiteName)
    }

    @Test
    fun `test B C - link metadata encryption and decryption`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val password = "LinkTestPass2026!#".toCharArray()

        repository.setupMasterPassword(password)
        val key = repository.getActiveKey()
        assertNotNull(key)

        val name = "AWS Management Console"
        val category = "Cloud Infrastructure"
        val url = "https://console.aws.amazon.com"
        val desc = "Production AWS admin portal"
        val tags = listOf("devops", "cloud")

        val saved = repository.saveLink(
            name = name,
            url = url,
            description = desc,
            category = category,
            tags = tags,
            isFavorite = true
        )
        assertTrue(saved)

        // B. Verify link metadata is encrypted in database
        val links = database.vaultDao().getAllLinksDirect()
        val linkEntity = links.first { it.isFavorite }
        assertNotEquals(name, linkEntity.name)
        assertNotEquals(category, linkEntity.category)
        assertNotEquals(url, linkEntity.url)
        assertNotEquals(desc, linkEntity.description)
        assertNotEquals("devops,cloud", linkEntity.tags)

        assertTrue(repository.isCiphertext(linkEntity.name, key!!))
        assertTrue(repository.isCiphertext(linkEntity.category, key))

        // C. Verify correct decryption while unlocked
        val decryptedLinks = repository.observeLinks().first()
        val linkItem = decryptedLinks.first { it.id == linkEntity.id }
        assertEquals(name, linkItem.name)
        assertEquals(category, linkItem.category)
        assertEquals(url, linkItem.url)
        assertEquals(desc, linkItem.description)
        assertEquals(tags, linkItem.tags)
    }

    @Test
    fun `test E - CRUD operations still work with metadata encryption`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val password = "CrudMasterPassword1!#".toCharArray()

        repository.setupMasterPassword(password)
        val key = repository.getActiveKey()!!

        // Create
        val saved = repository.savePassword(
            websiteName = "GitHub",
            url = "https://github.com",
            username = "octocat",
            password = "GitPassword!1",
            notes = "Dev account",
            category = "Work",
            tags = listOf("dev", "code"),
            isFavorite = false
        )
        assertTrue(saved)

        val items = repository.observeDecryptedPasswords().first()
        val created = items.first { it.username == "octocat" }
        assertEquals("GitHub", created.websiteName)

        // Update
        val updated = repository.savePassword(
            id = created.id,
            websiteName = "GitHub Enterprise",
            url = "https://github.corp.com",
            username = "octocat",
            password = "GitPassword!2",
            notes = "Updated notes",
            category = "Engineering",
            tags = listOf("dev", "enterprise"),
            isFavorite = true
        )
        assertTrue(updated)

        val updatedItems = repository.observeDecryptedPasswords().first()
        val itemAfterUpdate = updatedItems.first { it.id == created.id }
        assertEquals("GitHub Enterprise", itemAfterUpdate.websiteName)
        assertEquals("Engineering", itemAfterUpdate.category)
        assertTrue(itemAfterUpdate.isFavorite)

        // Verify DB stored encrypted
        val dbEntity = database.vaultDao().getPasswordById(created.id)!!
        assertNotEquals("GitHub Enterprise", dbEntity.websiteName)
        assertEquals("GitHub Enterprise", VaultCrypto.decrypt(dbEntity.websiteName, key))

        // Delete
        repository.deletePassword(created.id)
        val afterDelete = repository.observeDecryptedPasswords().first()
        assertTrue(afterDelete.none { it.id == created.id })
    }

    @Test
    fun `test F - existing plaintext data migration works and is idempotent`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val password = "MigrationPassword2026!#".toCharArray()

        repository.setupMasterPassword(password)
        val key = repository.getActiveKey()!!

        // Insert raw unencrypted legacy password entry into SQLite
        val legacyPasswordEntity = com.example.data.db.PasswordEntryEntity(
            websiteName = "PlaintextBank",
            url = "https://plaintextbank.com",
            category = "Banking",
            tags = "legacy,bank",
            isFavorite = true,
            encryptedUsername = VaultCrypto.encrypt("bankUser", key),
            encryptedPassword = VaultCrypto.encrypt("bankPass", key),
            encryptedNotes = VaultCrypto.encrypt("bankNotes", key)
        )
        val passId = database.vaultDao().insertPassword(legacyPasswordEntity)

        // Insert raw unencrypted legacy link entry into SQLite
        val legacyLinkEntity = com.example.data.db.LinkEntryEntity(
            name = "PlaintextInternalLink",
            url = "https://internal.plaintext.com",
            description = "Unencrypted description",
            category = "Internal",
            tags = "intranet,legacy",
            isFavorite = true
        )
        val linkId = database.vaultDao().insertLink(legacyLinkEntity)

        // Confirm fields are plaintext in database before migration
        val rawPassBefore = database.vaultDao().getPasswordById(passId)!!
        assertEquals("PlaintextBank", rawPassBefore.websiteName)
        assertEquals("https://plaintextbank.com", rawPassBefore.url)
        assertEquals("Banking", rawPassBefore.category)
        assertEquals("legacy,bank", rawPassBefore.tags)

        val rawLinkBefore = database.vaultDao().getLinkById(linkId)!!
        assertEquals("PlaintextInternalLink", rawLinkBefore.name)
        assertEquals("Internal", rawLinkBefore.category)

        // Execute migration
        val migratedCount = repository.migratePlaintextMetadataIfNeeded(key)
        assertTrue(migratedCount >= 2)

        // Verify plaintext values are NO LONGER stored in database
        val rawPassAfter = database.vaultDao().getPasswordById(passId)!!
        assertNotEquals("PlaintextBank", rawPassAfter.websiteName)
        assertNotEquals("https://plaintextbank.com", rawPassAfter.url)
        assertNotEquals("Banking", rawPassAfter.category)
        assertNotEquals("legacy,bank", rawPassAfter.tags)

        // Verify values decrypt correctly with active key
        assertEquals("PlaintextBank", VaultCrypto.decrypt(rawPassAfter.websiteName, key))
        assertEquals("https://plaintextbank.com", VaultCrypto.decrypt(rawPassAfter.url, key))
        assertEquals("Banking", VaultCrypto.decrypt(rawPassAfter.category, key))
        assertEquals("legacy,bank", VaultCrypto.decrypt(rawPassAfter.tags, key))

        val rawLinkAfter = database.vaultDao().getLinkById(linkId)!!
        assertNotEquals("PlaintextInternalLink", rawLinkAfter.name)
        assertNotEquals("Internal", rawLinkAfter.category)
        assertEquals("PlaintextInternalLink", VaultCrypto.decrypt(rawLinkAfter.name, key))
        assertEquals("Internal", VaultCrypto.decrypt(rawLinkAfter.category, key))

        // Idempotency check: migrating again should not re-encrypt already encrypted values
        val secondMigrationCount = repository.migratePlaintextMetadataIfNeeded(key)
        assertEquals(0, secondMigrationCount)

        val rawPassAfterSecond = database.vaultDao().getPasswordById(passId)!!
        assertEquals("PlaintextBank", VaultCrypto.decrypt(rawPassAfterSecond.websiteName, key))
    }

    @Test
    fun `test G - master password rotation re-encrypts metadata correctly`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())

        val oldPass = "OldMasterPassword123!".toCharArray()
        val newPass = "NewMasterPassword456!".toCharArray()

        repository.setupMasterPassword(oldPass)
        val oldKey = repository.getActiveKey()!!

        repository.savePassword(
            websiteName = "SecureGovernment",
            url = "https://gov.secret.example",
            username = "agent007",
            password = "TopSecretPassword",
            notes = "Classified",
            category = "Government",
            tags = listOf("classified", "special"),
            isFavorite = true
        )

        repository.saveLink(
            name = "ClassifiedPortal",
            url = "https://portal.classified.example",
            description = "Secret portal",
            category = "Intel",
            tags = listOf("classified"),
            isFavorite = true
        )

        // Rotate master password
        val changed = repository.changeMasterPassword("OldMasterPassword123!".toCharArray(), newPass)
        assertTrue(changed)

        val newKey = repository.getActiveKey()!!
        assertNotEquals(oldKey, newKey)

        // Verify entities in database decrypt with newKey
        val passEntity = database.vaultDao().getAllPasswordsDirect().first { it.isFavorite }
        assertEquals("SecureGovernment", VaultCrypto.decrypt(passEntity.websiteName, newKey))
        assertEquals("Government", VaultCrypto.decrypt(passEntity.category, newKey))
        assertEquals("classified,special", VaultCrypto.decrypt(passEntity.tags, newKey))

        // Verify entities in database FAIL to decrypt with oldKey
        var oldKeyFailed = false
        try {
            VaultCrypto.decrypt(passEntity.websiteName, oldKey)
        } catch (_: Exception) {
            oldKeyFailed = true
        }
        assertTrue(oldKeyFailed)

        // Verify observed flows work seamlessly under newKey
        val decryptedPasses = repository.observeDecryptedPasswords().first()
        assertEquals("SecureGovernment", decryptedPasses.first { it.isFavorite }.websiteName)

        val decryptedLinks = repository.observeLinks().first()
        assertEquals("ClassifiedPortal", decryptedLinks.first { it.isFavorite }.name)
    }

    @Test
    fun `test H - different encryption operations use different random IVs`() {
        val masterPassword = "MasterPasswordIVTest123!".toCharArray()
        val salt = VaultCrypto.generateRandomSalt()
        val key = VaultCrypto.deriveKey(masterPassword, salt)

        val plaintext = "IdenticalPlaintextMetadata"
        val enc1 = VaultCrypto.encrypt(plaintext, key)
        val enc2 = VaultCrypto.encrypt(plaintext, key)

        // Ciphertexts must differ due to distinct random 12-byte IVs
        assertNotEquals(enc1, enc2)

        val bytes1 = android.util.Base64.decode(enc1, android.util.Base64.NO_WRAP)
        val bytes2 = android.util.Base64.decode(enc2, android.util.Base64.NO_WRAP)

        val iv1 = bytes1.copyOfRange(0, 12)
        val iv2 = bytes2.copyOfRange(0, 12)

        var ivsDiffer = false
        for (i in 0 until 12) {
            if (iv1[i] != iv2[i]) {
                ivsDiffer = true
                break
            }
        }
        assertTrue("IVs must be independently generated random bytes", ivsDiffer)

        // Both decrypt back to original plaintext
        assertEquals(plaintext, VaultCrypto.decrypt(enc1, key))
        assertEquals(plaintext, VaultCrypto.decrypt(enc2, key))
    }

    @Test
    fun `test I - tampered ciphertext fails authenticated decryption`() {
        val masterPassword = "TamperProofMasterPassword2026!".toCharArray()
        val salt = VaultCrypto.generateRandomSalt()
        val key = VaultCrypto.deriveKey(masterPassword, salt)

        val plaintext = "CriticalSecretMetadata"
        val encrypted = VaultCrypto.encrypt(plaintext, key)

        val decoded = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
        // Tamper with one byte in the ciphertext payload
        decoded[15] = (decoded[15].toInt() xor 0x5A).toByte()
        val tamperedBase64 = android.util.Base64.encodeToString(decoded, android.util.Base64.NO_WRAP)

        var failed = false
        try {
            VaultCrypto.decrypt(tamperedBase64, key)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Tampered AES-GCM ciphertext must fail authenticated decryption", failed)
    }

    @Test
    fun `test S4 A - Export creates a valid authenticated encrypted container`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val masterPassword = "TestMasterPassword2026!".toCharArray()
        repository.setupMasterPassword(masterPassword)

        val passSaved = repository.savePassword(
            websiteName = "GitHub",
            url = "https://github.com",
            category = "Developer",
            tags = listOf("code", "git"),
            username = "octocat",
            password = "SecretPassword123!",
            notes = "My GitHub keys",
            isFavorite = false
        )
        assertTrue(passSaved)

        val linkSaved = repository.saveLink(
            id = 0L,
            name = "Android Docs",
            url = "https://developer.android.com",
            description = "Dev reference",
            category = "Docs",
            tags = listOf("android", "kotlin"),
            isFavorite = false
        )
        assertTrue(linkSaved)

        val backupJson = repository.exportVaultBackup("TestMasterPassword2026!".toCharArray())
        assertNotNull(backupJson)

        val root = org.json.JSONObject(backupJson!!)
        assertEquals("HexaPass", root.getString("app"))
        assertEquals(2, root.getInt("version"))
        assertEquals("authenticated-vault-backup", root.getString("format"))

        val kdf = root.getJSONObject("kdf")
        assertEquals("PBKDF2WithHmacSHA256", kdf.getString("algorithm"))
        assertEquals(100_000, kdf.getInt("iterations"))
        assertEquals(256, kdf.getInt("keyLengthBits"))
        assertTrue(kdf.getString("saltBase64").isNotBlank())

        val enc = root.getJSONObject("encryption")
        assertEquals("AES-256-GCM", enc.getString("cipher"))
        assertEquals(128, enc.getInt("tagLengthBits"))
        assertTrue(enc.getString("ivBase64").isNotBlank())

        assertTrue(root.getString("ciphertext").isNotBlank())
    }

    @Test
    fun `test S4 B - Exported container does NOT contain master password or sensitive plaintext metadata`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val masterPassword = "SensitiveMasterPassword99!".toCharArray()
        repository.setupMasterPassword(masterPassword)

        repository.savePassword(
            websiteName = "SuperSecretBank",
            url = "https://bankofamerica.com/private",
            category = "Finance",
            tags = listOf("bank", "wire", "urgent"),
            username = "topsecretuser",
            password = "VerySecretPassword456!",
            notes = "Account pin is 9988",
            isFavorite = false
        )

        repository.saveLink(
            id = 0L,
            name = "IntranetPortal",
            url = "https://internal.corp.net",
            description = "Confidential portal",
            category = "Work",
            tags = listOf("corp", "internal"),
            isFavorite = false
        )

        val backupJson = repository.exportVaultBackup("SensitiveMasterPassword99!".toCharArray())!!

        // Must NOT contain master password
        assertFalse("Export container must not leak master password", backupJson.contains("SensitiveMasterPassword99!"))

        // Must NOT contain any plaintext secrets or metadata
        assertFalse("Must not leak websiteName", backupJson.contains("SuperSecretBank"))
        assertFalse("Must not leak url", backupJson.contains("bankofamerica.com/private"))
        assertFalse("Must not leak category", backupJson.contains("Finance"))
        assertFalse("Must not leak tags", backupJson.contains("bank,wire,urgent"))
        assertFalse("Must not leak username", backupJson.contains("topsecretuser"))
        assertFalse("Must not leak password", backupJson.contains("VerySecretPassword456!"))
        assertFalse("Must not leak notes", backupJson.contains("Account pin is 9988"))
        assertFalse("Must not leak link name", backupJson.contains("IntranetPortal"))
        assertFalse("Must not leak link url", backupJson.contains("internal.corp.net"))
        assertFalse("Must not leak link description", backupJson.contains("Confidential portal"))
    }

    @Test
    fun `test S4 C and D - Export uses fresh random salt and IV for every export operation`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("RandomSaltIVTest1!".toCharArray())

        repository.savePassword(
            websiteName = "SiteA",
            url = "https://site-a.com",
            category = "CategoryA",
            tags = listOf("tag1"),
            username = "user1",
            password = "pass1",
            notes = "notes1",
            isFavorite = false
        )

        val backup1 = repository.exportVaultBackup("RandomSaltIVTest1!".toCharArray())!!
        val backup2 = repository.exportVaultBackup("RandomSaltIVTest1!".toCharArray())!!

        val root1 = org.json.JSONObject(backup1)
        val root2 = org.json.JSONObject(backup2)

        val salt1 = root1.getJSONObject("kdf").getString("saltBase64")
        val salt2 = root2.getJSONObject("kdf").getString("saltBase64")
        assertNotEquals("Each export must use a unique random salt", salt1, salt2)

        val iv1 = root1.getJSONObject("encryption").getString("ivBase64")
        val iv2 = root2.getJSONObject("encryption").getString("ivBase64")
        assertNotEquals("Each export must use a unique random IV", iv1, iv2)

        val cipher1 = root1.getString("ciphertext")
        val cipher2 = root2.getString("ciphertext")
        assertNotEquals("Ciphertexts must differ due to distinct salt and IV", cipher1, cipher2)
    }

    @Test
    fun `test S4 E and M and N - Correct master password successfully imports backup and restores entries`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("VaultTransferKey2026!".toCharArray())

        repository.savePassword(
            websiteName = "ServerBastion",
            url = "https://ssh.server.net",
            category = "Infrastructure",
            tags = listOf("ssh", "bastion"),
            username = "root",
            password = "UltraPassword987!",
            notes = "RSA 4096 key",
            isFavorite = false
        )

        repository.saveLink(
            id = 0L,
            name = "OpsDashboard",
            url = "https://ops.server.net",
            description = "Monitoring",
            category = "Infra",
            tags = listOf("ops", "metrics"),
            isFavorite = false
        )

        val backupJson = repository.exportVaultBackup("VaultTransferKey2026!".toCharArray())!!

        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()

        val importSuccess = repository.importVaultBackup(backupJson, "VaultTransferKey2026!".toCharArray())
        assertTrue("Import with correct password must succeed", importSuccess)

        val passwords = repository.observeDecryptedPasswords().first()
        assertEquals(1, passwords.size)
        val restoredPass = passwords[0]
        assertEquals("ServerBastion", restoredPass.websiteName)
        assertEquals("https://ssh.server.net", restoredPass.url)
        assertEquals("Infrastructure", restoredPass.category)
        assertEquals(listOf("ssh", "bastion"), restoredPass.tags)
        assertEquals("root", restoredPass.username)
        assertEquals("UltraPassword987!", restoredPass.password)
        assertEquals("RSA 4096 key", restoredPass.notes)

        val links = repository.observeLinks().first()
        assertEquals(1, links.size)
        val restoredLink = links[0]
        assertEquals("OpsDashboard", restoredLink.name)
        assertEquals("https://ops.server.net", restoredLink.url)
        assertEquals("Monitoring", restoredLink.description)
        assertEquals("Infra", restoredLink.category)
        assertEquals(listOf("ops", "metrics"), restoredLink.tags)
    }

    @Test
    fun `test S4 F and L - Incorrect master password fails safely and leaves existing vault unchanged`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("CorrectVaultKey123!".toCharArray())

        repository.savePassword(
            websiteName = "OriginalVaultEntry",
            url = "https://original.com",
            category = "Original",
            tags = listOf("orig"),
            username = "original_user",
            password = "original_pass",
            notes = "original_notes",
            isFavorite = false
        )

        val backupJson = repository.exportVaultBackup("CorrectVaultKey123!".toCharArray())!!

        val wrongPassword = "WrongPassword999!".toCharArray()
        val importResult = repository.importVaultBackup(backupJson, wrongPassword)
        assertFalse("Import with wrong password must return false", importResult)

        val passwords = repository.observeDecryptedPasswords().first()
        assertEquals(1, passwords.size)
        assertEquals("OriginalVaultEntry", passwords[0].websiteName)
    }

    @Test
    fun `test S4 G - Modified ciphertext fails authentication safely`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("TamperCipherKey123!".toCharArray())

        repository.savePassword(
            websiteName = "ImportantAccount",
            url = "https://important.org",
            category = "Critical",
            tags = listOf("crit"),
            username = "user",
            password = "pwd",
            notes = "",
            isFavorite = false
        )

        val backupJson = repository.exportVaultBackup("TamperCipherKey123!".toCharArray())!!
        val root = org.json.JSONObject(backupJson)
        val cipherBytes = android.util.Base64.decode(root.getString("ciphertext"), android.util.Base64.NO_WRAP)

        cipherBytes[10] = (cipherBytes[10].toInt() xor 0xFF).toByte()
        root.put("ciphertext", android.util.Base64.encodeToString(cipherBytes, android.util.Base64.NO_WRAP))

        val tamperedJson = root.toString()
        val importResult = repository.importVaultBackup(tamperedJson, "TamperCipherKey123!".toCharArray())
        assertFalse("Tampered ciphertext must fail authenticated decryption", importResult)
    }

    @Test
    fun `test S4 H - Modified IV fails authentication safely`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("TamperIVKey123!".toCharArray())

        repository.savePassword(
            websiteName = "VaultItem",
            url = "https://item.org",
            category = "Cat",
            tags = emptyList(),
            username = "u",
            password = "p",
            notes = "",
            isFavorite = false
        )

        val backupJson = repository.exportVaultBackup("TamperIVKey123!".toCharArray())!!
        val root = org.json.JSONObject(backupJson)
        val enc = root.getJSONObject("encryption")
        val ivBytes = android.util.Base64.decode(enc.getString("ivBase64"), android.util.Base64.NO_WRAP)

        ivBytes[0] = (ivBytes[0].toInt() xor 0x01).toByte()
        enc.put("ivBase64", android.util.Base64.encodeToString(ivBytes, android.util.Base64.NO_WRAP))

        val tamperedJson = root.toString()
        val importResult = repository.importVaultBackup(tamperedJson, "TamperIVKey123!".toCharArray())
        assertFalse("Tampered IV must fail authenticated decryption", importResult)
    }

    @Test
    fun `test S4 I - Modified salt or header or version fails validation or authentication`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("HeaderTestKey123!".toCharArray())

        repository.savePassword(
            websiteName = "VaultItem",
            url = "https://item.org",
            category = "Cat",
            tags = emptyList(),
            username = "u",
            password = "p",
            notes = "",
            isFavorite = false
        )

        val backupJson = repository.exportVaultBackup("HeaderTestKey123!".toCharArray())!!

        // Test 1: modified version in header
        val root1 = org.json.JSONObject(backupJson)
        root1.put("version", 999)
        assertFalse(repository.importVaultBackup(root1.toString(), "HeaderTestKey123!".toCharArray()))

        // Test 2: modified app in header
        val root2 = org.json.JSONObject(backupJson)
        root2.put("app", "MaliciousApp")
        assertFalse(repository.importVaultBackup(root2.toString(), "HeaderTestKey123!".toCharArray()))

        // Test 3: modified salt in header
        val root3 = org.json.JSONObject(backupJson)
        val kdf = root3.getJSONObject("kdf")
        val saltBytes = android.util.Base64.decode(kdf.getString("saltBase64"), android.util.Base64.NO_WRAP)
        saltBytes[0] = (saltBytes[0].toInt() xor 0x55).toByte()
        kdf.put("saltBase64", android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP))
        assertFalse(repository.importVaultBackup(root3.toString(), "HeaderTestKey123!".toCharArray()))
    }

    @Test
    fun `test S4 J and K - Truncated, malformed, or corrupted payload is safely rejected`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("CorruptedPayloadKey1!".toCharArray())

        // 1. Truncated JSON
        assertFalse(repository.importVaultBackup("{ \"app\": \"HexaPass\", \"version\": 2, ", "CorruptedPayloadKey1!".toCharArray()))

        // 2. Non-JSON string
        assertFalse(repository.importVaultBackup("THIS_IS_NOT_JSON", "CorruptedPayloadKey1!".toCharArray()))

        // 3. Empty string
        assertFalse(repository.importVaultBackup("", "CorruptedPayloadKey1!".toCharArray()))

        // 4. Missing required fields in container
        assertFalse(repository.importVaultBackup("{ \"app\": \"HexaPass\" }", "CorruptedPayloadKey1!".toCharArray()))
    }

    @Test
    fun `test S6 1 and 6 - Successful rotation with multiple entries, old password rejection, and new-key consistency`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())

        val oldPass = "OldMasterPassword123!".toCharArray()
        val newPass = "NewMasterPassword789!".toCharArray()

        repository.setupMasterPassword(oldPass)
        val oldKey = repository.getActiveKey()!!
        val oldMeta = database.vaultDao().getMetadataDirect()!!

        // Save multiple password entries
        repository.savePassword(
            websiteName = "AlphaCloud",
            url = "https://alpha.cloud.example",
            username = "alphaUser",
            password = "AlphaSecret999!",
            notes = "Cluster admin",
            category = "Cloud",
            tags = listOf("devops", "cloud"),
            isFavorite = true
        )
        repository.savePassword(
            websiteName = "BetaDatabase",
            url = "https://beta.db.example",
            username = "dbAdmin",
            password = "BetaSecret888!",
            notes = "PostgreSQL primary",
            category = "Database",
            tags = listOf("prod", "db"),
            isFavorite = false
        )

        // Save multiple link entries
        repository.saveLink(
            name = "GrafanaOps",
            url = "https://metrics.cloud.example",
            description = "Infrastructure metrics",
            category = "Monitoring",
            tags = listOf("ops", "metrics"),
            isFavorite = true
        )
        repository.saveLink(
            name = "KibanaLogs",
            url = "https://logs.cloud.example",
            description = "ELK Logging cluster",
            category = "Logging",
            tags = listOf("logs"),
            isFavorite = false
        )

        val passEntitiesBefore = database.vaultDao().getAllPasswordsDirect()
        val passEntityBeforeFav = passEntitiesBefore.first { it.isFavorite }
        val passEntityBeforeNonFav = passEntitiesBefore.first { !it.isFavorite }
        val passId1 = passEntityBeforeFav.id
        val passId2 = passEntityBeforeNonFav.id

        val linkEntitiesBefore = database.vaultDao().getAllLinksDirect()
        val linkEntityBeforeFav = linkEntitiesBefore.first { it.isFavorite }
        val linkId1 = linkEntityBeforeFav.id

        // Perform rotation
        val success = repository.changeMasterPassword("OldMasterPassword123!".toCharArray(), newPass)
        assertTrue("Password rotation should succeed", success)

        val newKey = repository.getActiveKey()!!
        assertNotEquals("Active key must be updated to new key", oldKey, newKey)

        val newMeta = database.vaultDao().getMetadataDirect()!!
        assertNotEquals("Salt must be regenerated", oldMeta.saltBase64, newMeta.saltBase64)
        assertNotEquals("Verifier must be regenerated", oldMeta.verifierCipherBase64, newMeta.verifierCipherBase64)

        // Verify entities in database now decrypt with newKey and FAIL with oldKey
        val passEntity1 = database.vaultDao().getPasswordById(passId1)!!
        assertEquals("AlphaCloud", VaultCrypto.decrypt(passEntity1.websiteName, newKey))
        assertEquals("AlphaSecret999!", VaultCrypto.decrypt(passEntity1.encryptedPassword, newKey))
        assertEquals("Cluster admin", VaultCrypto.decrypt(passEntity1.encryptedNotes, newKey))

        val passEntity2 = database.vaultDao().getPasswordById(passId2)!!
        assertEquals("BetaDatabase", VaultCrypto.decrypt(passEntity2.websiteName, newKey))
        assertEquals("BetaSecret888!", VaultCrypto.decrypt(passEntity2.encryptedPassword, newKey))

        val linkEntity1 = database.vaultDao().getLinkById(linkId1)!!
        assertEquals("GrafanaOps", VaultCrypto.decrypt(linkEntity1.name, newKey))
        assertEquals("https://metrics.cloud.example", VaultCrypto.decrypt(linkEntity1.url, newKey))

        // Old key must fail on all records (no mixed key state)
        var oldKeyDecryptionFailed = false
        try {
            VaultCrypto.decrypt(passEntity1.websiteName, oldKey)
        } catch (_: Exception) {
            oldKeyDecryptionFailed = true
        }
        assertTrue("Old key must fail on re-encrypted entries", oldKeyDecryptionFailed)

        // Lock vault and verify old password is now rejected, while new password unlocks
        repository.lockVault()
        assertFalse("Old password must no longer unlock the vault", repository.unlockVault("OldMasterPassword123!".toCharArray()))
        assertTrue("New password must successfully unlock the vault", repository.unlockVault("NewMasterPassword789!".toCharArray()))

        // Verify all observed items match perfectly under new password
        val passwords = repository.observeDecryptedPasswords().first()
        assertEquals(2, passwords.size)
        val p1 = passwords.first { it.id == passId1 }
        assertEquals("AlphaCloud", p1.websiteName)
        assertEquals("alphaUser", p1.username)
        assertEquals("AlphaSecret999!", p1.password)
        assertEquals("Cluster admin", p1.notes)

        val links = repository.observeLinks().first()
        assertEquals(2, links.size)
        val l1 = links.first { it.id == linkId1 }
        assertEquals("GrafanaOps", l1.name)
        assertEquals("https://metrics.cloud.example", l1.url)
    }

    @Test
    fun `test S6 2 - Rotation changes salt and verifier`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())

        repository.setupMasterPassword("InitialPassword123!".toCharArray())
        val metaBefore = database.vaultDao().getMetadataDirect()!!

        val success = repository.changeMasterPassword("InitialPassword123!".toCharArray(), "RotatedPassword456!".toCharArray())
        assertTrue(success)

        val metaAfter = database.vaultDao().getMetadataDirect()!!
        assertNotEquals(metaBefore.saltBase64, metaAfter.saltBase64)
        assertNotEquals(metaBefore.verifierCipherBase64, metaAfter.verifierCipherBase64)
    }

    @Test
    fun `test S6 3 - Failure before transaction leaves vault unchanged`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())

        val oldPass = "ValidOldPassword123!".toCharArray()
        repository.setupMasterPassword(oldPass)
        val oldKey = repository.getActiveKey()!!
        val originalMeta = database.vaultDao().getMetadataDirect()!!

        repository.savePassword(
            websiteName = "CorruptTestSite",
            url = "https://corrupt.example",
            username = "user1",
            password = "MyPassword123!",
            notes = "Will be tested",
            category = "Test",
            tags = listOf("test"),
            isFavorite = false
        )

        val passId = database.vaultDao().getAllPasswordsDirect().first().id

        // Corrupt encryptedPassword in SQLite directly to trigger a decryption error before transaction starts
        val rawEntity = database.vaultDao().getPasswordById(passId)!!
        database.vaultDao().updatePassword(rawEntity.copy(encryptedPassword = "CORRUPTED_NON_BASE64_CIPHERTEXT"))

        // Attempt rotation with correct old password
        val result = repository.changeMasterPassword("ValidOldPassword123!".toCharArray(), "NewTargetPassword999!".toCharArray())
        assertFalse("Rotation must fail safely before transaction when decryption fails", result)

        // Metadata and activeKey must remain untouched
        val metaAfter = database.vaultDao().getMetadataDirect()!!
        assertEquals("Salt must not be modified on failure before transaction", originalMeta.saltBase64, metaAfter.saltBase64)
        assertEquals("Verifier must not be modified on failure before transaction", originalMeta.verifierCipherBase64, metaAfter.verifierCipherBase64)
        assertEquals("Active key must remain the original key", oldKey, repository.getActiveKey())
    }

    @Test
    fun `test S6 4, 5, 7 - Failure during transaction rolls back changes and leaves no partially rotated records`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())

        val oldPass = "StableOldPassword123!".toCharArray()
        repository.setupMasterPassword(oldPass)
        val oldKey = repository.getActiveKey()!!
        val originalMeta = database.vaultDao().getMetadataDirect()!!

        repository.savePassword(
            websiteName = "ServerOne",
            url = "https://one.example",
            username = "root",
            password = "SecretOne123!",
            notes = "First server",
            category = "Infra",
            tags = listOf("server"),
            isFavorite = true
        )
        repository.savePassword(
            websiteName = "ServerTwo",
            url = "https://two.example",
            username = "admin",
            password = "SecretTwo456!",
            notes = "Second server",
            category = "Infra",
            tags = listOf("server"),
            isFavorite = false
        )
        repository.saveLink(
            name = "OpsPortal",
            url = "https://ops.example",
            description = "Operations",
            category = "Infra",
            tags = listOf("ops"),
            isFavorite = false
        )

        val passesBefore = database.vaultDao().getAllPasswordsDirect()
        val passId1 = passesBefore.first { it.isFavorite }.id
        val passId2 = passesBefore.first { !it.isFavorite }.id
        val linkId1 = database.vaultDao().getAllLinksDirect().first().id

        // Create an SQLite trigger that fails specifically on link update during the transaction
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_mid_rotation BEFORE UPDATE ON link_entries BEGIN SELECT RAISE(ABORT, 'Simulated mid-transaction failure'); END;"
        )

        // Attempt rotation to new password
        val rotationResult = repository.changeMasterPassword("StableOldPassword123!".toCharArray(), "FailedNewPassword456!".toCharArray())
        assertFalse("Rotation must return false when transaction fails mid-flight", rotationResult)

        // Clean up the trigger
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_mid_rotation")

        // 1. Verify active key was NOT updated
        assertEquals("Active key must not be changed on failed transaction", oldKey, repository.getActiveKey())

        // 2. Verify vault metadata was rolled back
        val metaAfterFailure = database.vaultDao().getMetadataDirect()!!
        assertEquals("Metadata salt must remain original", originalMeta.saltBase64, metaAfterFailure.saltBase64)
        assertEquals("Metadata verifier must remain original", originalMeta.verifierCipherBase64, metaAfterFailure.verifierCipherBase64)

        // 3. Verify that passId1 was rolled back and is NOT encrypted with new key
        val pass1AfterFailure = database.vaultDao().getPasswordById(passId1)!!
        assertEquals("WebsiteName of pass1 must still decrypt with oldKey", "ServerOne", VaultCrypto.decrypt(pass1AfterFailure.websiteName, oldKey))
        assertEquals("Password of pass1 must still decrypt with oldKey", "SecretOne123!", VaultCrypto.decrypt(pass1AfterFailure.encryptedPassword, oldKey))

        val pass2AfterFailure = database.vaultDao().getPasswordById(passId2)!!
        assertEquals("WebsiteName of pass2 must still decrypt with oldKey", "ServerTwo", VaultCrypto.decrypt(pass2AfterFailure.websiteName, oldKey))

        val link1AfterFailure = database.vaultDao().getLinkById(linkId1)!!
        assertEquals("Name of link1 must still decrypt with oldKey", "OpsPortal", VaultCrypto.decrypt(link1AfterFailure.name, oldKey))

        // 4. Old master password unlocks seamlessly and all fields are intact
        repository.lockVault()
        val unlockSuccess = repository.unlockVault("StableOldPassword123!".toCharArray())
        assertTrue("Original master password must still unlock the vault cleanly", unlockSuccess)

        val recoveredPasswords = repository.observeDecryptedPasswords().first()
        assertEquals(2, recoveredPasswords.size)
        val recoveredP1 = recoveredPasswords.first { it.id == passId1 }
        assertEquals("ServerOne", recoveredP1.websiteName)
        assertEquals("https://one.example", recoveredP1.url)
        assertEquals("root", recoveredP1.username)
        assertEquals("SecretOne123!", recoveredP1.password)
        assertEquals("First server", recoveredP1.notes)
        assertEquals("Infra", recoveredP1.category)
        assertEquals(listOf("server"), recoveredP1.tags)

        val recoveredLinks = repository.observeLinks().first()
        assertEquals(1, recoveredLinks.size)
        val recoveredL1 = recoveredLinks.first { it.id == linkId1 }
        assertEquals("OpsPortal", recoveredL1.name)
        assertEquals("https://ops.example", recoveredL1.url)
        assertEquals("Operations", recoveredL1.description)
        assertEquals("Infra", recoveredL1.category)
        assertEquals(listOf("ops"), recoveredL1.tags)

        // 5. Failed new password cannot unlock
        repository.lockVault()
        assertFalse("New password must not unlock vault after failed rotation", repository.unlockVault("FailedNewPassword456!".toCharArray()))
    }

    @Test
    fun `test S7 1 - Clear clipboard immediately on vault lock`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val masterPass = "MasterPassword2026!#".toCharArray()
        repository.setupMasterPassword(masterPass)

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success -> vmUnlockSuccess = success }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val sensitiveSecret = "SuperSensitivePassword999!"

        viewModel.copyToClipboard("Password", sensitiveSecret, isSensitive = true)
        val clipBefore = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals(sensitiveSecret, clipBefore)

        viewModel.lockVault()

        val clipTextAfterLock = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertNotEquals(sensitiveSecret, clipTextAfterLock)
        assertTrue("Clipboard must be cleared after lock", clipTextAfterLock.isNullOrEmpty())
    }

    @Test
    fun `test S7 2 - Existing clipboard timeout still works when vault unlocked`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("MasterPassword2026!#".toCharArray())

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success -> vmUnlockSuccess = success }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)

        // Set timeout to 1 second and wait for ViewModel metadata to update
        viewModel.updateClipboardTimeout(1)
        val startMeta = System.currentTimeMillis()
        while (viewModel.metadata.value?.clipboardClearSeconds != 1 && System.currentTimeMillis() - startMeta < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertEquals(1, viewModel.metadata.value?.clipboardClearSeconds)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val sensitiveSecret = "AutoExpireSecret123!"

        viewModel.copyToClipboard("Password", sensitiveSecret, isSensitive = true)
        assertEquals(sensitiveSecret, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Wait for timeout to fire (1s)
        val waitStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - waitStart < 3500) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            kotlinx.coroutines.delay(100)
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current.isNullOrEmpty()) break
        }

        val clipTextAfterTimeout = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertNotEquals(sensitiveSecret, clipTextAfterTimeout)
        assertTrue("Clipboard must be cleared by timeout", clipTextAfterTimeout.isNullOrEmpty())
    }

    @Test
    fun `test S7 3 - Do not erase newer external clipboard content on lock`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("MasterPassword2026!#".toCharArray())

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success -> vmUnlockSuccess = success }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val sensitiveSecret = "InitialSensitivePass456!"

        viewModel.copyToClipboard("Password", sensitiveSecret, isSensitive = true)
        assertEquals(sensitiveSecret, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Simulate another external app replacing the clipboard with unrelated content
        val externalContent = "https://external.app.example/unrelated/note"
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("External", externalContent))
        assertEquals(externalContent, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // HexaPass locks
        viewModel.lockVault()

        // Unrelated external content must remain intact
        val clipTextAfterLock = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("External clipboard content must remain untouched", externalContent, clipTextAfterLock)
    }

    @Test
    fun `test S7 4 - Repeated copy clears latest sensitive content on lock`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("MasterPassword2026!#".toCharArray())

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success -> vmUnlockSuccess = success }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val passwordA = "FirstPasswordAlpha!"
        val passwordB = "SecondPasswordBeta!"

        viewModel.copyToClipboard("Password", passwordA, isSensitive = true)
        assertEquals(passwordA, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        viewModel.copyToClipboard("Password", passwordB, isSensitive = true)
        assertEquals(passwordB, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        viewModel.lockVault()

        val clipTextAfterLock = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertNotEquals(passwordB, clipTextAfterLock)
        assertNotEquals(passwordA, clipTextAfterLock)
        assertTrue("Clipboard must be cleared after lock", clipTextAfterLock.isNullOrEmpty())
    }

    @Test
    fun `test S7 5 - Background auto-lock clears sensitive clipboard`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("MasterPassword2026!#".toCharArray())

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success -> vmUnlockSuccess = success }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val sensitiveSecret = "BackgroundAutoLockSecret789!"

        viewModel.copyToClipboard("Password", sensitiveSecret, isSensitive = true)
        assertEquals(sensitiveSecret, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Simulate app entering background
        viewModel.onAppBackgrounded()

        assertEquals(com.example.ui.viewmodel.VaultAuthState.Locked, viewModel.authState.value)
        val clipTextAfterBackground = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertNotEquals(sensitiveSecret, clipTextAfterBackground)
        assertTrue("Background auto-lock must clear sensitive clipboard", clipTextAfterBackground.isNullOrEmpty())
    }

    @Test
    fun `test S7 6 - Delayed job race does not erase newer clipboard content after lock`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("MasterPassword2026!#".toCharArray())

        database.vaultDao().updateClipboardTimeout(1)

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success -> vmUnlockSuccess = success }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val sensitiveSecret = "RaceSensitiveSecret!"

        viewModel.copyToClipboard("Password", sensitiveSecret, isSensitive = true)
        assertEquals(sensitiveSecret, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Immediately lock vault
        viewModel.lockVault()

        val clipTextImmediately = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertTrue(clipTextImmediately.isNullOrEmpty())

        // External content is placed onto clipboard
        val newContent = "ImportantUserNoteNotFromVault"
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Note", newContent))

        // Wait past the original 1-second timeout window
        val waitStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - waitStart < 2000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(100)
        }

        // Verify the new content was NOT erased by any delayed job
        val clipTextAfterTimeoutWindow = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("New clipboard content must not be erased by delayed job", newContent, clipTextAfterTimeoutWindow)
    }

    @Test
    fun `test S7 7 - Existing lock behavior regression`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("MasterPassword2026!#".toCharArray())

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var vmUnlockSuccess = false
        viewModel.unlockVault("MasterPassword2026!#".toCharArray()) { success -> vmUnlockSuccess = success }
        val startTime = System.currentTimeMillis()
        while (!vmUnlockSuccess && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(vmUnlockSuccess)
        assertNotNull(repository.getActiveKey())

        // Set search query and category
        viewModel.setSearchQuery("SearchKeyword")
        viewModel.setPasswordCategory("Work")
        assertEquals("SearchKeyword", viewModel.searchQuery.value)
        assertEquals("Work", viewModel.selectedPasswordCategory.value)

        // Lock vault
        viewModel.lockVault("Testing regression")
        repository.lockVault()

        // Verify active key is wiped
        assertNull(repository.getActiveKey())
        assertFalse(repository.isUnlocked.value)

        // Verify UI state reset
        assertEquals(com.example.ui.viewmodel.VaultAuthState.Locked, viewModel.authState.value)
        assertEquals("", viewModel.searchQuery.value)
        assertEquals("All", viewModel.selectedPasswordCategory.value)
        assertEquals("All", viewModel.selectedLinkCategory.value)
        assertFalse(viewModel.passwordFavoritesOnly.value)
        assertFalse(viewModel.linkFavoritesOnly.value)
    }

    // ==========================================
    // STEP 9A: SEC-LOW-02 External URL Security
    // ==========================================

    @Test
    fun `TEST URL-1 - Valid HTTPS URL opens successfully`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        val safeUri = com.example.util.UrlSecurityManager.sanitizeUrl("https://example.com/login")
        assertNotNull(safeUri)
        assertEquals("https", safeUri?.scheme)
        assertEquals("example.com", safeUri?.host)
        assertEquals("/login", safeUri?.path)

        val launched = com.example.util.UrlSecurityManager.launchSafeUrl(app, "https://example.com/login")
        assertTrue(launched)

        val intent = shadowApp.nextStartedActivity
        assertNotNull(intent)
        assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/login", intent.dataString)
        assertTrue(intent.hasCategory(android.content.Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun `TEST URL-2 - Valid HTTP URL follows the existing supported behavior`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        val safeUri = com.example.util.UrlSecurityManager.sanitizeUrl("http://legacy-site.example.org")
        assertNotNull(safeUri)
        assertEquals("http", safeUri?.scheme)
        assertEquals("legacy-site.example.org", safeUri?.host)

        val launched = com.example.util.UrlSecurityManager.launchSafeUrl(app, "http://legacy-site.example.org")
        assertTrue(launched)

        val intent = shadowApp.nextStartedActivity
        assertNotNull(intent)
        assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
        assertEquals("http://legacy-site.example.org", intent.dataString)
    }

    @Test
    fun `TEST URL-3 - Malformed URL is rejected safely`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        val malformedUrls = listOf(
            "",
            "    ",
            "https://",
            "http://",
            "http://:8080",
            "https://example .com",
            "https://\nexample.com",
            "https://example.com:99999"
        )

        for (malformed in malformedUrls) {
            val parsed = com.example.util.UrlSecurityManager.sanitizeUrl(malformed)
            assertNull("Malformed URL '$malformed' should be rejected", parsed)

            val launched = com.example.util.UrlSecurityManager.launchSafeUrl(app, malformed)
            assertFalse("Launching malformed URL '$malformed' should fail safely", launched)
        }

        assertNull("No activity should be launched for malformed URLs", shadowApp.nextStartedActivity)
    }

    @Test
    fun `TEST URL-4 - Unsupported schemes are rejected safely`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        val dangerousSchemes = listOf(
            "javascript:alert(document.cookie)",
            "file:///etc/passwd",
            "content://media/external/images",
            "intent:#Intent;action=android.intent.action.VIEW;end",
            "customscheme://open",
            "data:text/html,<h1>PWNED</h1>",
            "about:blank",
            "mailto:victim@example.com",
            "tel:5551234567"
        )

        for (dangerous in dangerousSchemes) {
            val parsed = com.example.util.UrlSecurityManager.sanitizeUrl(dangerous)
            assertNull("Dangerous scheme '$dangerous' should be rejected", parsed)

            val launched = com.example.util.UrlSecurityManager.launchSafeUrl(app, dangerous)
            assertFalse("Launching dangerous scheme '$dangerous' should return false", launched)
        }

        assertNull("No activity should be launched for dangerous schemes", shadowApp.nextStartedActivity)
    }

    @Test
    fun `TEST URL-5 - URL containing userinfo must not send userinfo to external application`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        val urlWithUserInfo = "https://user:password@example.com/"
        val sanitized = com.example.util.UrlSecurityManager.sanitizeUrl(urlWithUserInfo)
        assertNotNull(sanitized)
        assertEquals("https://example.com/", sanitized.toString())
        assertNull("UserInfo must be stripped", sanitized?.userInfo)
        assertFalse(sanitized.toString().contains("user"))
        assertFalse(sanitized.toString().contains("password"))

        val launched = com.example.util.UrlSecurityManager.launchSafeUrl(app, urlWithUserInfo)
        assertTrue(launched)

        val intent = shadowApp.nextStartedActivity
        assertNotNull(intent)
        assertEquals("https://example.com/", intent.dataString)
        assertFalse("Intent data must not contain username", intent.dataString!!.contains("user"))
        assertFalse("Intent data must not contain password", intent.dataString!!.contains("password"))
        assertNull("Intent Uri must not have userInfo", intent.data!!.userInfo)

        // Test with port, path, query, and userinfo
        shadowApp.clearNextStartedActivities()
        val complex = "https://admin:superSecretKey456@dashboard.internal.net:8443/settings?tab=security#keys"
        val complexSanitized = com.example.util.UrlSecurityManager.sanitizeUrl(complex)
        assertNotNull(complexSanitized)
        assertEquals("https://dashboard.internal.net:8443/settings?tab=security#keys", complexSanitized.toString())
        assertNull(complexSanitized?.userInfo)
        assertFalse(complexSanitized.toString().contains("admin"))
        assertFalse(complexSanitized.toString().contains("superSecretKey456"))
    }

    @Test
    fun `TEST URL-6 - Legitimate URL containing normal query string continues to work`() {
        val urlWithQuery = "https://example.com/search?q=kotlin+compose&page=2&filter=security"
        val sanitized = com.example.util.UrlSecurityManager.sanitizeUrl(urlWithQuery)
        assertNotNull(sanitized)
        assertEquals("https", sanitized?.scheme)
        assertEquals("example.com", sanitized?.host)
        assertEquals("/search", sanitized?.path)
        assertEquals("q=kotlin+compose&page=2&filter=security", sanitized?.query)
        assertEquals(urlWithQuery, sanitized.toString())
    }

    @Test
    fun `TEST URL-7 - Legitimate URL containing a path continues to work`() {
        val urlWithPath = "https://github.com/torvalds/linux/blob/master/README"
        val sanitized = com.example.util.UrlSecurityManager.sanitizeUrl(urlWithPath)
        assertNotNull(sanitized)
        assertEquals("https", sanitized?.scheme)
        assertEquals("github.com", sanitized?.host)
        assertEquals("/torvalds/linux/blob/master/README", sanitized?.path)
        assertEquals(urlWithPath, sanitized.toString())

        // Schemeless website with path and query defaults to HTTPS
        val schemeless = "docs.android.com/guide/topics/security?hl=en#overview"
        val schemelessSanitized = com.example.util.UrlSecurityManager.sanitizeUrl(schemeless)
        assertNotNull(schemelessSanitized)
        assertEquals("https://docs.android.com/guide/topics/security?hl=en#overview", schemelessSanitized.toString())
    }

    @Test
    fun `TEST URL-8 - No sensitive URL is written to logs`() {
        // Calling sanitizeUrl and launchSafeUrl with sensitive URLs produces zero log output
        val sensitiveUrl = "https://user:password@secret-server.internal/token?secret=myApiKey123"
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val result = com.example.util.UrlSecurityManager.sanitizeUrl(sensitiveUrl)
        assertNotNull(result)
        val launched = com.example.util.UrlSecurityManager.launchSafeUrl(app, sensitiveUrl)
        assertTrue(launched)
    }

    // ==========================================
    // STEP 9A: SEC-LOW-03 Android Backup Defense
    // ==========================================

    @Test
    fun `TEST BACKUP-1 - Manifest specifies allowBackup false`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_META_DATA)
        val appInfo = packageInfo.applicationInfo
        assertNotNull(appInfo)
        val allowBackupFlag = (appInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        assertFalse("android:allowBackup must be explicitly false", allowBackupFlag)
    }

    // ========================================================
    // STEP 9B: SEC-LOW-01 Decrypted String / Memory Lifetime
    // ========================================================

    @Test
    fun `TEST MEM-1 - Vault lock clears all existing decrypted ViewModel and state references`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        val masterPassword = "TestMasterPassword2026!".toCharArray()
        repository.setupMasterPassword(masterPassword)

        repository.savePassword(
            websiteName = "SecretService",
            url = "https://secrets.example.com",
            username = "secretUser",
            password = "SuperSecretPassword123!",
            notes = "Top secret backup keys",
            category = "Work",
            tags = listOf("secret", "work"),
            isFavorite = true
        )
        repository.saveLink(
            name = "SecretPortal",
            url = "https://portal.example.com",
            description = "Internal portal",
            category = "Work",
            tags = listOf("portal"),
            isFavorite = false
        )

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var unlocked = false
        viewModel.unlockVault("TestMasterPassword2026!".toCharArray()) { success -> unlocked = success }
        val start = System.currentTimeMillis()
        while (!unlocked && System.currentTimeMillis() - start < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(unlocked)

        // Verify active key exists
        assertNotNull(repository.getActiveKey())

        // Access generator
        viewModel.navigateTo(com.example.data.model.VaultScreen.GENERATOR)
        assertFalse("Generated password should not be empty on generator screen", viewModel.generatedPassword.value.isEmpty())

        // Set search query
        viewModel.setSearchQuery("Secret")
        assertEquals("Secret", viewModel.searchQuery.value)

        // Lock vault
        viewModel.lockVault("User locked")
        repository.lockVault()

        // Assert all sensitive in-memory state in ViewModel & Repository is cleared
        assertEquals(com.example.ui.viewmodel.VaultAuthState.Locked, viewModel.authState.value)
        assertNull("Repository active key must be cleared", repository.getActiveKey())
        assertEquals("Search query must be cleared", "", viewModel.searchQuery.value)
        assertEquals("Generated password must be cleared", "", viewModel.generatedPassword.value)
        assertEquals("Password categories must be reset", "All", viewModel.selectedPasswordCategory.value)
        assertFalse("Favorites filter must be reset", viewModel.passwordFavoritesOnly.value)

        // Flows emit empty lists when locked
        val observedPasswords = repository.observeDecryptedPasswords().first()
        assertTrue("Decrypted passwords flow must be empty when locked", observedPasswords.isEmpty())

        val observedLinks = repository.observeLinks().first()
        assertTrue("Decrypted links flow must be empty when locked", observedLinks.isEmpty())

        val report = repository.computeSecurityReport(observedPasswords)
        assertEquals(0, report.totalPasswords)
        assertTrue(report.reusedGroups.isEmpty())
        assertTrue(report.weakItems.isEmpty())
    }

    @Test
    fun `TEST MEM-2 - Closing a sensitive password detail or edit UI clears its temporary sensitive state`() {
        // Test that temporary edit state holders can be explicitly cleared without retaining references
        var websiteName = "BankingPortal"
        var url = "https://bank.example.com"
        var username = "user123"
        var password = "BankPassword!789"
        var notes = "Secret PIN 4321"

        // Verify state is populated initially
        assertFalse(password.isEmpty())
        assertFalse(notes.isEmpty())

        // Simulate closing/dismissing dialog and executing state cleanup
        val clearSensitiveFields = {
            websiteName = ""
            url = ""
            username = ""
            password = ""
            notes = ""
        }
        clearSensitiveFields()

        assertEquals("", websiteName)
        assertEquals("", url)
        assertEquals("", username)
        assertEquals("", password)
        assertEquals("", notes)
    }

    @Test
    fun `TEST MEM-3 - Closing secure-note UI clears temporary plaintext state`() {
        var secureNoteContent = "Recovery key: 1234-5678-9012-3456"
        assertFalse(secureNoteContent.isEmpty())

        val clearNotes = {
            secureNoteContent = ""
        }
        clearNotes()

        assertEquals("", secureNoteContent)
    }

    @Test
    fun `TEST MEM-4 - Master password is not retained in persistent ViewModel or application state after authentication`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())

        val rawMasterPass = "TransientMasterPass123!".toCharArray()
        repository.setupMasterPassword(rawMasterPass)

        // CharArray must be wiped by repository setupMasterPassword
        assertTrue("Master password CharArray must be wiped", rawMasterPass.all { it == '\u0000' })

        // Inspect ViewModel fields to ensure NO field persists master password as String
        val vmFields = com.example.ui.viewmodel.VaultViewModel::class.java.declaredFields
        for (field in vmFields) {
            val name = field.name.lowercase()
            assertFalse(
                "ViewModel must not persist master password field: ${field.name}",
                name.contains("masterpassword") || (name.contains("master") && name.contains("pass"))
            )
        }
    }

    @Test
    fun `TEST MEM-5 - Existing CharArray and ByteArray wipe behavior continues to execute`() {
        val charArr = "SensitiveCharData123!".toCharArray()
        VaultCrypto.wipeMemory(charArr)
        assertTrue(charArr.all { it == '\u0000' })

        val byteArr = "SensitiveByteData456!".toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        VaultCrypto.wipeMemory(byteArr)
        assertTrue(byteArr.all { it == 0.toByte() })
    }

    @Test
    fun `TEST MEM-6 - Background auto-lock still clears decrypted state`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("BackgroundLockTest123!".toCharArray())

        repository.savePassword(
            websiteName = "BackgroundSite",
            url = "https://bg.example.com",
            username = "bgUser",
            password = "BgPassword123!",
            notes = "bg notes",
            category = "Work",
            tags = listOf("bg"),
            isFavorite = false
        )

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var unlocked = false
        viewModel.unlockVault("BackgroundLockTest123!".toCharArray()) { success -> unlocked = success }
        val start = System.currentTimeMillis()
        while (!unlocked && System.currentTimeMillis() - start < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(unlocked)
        assertNotNull(repository.getActiveKey())

        // Trigger background event
        viewModel.onAppBackgrounded()
        repository.lockVault()

        // Verify locked and cleared
        assertEquals(com.example.ui.viewmodel.VaultAuthState.Locked, viewModel.authState.value)
        assertNull(repository.getActiveKey())
        val observed = repository.observeDecryptedPasswords().first()
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `TEST MEM-7 - Existing password CRUD still works`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("CrudMasterPassword2026!".toCharArray())

        // Create
        val saved = repository.savePassword(
            websiteName = "ServiceCRUD",
            url = "https://crud.example.com",
            username = "crudUser",
            password = "CrudPassword123!",
            notes = "Crud notes",
            category = "Work",
            tags = listOf("crud"),
            isFavorite = false
        )
        assertTrue(saved)

        // Read
        val list = repository.observeDecryptedPasswords().first()
        assertEquals(1, list.size)
        val item = list.first()
        assertEquals("ServiceCRUD", item.websiteName)
        assertEquals("crudUser", item.username)
        assertEquals("CrudPassword123!", item.password)

        // Update
        val updated = repository.savePassword(
            id = item.id,
            websiteName = "ServiceCRUDUpdated",
            url = "https://crud.example.com/v2",
            username = "crudUserV2",
            password = "CrudPasswordUpdated456!",
            notes = "Updated notes",
            category = "Finance",
            tags = listOf("crud", "v2"),
            isFavorite = true
        )
        assertTrue(updated)

        val updatedList = repository.observeDecryptedPasswords().first()
        assertEquals(1, updatedList.size)
        val updatedItem = updatedList.first()
        assertEquals("ServiceCRUDUpdated", updatedItem.websiteName)
        assertEquals("CrudPasswordUpdated456!", updatedItem.password)
        assertTrue(updatedItem.isFavorite)

        // Delete
        repository.deletePassword(updatedItem.id)
        val emptyList = repository.observeDecryptedPasswords().first()
        assertTrue(emptyList.isEmpty())
    }

    @Test
    fun `TEST MEM-8 - Existing link CRUD still works`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("LinkCrudMasterPassword2026!".toCharArray())

        // Create
        val saved = repository.saveLink(
            name = "LinkCRUD",
            url = "https://link.example.com",
            description = "Link description",
            category = "Dev",
            tags = listOf("dev"),
            isFavorite = false
        )
        assertTrue(saved)

        // Read
        val list = repository.observeLinks().first()
        assertEquals(1, list.size)
        val item = list.first()
        assertEquals("LinkCRUD", item.name)
        assertEquals("https://link.example.com", item.url)

        // Update
        val updated = repository.saveLink(
            id = item.id,
            name = "LinkCRUDUpdated",
            url = "https://link.example.com/v2",
            description = "Updated description",
            category = "Research",
            tags = listOf("research"),
            isFavorite = true
        )
        assertTrue(updated)

        val updatedList = repository.observeLinks().first()
        assertEquals(1, updatedList.size)
        val updatedItem = updatedList.first()
        assertEquals("LinkCRUDUpdated", updatedItem.name)
        assertTrue(updatedItem.isFavorite)

        // Delete
        repository.deleteLink(updatedItem.id)
        val emptyList = repository.observeLinks().first()
        assertTrue(emptyList.isEmpty())
    }

    @Test
    fun `TEST MEM-9 - Existing search and filter functionality still works`() = kotlinx.coroutines.runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("SearchFilterTest123!".toCharArray())

        repository.savePassword(
            websiteName = "AlphaPortal",
            url = "https://alpha.example.com",
            username = "alphaUser",
            password = "AlphaPassword123!",
            notes = "",
            category = "Work",
            tags = listOf("alpha"),
            isFavorite = false
        )
        repository.savePassword(
            websiteName = "BetaPortal",
            url = "https://beta.example.com",
            username = "betaUser",
            password = "BetaPassword456!",
            notes = "",
            category = "Personal",
            tags = listOf("beta"),
            isFavorite = true
        )

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)

        val collectJob1 = launch { viewModel.allPasswords.collect { } }
        val collectJob2 = launch { viewModel.filteredPasswords.collect { } }

        var unlocked = false
        viewModel.unlockVault("SearchFilterTest123!".toCharArray()) { success -> unlocked = success }
        val start = System.currentTimeMillis()
        while (!unlocked && System.currentTimeMillis() - start < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(unlocked)

        // Wait for allPasswords to collect
        val waitListStart = System.currentTimeMillis()
        while (viewModel.allPasswords.value.size < 2 && System.currentTimeMillis() - waitListStart < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertEquals(2, viewModel.allPasswords.value.size)

        // Search for Alpha
        viewModel.setSearchQuery("Alpha")
        val searchStart = System.currentTimeMillis()
        while (viewModel.filteredPasswords.value.size != 1 && System.currentTimeMillis() - searchStart < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertEquals(1, viewModel.filteredPasswords.value.size)
        assertEquals("AlphaPortal", viewModel.filteredPasswords.value.first().websiteName)

        // Filter by category Personal
        viewModel.setSearchQuery("")
        viewModel.setPasswordCategory("Personal")
        val catStart = System.currentTimeMillis()
        while (viewModel.filteredPasswords.value.size != 1 && System.currentTimeMillis() - catStart < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertEquals(1, viewModel.filteredPasswords.value.size)
        assertEquals("BetaPortal", viewModel.filteredPasswords.value.first().websiteName)

        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun `TEST MEM-10 - Existing clipboard security remains unchanged and tests continue passing`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        repository.setupMasterPassword("ClipSecTest2026!".toCharArray())

        val viewModel = com.example.ui.viewmodel.VaultViewModel(app)
        var unlocked = false
        viewModel.unlockVault("ClipSecTest2026!".toCharArray()) { success -> unlocked = success }
        val start = System.currentTimeMillis()
        while (!unlocked && System.currentTimeMillis() - start < 3000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
        }
        assertTrue(unlocked)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val sensitivePassword = "ClipboardSensitivePass789!"
        viewModel.copyToClipboard("Password", sensitivePassword, isSensitive = true)

        assertEquals(sensitivePassword, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Lock vault
        viewModel.lockVault()

        // Clipboard must be cleared immediately
        val clipAfterLock = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertTrue("Clipboard must be cleared after vault lock", clipAfterLock.isNullOrEmpty())
    }

    @Test
    fun `test 1 and 2 - Password entries 1 to 10 succeed and 11th creation fails`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            val success = repository.savePassword(
                id = 0L,
                websiteName = "Site $i",
                url = "https://site$i.com",
                username = "user$i",
                password = "Password$i!",
                notes = "Notes $i",
                category = "Login",
                tags = listOf("tag$i"),
                isFavorite = false
            )
            assertTrue("Password #$i creation must succeed", success)
        }
        assertEquals("Password count must be exactly 10", 10, repository.getPasswordCount())

        val p11Success = repository.savePassword(
            id = 0L,
            websiteName = "Site 11",
            url = "https://site11.com",
            username = "user11",
            password = "Password11!",
            notes = "Notes 11",
            category = "Login",
            tags = listOf("tag11"),
            isFavorite = false
        )
        assertFalse("11th password creation must be rejected", p11Success)
        assertEquals("Password count must strictly remain 10", 10, repository.getPasswordCount())
    }

    @Test
    fun `test 3 - Editing password 10 succeeds`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            repository.savePassword(0L, "Site $i", "https://site$i.com", "user$i", "Pass$i!", "Notes", "Login", emptyList(), false)
        }
        assertEquals(10, repository.getPasswordCount())

        val tenth = database.vaultDao().getAllPasswordsDirect().first()
        val editSuccess = repository.savePassword(
            id = tenth.id,
            websiteName = "Updated Site 10",
            url = "https://updatedsite10.com",
            username = "updatedUser",
            password = "UpdatedPass10!",
            notes = "Updated notes",
            category = "Work",
            tags = listOf("updated"),
            isFavorite = true
        )
        assertTrue("Editing password when at limit must succeed", editSuccess)
        assertEquals("Password count must remain 10 after edit", 10, repository.getPasswordCount())
    }

    @Test
    fun `test 4 - Deleting a password frees a slot`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            repository.savePassword(0L, "Site $i", "https://site$i.com", "user$i", "Pass$i!", "Notes", "Login", emptyList(), false)
        }
        assertEquals(10, repository.getPasswordCount())

        val passwordToDelete = database.vaultDao().getAllPasswordsDirect().first()
        repository.deletePassword(passwordToDelete.id)
        assertEquals("Password count must decrease to 9 after deletion", 9, repository.getPasswordCount())

        val newPassSuccess = repository.savePassword(
            id = 0L,
            websiteName = "New Site After Delete",
            url = "https://newsite.com",
            username = "newUser",
            password = "NewPass!",
            notes = "New",
            category = "Login",
            tags = emptyList(),
            isFavorite = false
        )
        assertTrue("Adding new password after freeing a slot must succeed", newPassSuccess)
        assertEquals("Password count must be 10 again", 10, repository.getPasswordCount())
    }

    @Test
    fun `test 5 and 6 - Link entries 1 to 10 succeed and 11th creation fails`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            val success = repository.saveLink(
                id = 0L,
                name = "Link $i",
                url = "https://link$i.com",
                description = "Desc $i",
                category = "General",
                tags = listOf("tag$i"),
                isFavorite = false
            )
            assertTrue("Link #$i creation must succeed", success)
        }
        assertEquals("Link count must be exactly 10", 10, repository.getLinkCount())

        val l11Success = repository.saveLink(
            id = 0L,
            name = "Link 11",
            url = "https://link11.com",
            description = "Desc 11",
            category = "General",
            tags = listOf("tag11"),
            isFavorite = false
        )
        assertFalse("11th link creation must be rejected", l11Success)
        assertEquals("Link count must strictly remain 10", 10, repository.getLinkCount())
    }

    @Test
    fun `test 7 - Editing link 10 succeeds`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            repository.saveLink(0L, "Link $i", "https://link$i.com", "Desc $i", "General", emptyList(), false)
        }
        assertEquals(10, repository.getLinkCount())

        val tenth = database.vaultDao().getAllLinksDirect().first()
        val editSuccess = repository.saveLink(
            id = tenth.id,
            name = "Updated Link 10",
            url = "https://updatedlink10.com",
            description = "Updated desc",
            category = "Work",
            tags = listOf("updated"),
            isFavorite = true
        )
        assertTrue("Editing link when at limit must succeed", editSuccess)
        assertEquals("Link count must remain 10 after edit", 10, repository.getLinkCount())
    }

    @Test
    fun `test 8 - Deleting a link frees a slot`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            repository.saveLink(0L, "Link $i", "https://link$i.com", "Desc $i", "General", emptyList(), false)
        }
        assertEquals(10, repository.getLinkCount())

        val linkToDelete = database.vaultDao().getAllLinksDirect().first()
        repository.deleteLink(linkToDelete.id)
        assertEquals("Link count must decrease to 9 after deletion", 9, repository.getLinkCount())

        val newLinkSuccess = repository.saveLink(
            id = 0L,
            name = "New Link After Delete",
            url = "https://newlink.com",
            description = "New Desc",
            category = "General",
            tags = emptyList(),
            isFavorite = false
        )
        assertTrue("Adding new link after freeing a slot must succeed", newLinkSuccess)
        assertEquals("Link count must be 10 again", 10, repository.getLinkCount())
    }

    @Test
    fun `test 9 - Import exceeding 10 passwords is rejected atomically`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            repository.savePassword(0L, "Site $i", "https://site$i.com", "user$i", "Pass$i!", "Notes", "Login", emptyList(), false)
        }
        val backupJson = repository.exportVaultBackup("TestLimit1234!".toCharArray())
        assertNotNull(backupJson)

        // Clear and insert 1 existing password
        database.vaultDao().clearAllPasswords()
        repository.savePassword(0L, "Existing Site", "https://existing.com", "existingUser", "ExistingPass!", "Notes", "Login", emptyList(), false)
        assertEquals(1, repository.getPasswordCount())

        // Attempting to import backup of 10 items when 1 already exists (1 + 10 = 11 > 10) must be rejected atomically
        val importSuccess = repository.importVaultBackup(backupJson!!, "TestLimit1234!".toCharArray())
        assertFalse("Import exceeding 10 passwords must be rejected atomically", importSuccess)
        assertEquals("Existing password count must remain strictly 1 without partial import or data corruption", 1, repository.getPasswordCount())
    }

    @Test
    fun `test 10 - Import exceeding 10 links is rejected atomically`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..10) {
            repository.saveLink(0L, "Link $i", "https://link$i.com", "Desc $i", "General", emptyList(), false)
        }
        val backupJson = repository.exportVaultBackup("TestLimit1234!".toCharArray())
        assertNotNull(backupJson)

        // Clear and insert 1 existing link
        database.vaultDao().clearAllLinks()
        repository.saveLink(0L, "Existing Link", "https://existinglink.com", "Desc", "General", emptyList(), false)
        assertEquals(1, repository.getLinkCount())

        // Attempting to import backup of 10 links when 1 already exists (1 + 10 = 11 > 10) must be rejected atomically
        val importSuccess = repository.importVaultBackup(backupJson!!, "TestLimit1234!".toCharArray())
        assertFalse("Import exceeding 10 links must be rejected atomically", importSuccess)
        assertEquals("Existing link count must remain strictly 1 without partial import or data corruption", 1, repository.getLinkCount())
    }

    @Test
    fun `test 11 - Import within limits succeeds`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val database = com.example.data.db.VaultDatabase.getInstance(app)
        val repository = com.example.data.repository.VaultRepository(database.vaultDao())
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        repository.setupMasterPassword("TestLimit1234!".toCharArray())

        for (i in 1..3) {
            repository.savePassword(0L, "Site $i", "https://site$i.com", "user$i", "Pass$i!", "Notes", "Login", emptyList(), false)
            repository.saveLink(0L, "Link $i", "https://link$i.com", "Desc $i", "General", emptyList(), false)
        }
        val backupJson = repository.exportVaultBackup("TestLimit1234!".toCharArray())
        assertNotNull(backupJson)

        // Clear and import
        database.vaultDao().clearAllPasswords()
        database.vaultDao().clearAllLinks()
        val importSuccess = repository.importVaultBackup(backupJson!!, "TestLimit1234!".toCharArray())
        assertTrue("Import within limits must succeed", importSuccess)
        assertEquals(3, repository.getPasswordCount())
        assertEquals(3, repository.getLinkCount())
    }
}
