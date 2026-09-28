package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.BiometricSecurityManager
import com.example.data.crypto.PasswordStrength
import com.example.data.crypto.VaultCrypto
import com.example.data.db.VaultDatabase
import com.example.data.model.DecryptedPasswordItem
import com.example.data.model.LinkItem
import com.example.data.model.SecurityReport
import com.example.data.model.VaultScreen
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface VaultAuthState {
    object Loading : VaultAuthState
    object FirstTimeSetup : VaultAuthState
    object Locked : VaultAuthState
    object Unlocked : VaultAuthState
}

enum class PasswordSortOption(val label: String) {
    RECENT("Recently Updated"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    STRENGTH("Security Strength")
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VaultDatabase.getInstance(application)
    private val repository = VaultRepository(database.vaultDao())

    val metadata = repository.metadataFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    private val _authState = MutableStateFlow<VaultAuthState>(VaultAuthState.Loading)
    val authState: StateFlow<VaultAuthState> = _authState.asStateFlow()

    private val _currentScreen = MutableStateFlow(VaultScreen.PASSWORDS)
    val currentScreen: StateFlow<VaultScreen> = _currentScreen.asStateFlow()

    // Search and Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedPasswordCategory = MutableStateFlow("All")
    val selectedPasswordCategory = _selectedPasswordCategory.asStateFlow()

    private val _passwordFavoritesOnly = MutableStateFlow(false)
    val passwordFavoritesOnly = _passwordFavoritesOnly.asStateFlow()

    private val _passwordSortOption = MutableStateFlow(PasswordSortOption.RECENT)
    val passwordSortOption = _passwordSortOption.asStateFlow()

    private val _selectedLinkCategory = MutableStateFlow("All")
    val selectedLinkCategory = _selectedLinkCategory.asStateFlow()

    private val _linkFavoritesOnly = MutableStateFlow(false)
    val linkFavoritesOnly = _linkFavoritesOnly.asStateFlow()

    // Passwords & Links Flows
    val allPasswords = repository.observeDecryptedPasswords().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allLinks = repository.observeLinks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Filtered Passwords
    val filteredPasswords: StateFlow<List<DecryptedPasswordItem>> = combine(
        allPasswords,
        _searchQuery,
        _selectedPasswordCategory,
        _passwordFavoritesOnly,
        _passwordSortOption
    ) { list, query, category, favOnly, sort ->
        var result = list

        if (favOnly) {
            result = result.filter { it.isFavorite }
        }
        if (category != "All") {
            result = result.filter { it.category.equals(category, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.websiteName.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        it.url.lowercase().contains(q) ||
                        it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }

        when (sort) {
            PasswordSortOption.RECENT -> result.sortedByDescending { it.updatedAt }
            PasswordSortOption.NAME_ASC -> result.sortedBy { it.websiteName.lowercase() }
            PasswordSortOption.NAME_DESC -> result.sortedByDescending { it.websiteName.lowercase() }
            PasswordSortOption.STRENGTH -> result.sortedBy { it.strength.score }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Links
    val filteredLinks: StateFlow<List<LinkItem>> = combine(
        allLinks,
        _searchQuery,
        _selectedLinkCategory,
        _linkFavoritesOnly
    ) { list, query, category, favOnly ->
        var result = list

        if (favOnly) {
            result = result.filter { it.isFavorite }
        }
        if (category != "All") {
            result = result.filter { it.category.equals(category, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.name.lowercase().contains(q) ||
                        it.url.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }
        result.sortedByDescending { it.updatedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Security Report
    val securityReport: StateFlow<SecurityReport> = allPasswords.map { list ->
        repository.computeSecurityReport(list)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        repository.computeSecurityReport(emptyList())
    )

    // Generator State
    private val _generatorLength = MutableStateFlow(16)
    val generatorLength = _generatorLength.asStateFlow()

    private val _genUpper = MutableStateFlow(true)
    val genUpper = _genUpper.asStateFlow()

    private val _genLower = MutableStateFlow(true)
    val genLower = _genLower.asStateFlow()

    private val _genNumbers = MutableStateFlow(true)
    val genNumbers = _genNumbers.asStateFlow()

    private val _genSymbols = MutableStateFlow(true)
    val genSymbols = _genSymbols.asStateFlow()

    private val _genExcludeLookalike = MutableStateFlow(false)
    val genExcludeLookalike = _genExcludeLookalike.asStateFlow()

    private val _generatedPassword = MutableStateFlow("")
    val generatedPassword = _generatedPassword.asStateFlow()

    private val _generatedPasswordStrength = MutableStateFlow(VaultCrypto.evaluateStrength(""))
    val generatedPasswordStrength = _generatedPasswordStrength.asStateFlow()

    // Clipboard & Notification State
    private val _securityNotice = MutableStateFlow<String?>(null)
    val securityNotice = _securityNotice.asStateFlow()

    // Biometric Management States (must precede init block)
    private val _isBiometricSupported = MutableStateFlow(false)
    val isBiometricSupported: StateFlow<Boolean> = _isBiometricSupported.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private var clipboardJob: Job? = null
    private var lastCopiedText: String? = null
    private var isLastCopiedSensitive: Boolean = false

    // Inactivity Auto-Lock
    private var lastActivityTime = System.currentTimeMillis()

    // Background Auto-Lock Observer via ProcessLifecycleOwner
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            onAppBackgrounded()
        }
    }

    init {
        registerLifecycleObserver()
        refreshBiometricState()
        checkInitialAuthState()
        startInactivityMonitor()
    }

    private fun registerLifecycleObserver() {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
            } else {
                Handler(Looper.getMainLooper()).post {
                    try {
                        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun onAppBackgrounded() {
        if (_authState.value == VaultAuthState.Unlocked) {
            lockVault("Vault locked upon leaving application")
        }
    }

    private fun checkInitialAuthState() {
        viewModelScope.launch {
            val isConfigured = repository.isVaultConfigured()
            _authState.value = if (isConfigured) {
                VaultAuthState.Locked
            } else {
                VaultAuthState.FirstTimeSetup
            }
        }
    }

    fun recordUserActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    private fun startInactivityMonitor() {
        viewModelScope.launch {
            while (isActive) {
                delay(4000)
                if (_authState.value == VaultAuthState.Unlocked) {
                    val meta = metadata.value
                    val timeoutMinutes = meta?.autoLockMinutes ?: 5
                    if (timeoutMinutes > 0) {
                        val idleMillis = System.currentTimeMillis() - lastActivityTime
                        if (idleMillis >= timeoutMinutes * 60 * 1000L) {
                            lockVault("Vault automatically locked due to inactivity")
                        }
                    }
                }
            }
        }
    }

    fun setupMasterPassword(password: CharArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.setupMasterPassword(password)
            if (success) {
                _authState.value = VaultAuthState.Unlocked
                recordUserActivity()
            }
            onResult(success)
        }
    }

    fun unlockVault(password: CharArray, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.unlockVault(password)
            if (success) {
                _authState.value = VaultAuthState.Unlocked
                recordUserActivity()
            }
            onResult(success)
        }
    }

    fun lockVault(reason: String = "Vault locked") {
        // 1 & 2. Detect whether HexaPass has sensitive clipboard content active and safely clear it
        clearSensitiveClipboardIfOwned()

        // 3. Cancel the delayed clipboard-clear job
        clipboardJob?.cancel()
        clipboardJob = null

        // 4. Clear clipboard-related in-memory state
        lastCopiedText = null
        isLastCopiedSensitive = false

        // 5 & 6. Continue the existing vault-lock process
        _generatedPassword.value = ""
        _generatedPasswordStrength.value = VaultCrypto.evaluateStrength("")
        repository.lockVault()
        _authState.value = VaultAuthState.Locked
        _securityNotice.value = reason
        _searchQuery.value = ""
        _selectedPasswordCategory.value = "All"
        _selectedLinkCategory.value = "All"
        _passwordFavoritesOnly.value = false
        _linkFavoritesOnly.value = false
        _currentScreen.value = VaultScreen.PASSWORDS
    }

    fun navigateTo(screen: VaultScreen) {
        recordUserActivity()
        _currentScreen.value = screen
        if (screen == VaultScreen.GENERATOR && _generatedPassword.value.isEmpty()) {
            regeneratePassword()
        }
    }

    fun setSearchQuery(query: String) {
        recordUserActivity()
        _searchQuery.value = query
    }

    fun setPasswordCategory(cat: String) {
        recordUserActivity()
        _selectedPasswordCategory.value = cat
    }

    fun togglePasswordFavoritesOnly() {
        recordUserActivity()
        _passwordFavoritesOnly.value = !_passwordFavoritesOnly.value
    }

    fun setPasswordSort(sort: PasswordSortOption) {
        recordUserActivity()
        _passwordSortOption.value = sort
    }

    fun setLinkCategory(cat: String) {
        recordUserActivity()
        _selectedLinkCategory.value = cat
    }

    fun toggleLinkFavoritesOnly() {
        recordUserActivity()
        _linkFavoritesOnly.value = !_linkFavoritesOnly.value
    }

    // Generator Controls
    fun setGeneratorLength(length: Int) {
        recordUserActivity()
        _generatorLength.value = length.coerceIn(4, 64)
        regeneratePassword()
    }

    fun toggleGenUpper() {
        recordUserActivity()
        if (_genUpper.value && !_genLower.value && !_genNumbers.value && !_genSymbols.value) return
        _genUpper.value = !_genUpper.value
        regeneratePassword()
    }

    fun toggleGenLower() {
        recordUserActivity()
        if (!_genUpper.value && _genLower.value && !_genNumbers.value && !_genSymbols.value) return
        _genLower.value = !_genLower.value
        regeneratePassword()
    }

    fun toggleGenNumbers() {
        recordUserActivity()
        if (!_genUpper.value && !_genLower.value && _genNumbers.value && !_genSymbols.value) return
        _genNumbers.value = !_genNumbers.value
        regeneratePassword()
    }

    fun toggleGenSymbols() {
        recordUserActivity()
        if (!_genUpper.value && !_genLower.value && !_genNumbers.value && _genSymbols.value) return
        _genSymbols.value = !_genSymbols.value
        regeneratePassword()
    }

    fun toggleGenExcludeLookalike() {
        recordUserActivity()
        _genExcludeLookalike.value = !_genExcludeLookalike.value
        regeneratePassword()
    }

    fun regeneratePassword() {
        val pass = VaultCrypto.generatePassword(
            length = _generatorLength.value,
            includeUpper = _genUpper.value,
            includeLower = _genLower.value,
            includeNumbers = _genNumbers.value,
            includeSymbols = _genSymbols.value,
            excludeLookalike = _genExcludeLookalike.value
        )
        _generatedPassword.value = pass
        _generatedPasswordStrength.value = VaultCrypto.evaluateStrength(pass)
    }

    // Secure Clipboard with Auto-Clear
    fun copyToClipboard(label: String, text: String, isSensitive: Boolean = true) {
        recordUserActivity()
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText(label, text)
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        lastCopiedText = text
        isLastCopiedSensitive = isSensitive

        val timeoutSeconds = metadata.value?.clipboardClearSeconds ?: 30
        if (isSensitive && timeoutSeconds > 0) {
            _securityNotice.value = "$label copied. Auto-clearing clipboard in ${timeoutSeconds}s"
            clipboardJob?.cancel()
            val textToClear = text
            clipboardJob = viewModelScope.launch {
                delay(timeoutSeconds * 1000L)
                val currentClip = clipboard.primaryClip
                if (currentClip != null && currentClip.itemCount > 0) {
                    val currentText = currentClip.getItemAt(0)?.text?.toString()
                    if (currentText == textToClear && isLastCopiedSensitive && lastCopiedText == textToClear) {
                        clearSystemClipboard(clipboard)
                        lastCopiedText = null
                        isLastCopiedSensitive = false
                        _securityNotice.value = "Clipboard cleared for privacy"
                    }
                }
            }
        } else {
            clipboardJob?.cancel()
            clipboardJob = null
            _securityNotice.value = "$label copied to clipboard"
        }
    }

    private fun clearSensitiveClipboardIfOwned() {
        if (!isLastCopiedSensitive) return
        val expectedText = lastCopiedText ?: return
        try {
            val context = getApplication<Application>()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val currentClip = clipboard.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0)?.text?.toString()
                if (currentText == expectedText) {
                    clearSystemClipboard(clipboard)
                }
            }
        } catch (_: Throwable) {
            // Never log sensitive data
        }
    }

    private fun clearSystemClipboard(clipboard: ClipboardManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Throwable) {
            try {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            } catch (_: Throwable) {
            }
        }
    }

    fun dismissNotice() {
        _securityNotice.value = null
    }

    // Passwords CRUD
    fun savePassword(
        id: Long = 0,
        websiteName: String,
        url: String,
        username: String,
        password: String,
        notes: String,
        category: String,
        tags: List<String>,
        isFavorite: Boolean,
        onDone: (Boolean) -> Unit
    ) {
        recordUserActivity()
        viewModelScope.launch {
            if (id == 0L && repository.getPasswordCount() >= 10) {
                _securityNotice.value = "Password limit reached. Maximum 10 password entries are allowed."
                onDone(false)
                return@launch
            }
            val success = repository.savePassword(
                id = id,
                websiteName = websiteName,
                url = url,
                username = username,
                password = password,
                notes = notes,
                category = category,
                tags = tags,
                isFavorite = isFavorite
            )
            if (!success && id == 0L) {
                _securityNotice.value = "Password limit reached. Maximum 10 password entries are allowed."
            }
            onDone(success)
        }
    }

    fun deletePassword(id: Long) {
        recordUserActivity()
        viewModelScope.launch {
            repository.deletePassword(id)
            _securityNotice.value = "Password removed from vault"
        }
    }

    fun togglePasswordFavorite(item: DecryptedPasswordItem) {
        recordUserActivity()
        viewModelScope.launch {
            repository.togglePasswordFavorite(item.id, !item.isFavorite)
        }
    }

    // Links CRUD
    fun saveLink(
        id: Long = 0,
        name: String,
        url: String,
        description: String,
        category: String,
        tags: List<String>,
        isFavorite: Boolean,
        onDone: (Boolean) -> Unit
    ) {
        recordUserActivity()
        viewModelScope.launch {
            if (id == 0L && repository.getLinkCount() >= 10) {
                _securityNotice.value = "Link limit reached. Maximum 10 link entries are allowed."
                onDone(false)
                return@launch
            }
            val success = repository.saveLink(
                id = id,
                name = name,
                url = url,
                description = description,
                category = category,
                tags = tags,
                isFavorite = isFavorite
            )
            if (!success && id == 0L) {
                _securityNotice.value = "Link limit reached. Maximum 10 link entries are allowed."
            }
            onDone(success)
        }
    }

    fun deleteLink(id: Long) {
        recordUserActivity()
        viewModelScope.launch {
            repository.deleteLink(id)
            _securityNotice.value = "Link removed from vault"
        }
    }

    fun toggleLinkFavorite(item: LinkItem) {
        recordUserActivity()
        viewModelScope.launch {
            repository.toggleLinkFavorite(item.id, !item.isFavorite)
        }
    }

    // Settings actions
    fun updateAutoLock(minutes: Int) {
        recordUserActivity()
        viewModelScope.launch {
            repository.updateAutoLock(minutes)
            _securityNotice.value = "Auto-lock updated to $minutes minutes"
        }
    }

    fun updateClipboardTimeout(seconds: Int) {
        recordUserActivity()
        viewModelScope.launch {
            repository.updateClipboardTimeout(seconds)
            _securityNotice.value = "Clipboard clear timeout updated to ${seconds}s"
        }
    }

    fun changeMasterPassword(oldPass: CharArray, newPass: CharArray, onResult: (Boolean) -> Unit) {
        recordUserActivity()
        viewModelScope.launch {
            val wasBioEnabled = _isBiometricEnabled.value
            val success = repository.changeMasterPassword(oldPass, newPass)
            if (success) {
                if (wasBioEnabled) {
                    val newKey = repository.getActiveKey()
                    if (newKey != null) {
                        BiometricSecurityManager.enableBiometrics(getApplication(), newKey)
                    }
                }
                _securityNotice.value = "Master password successfully changed"
            }
            onResult(success)
        }
    }


    fun exportVault(password: CharArray, onResult: (String?) -> Unit) {
        recordUserActivity()
        viewModelScope.launch {
            val json = repository.exportVaultBackup(password)
            onResult(json)
        }
    }

    fun exportVault(onResult: (String?) -> Unit) {
        onResult(null)
    }

    fun importVault(jsonString: String, password: CharArray, onResult: (Boolean) -> Unit) {
        recordUserActivity()
        viewModelScope.launch {
            val passCountBefore = repository.getPasswordCount()
            val linkCountBefore = repository.getLinkCount()
            val success = repository.importVaultBackup(jsonString, password)
            if (success) {
                _securityNotice.value = "Vault items imported successfully"
            } else {
                if (passCountBefore >= 10 || linkCountBefore >= 10) {
                    _securityNotice.value = "Import rejected: maximum limit of 10 password and 10 link entries reached."
                } else {
                    _securityNotice.value = "Import failed: invalid password, corrupted backup, or entry limit exceeded."
                }
            }
            onResult(success)
        }
    }

    fun importVault(jsonString: String, onResult: (Boolean) -> Unit) {
        importVault(jsonString, CharArray(0), onResult)
    }

    fun wipeVault(onDone: () -> Unit) {
        clearSensitiveClipboardIfOwned()
        clipboardJob?.cancel()
        clipboardJob = null
        lastCopiedText = null
        isLastCopiedSensitive = false
        BiometricSecurityManager.disableBiometrics(getApplication())
        _isBiometricEnabled.value = false
        viewModelScope.launch {
            repository.wipeVault()
            _authState.value = VaultAuthState.FirstTimeSetup
            _securityNotice.value = "All vault data wiped successfully"
            onDone()
        }
    }

    // Biometric Management
    fun refreshBiometricState() {
        _isBiometricSupported.value = BiometricSecurityManager.canAuthenticate(getApplication())
        _isBiometricEnabled.value = BiometricSecurityManager.isBiometricEnabled(getApplication())
    }

    fun setBiometricEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            val key = repository.getActiveKey() ?: return false
            val success = BiometricSecurityManager.enableBiometrics(getApplication(), key)
            if (success) {
                _isBiometricEnabled.value = true
                _securityNotice.value = "Biometric authentication enabled"
            }
            success
        } else {
            BiometricSecurityManager.disableBiometrics(getApplication())
            _isBiometricEnabled.value = false
            _securityNotice.value = "Biometric authentication disabled"
            true
        }
    }

    fun authenticateAndUnlockWithBiometrics(
        activity: FragmentActivity,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!_isBiometricSupported.value || !_isBiometricEnabled.value) {
            onResult(false, "Biometrics not configured")
            return
        }

        BiometricSecurityManager.authenticateWithPrompt(
            activity = activity,
            title = "Unlock HexaPass Vault",
            subtitle = "Verify fingerprint or face recognition",
            negativeButtonText = "Use Master Password",
            onSuccess = {
                val vaultKey = BiometricSecurityManager.retrieveVaultSecretKey(getApplication())
                if (vaultKey == null) {
                    onResult(false, "Failed to retrieve secure vault key")
                    return@authenticateWithPrompt
                }
                viewModelScope.launch {
                    val success = repository.unlockWithVaultKey(vaultKey)
                    if (success) {
                        _authState.value = VaultAuthState.Unlocked
                        recordUserActivity()
                        _securityNotice.value = "Vault unlocked with biometric identity"
                    }
                    onResult(success, if (success) null else "Invalid verification token")
                }
            },
            onError = { errorMsg ->
                onResult(false, errorMsg)
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
            } else {
                Handler(Looper.getMainLooper()).post {
                    try {
                        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (_: Throwable) {
        }
        clearSensitiveClipboardIfOwned()
        clipboardJob?.cancel()
        clipboardJob = null
        lastCopiedText = null
        isLastCopiedSensitive = false
        _generatedPassword.value = ""
        repository.lockVault()
    }
}

