package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.model.DecryptedPasswordItem
import com.example.data.model.VaultScreen
import com.example.ui.components.HexaBottomNav
import com.example.ui.components.HexaSecurityNotificationBar
import com.example.ui.components.HexaTopBar
import com.example.ui.screens.FirstTimeSetupScreen
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.LinksScreen
import com.example.ui.screens.PasswordsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultUnlockScreen
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.HexaPassTheme
import com.example.ui.viewmodel.VaultAuthState
import com.example.ui.viewmodel.VaultViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HexaPassTheme {
                HexaPassApp(
                    activity = this,
                    viewModel = viewModel
                )
            }
        }
    }

    fun setSecureFlag(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.recordUserActivity()
    }
}

@Composable
fun HexaPassApp(
    activity: MainActivity,
    viewModel: VaultViewModel
) {
    val authState by viewModel.authState.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val securityNotice by viewModel.securityNotice.collectAsState()

    // FLAG_SECURE: Protect the unlocked vault and sensitive credential screens from screenshots and Recents previews
    // Do not unnecessarily block non-sensitive screens
    val isSensitiveScreen = authState is VaultAuthState.Unlocked
    DisposableEffect(isSensitiveScreen) {
        activity.setSecureFlag(isSensitiveScreen)
        onDispose { }
    }

    // Passwords & Links filtered lists
    val filteredPasswords by viewModel.filteredPasswords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPasswordCategory by viewModel.selectedPasswordCategory.collectAsState()
    val passwordFavoritesOnly by viewModel.passwordFavoritesOnly.collectAsState()
    val passwordSortOption by viewModel.passwordSortOption.collectAsState()

    val filteredLinks by viewModel.filteredLinks.collectAsState()
    val selectedLinkCategory by viewModel.selectedLinkCategory.collectAsState()
    val linkFavoritesOnly by viewModel.linkFavoritesOnly.collectAsState()

    // Generator & Security
    val genPassword by viewModel.generatedPassword.collectAsState()
    val genStrength by viewModel.generatedPasswordStrength.collectAsState()
    val genLength by viewModel.generatorLength.collectAsState()
    val genUpper by viewModel.genUpper.collectAsState()
    val genLower by viewModel.genLower.collectAsState()
    val genNumbers by viewModel.genNumbers.collectAsState()
    val genSymbols by viewModel.genSymbols.collectAsState()
    val genExcludeLookalike by viewModel.genExcludeLookalike.collectAsState()
    val metadata by viewModel.metadata.collectAsState()

    var quickAddPasswordOpen by remember { mutableStateOf(false) }
    var quickAddLinkOpen by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(authState) {
        if (authState !is VaultAuthState.Unlocked) {
            quickAddPasswordOpen = false
            quickAddLinkOpen = false
        }
    }

    when (authState) {
        VaultAuthState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyberBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = CyberCyan
                )
            }
        }

        VaultAuthState.FirstTimeSetup -> {
            FirstTimeSetupScreen(
                onSetupMasterPassword = { pass, cb ->
                    viewModel.setupMasterPassword(pass, cb)
                }
            )
        }

        VaultAuthState.Locked -> {
            val isBioSupported by viewModel.isBiometricSupported.collectAsState()
            val isBioEnabled by viewModel.isBiometricEnabled.collectAsState()

            VaultUnlockScreen(
                isBiometricAvailable = isBioSupported && isBioEnabled,
                onUnlock = { pass, cb ->
                    viewModel.unlockVault(pass, cb)
                },
                onBiometricUnlock = {
                    viewModel.authenticateAndUnlockWithBiometrics(activity) { _, _ -> }
                },
                onWipeVault = {
                    viewModel.wipeVault {}
                }
            )
        }

        VaultAuthState.Unlocked -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = CyberBackground,
                topBar = {
                    Column {
                        HexaTopBar(
                            currentScreen = currentScreen,
                            onLockVault = { viewModel.lockVault() }
                        )
                        HexaSecurityNotificationBar(
                            message = securityNotice,
                            onDismiss = { viewModel.dismissNotice() }
                        )
                    }
                },
                bottomBar = {
                    HexaBottomNav(
                        currentScreen = currentScreen,
                        onSelectScreen = { viewModel.navigateTo(it) }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(CyberBackground)
                ) {
                    when (currentScreen) {
                        VaultScreen.DASHBOARD,
                        VaultScreen.SECURITY,
                        VaultScreen.PASSWORDS -> {
                            PasswordsScreen(
                                passwords = filteredPasswords,
                                searchQuery = searchQuery,
                                selectedCategory = selectedPasswordCategory,
                                favoritesOnly = passwordFavoritesOnly,
                                sortOption = passwordSortOption,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                onCategorySelect = { viewModel.setPasswordCategory(it) },
                                onToggleFavoritesOnly = { viewModel.togglePasswordFavoritesOnly() },
                                onSortSelect = { viewModel.setPasswordSort(it) },
                                onCopyUsername = { user ->
                                    viewModel.copyToClipboard(label = "Username", text = user, isSensitive = false)
                                },
                                onCopyPassword = { pass ->
                                    viewModel.copyToClipboard(label = "Password", text = pass, isSensitive = true)
                                },
                                onToggleFavorite = { item ->
                                    viewModel.togglePasswordFavorite(item)
                                },
                                onSavePassword = { id, name, url, user, pass, notes, cat, tags, fav, cb ->
                                    viewModel.savePassword(id, name, url, user, pass, notes, cat, tags, fav, cb)
                                },
                                onDeletePassword = { id ->
                                    viewModel.deletePassword(id)
                                },
                                initialAddOpen = quickAddPasswordOpen,
                                onAddDialogClosed = { quickAddPasswordOpen = false }
                            )
                        }

                        VaultScreen.LINKS -> {
                            LinksScreen(
                                links = filteredLinks,
                                searchQuery = searchQuery,
                                selectedCategory = selectedLinkCategory,
                                favoritesOnly = linkFavoritesOnly,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                onCategorySelect = { viewModel.setLinkCategory(it) },
                                onToggleFavoritesOnly = { viewModel.toggleLinkFavoritesOnly() },
                                onCopyUrl = { url ->
                                    viewModel.copyToClipboard(label = "URL", text = url, isSensitive = false)
                                },
                                onToggleFavorite = { item ->
                                    viewModel.toggleLinkFavorite(item)
                                },
                                onSaveLink = { id, name, url, desc, cat, tags, fav, cb ->
                                    viewModel.saveLink(id, name, url, desc, cat, tags, fav, cb)
                                },
                                onDeleteLink = { id ->
                                    viewModel.deleteLink(id)
                                },
                                initialAddOpen = quickAddLinkOpen,
                                onAddDialogClosed = { quickAddLinkOpen = false }
                            )
                        }

                        VaultScreen.GENERATOR -> {
                            GeneratorScreen(
                                generatedPassword = genPassword,
                                strength = genStrength,
                                length = genLength,
                                includeUpper = genUpper,
                                includeLower = genLower,
                                includeNumbers = genNumbers,
                                includeSymbols = genSymbols,
                                excludeLookalike = genExcludeLookalike,
                                onLengthChange = { viewModel.setGeneratorLength(it) },
                                onToggleUpper = { viewModel.toggleGenUpper() },
                                onToggleLower = { viewModel.toggleGenLower() },
                                onToggleNumbers = { viewModel.toggleGenNumbers() },
                                onToggleSymbols = { viewModel.toggleGenSymbols() },
                                onToggleExcludeLookalike = { viewModel.toggleGenExcludeLookalike() },
                                onRegenerate = { viewModel.regeneratePassword() },
                                onCopyPassword = { pass ->
                                    viewModel.copyToClipboard(label = "Generated Password", text = pass, isSensitive = true)
                                }
                            )
                        }

                        VaultScreen.SETTINGS -> {
                            val isBioSupported by viewModel.isBiometricSupported.collectAsState()
                            val isBioEnabled by viewModel.isBiometricEnabled.collectAsState()

                            SettingsScreen(
                                metadata = metadata,
                                isBiometricSupported = isBioSupported,
                                isBiometricEnabled = isBioEnabled,
                                onToggleBiometrics = { enabled ->
                                    viewModel.setBiometricEnabled(enabled)
                                },
                                onUpdateAutoLock = { viewModel.updateAutoLock(it) },
                                onUpdateClipboardTimeout = { viewModel.updateClipboardTimeout(it) },
                                onChangeMasterPassword = { oldPass, newPass, cb ->
                                    viewModel.changeMasterPassword(oldPass, newPass, cb)
                                },
                                onExportVault = { password, cb ->
                                    viewModel.exportVault(password, cb)
                                },
                                onImportVault = { json, password, cb ->
                                    viewModel.importVault(json, password, cb)
                                },
                                onLockVault = { viewModel.lockVault() },
                                onWipeVault = { viewModel.wipeVault {} },
                                onCopyExportedJson = { json ->
                                    viewModel.copyToClipboard(label = "Encrypted Vault Backup", text = json, isSensitive = true)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
