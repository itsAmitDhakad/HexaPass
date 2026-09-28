package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.db.VaultMetadataEntity
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberObsidian
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun SettingsScreen(
    metadata: VaultMetadataEntity?,
    isBiometricSupported: Boolean = false,
    isBiometricEnabled: Boolean = false,
    onToggleBiometrics: (Boolean) -> Unit = {},
    onUpdateAutoLock: (Int) -> Unit,
    onUpdateClipboardTimeout: (Int) -> Unit,
    onChangeMasterPassword: (CharArray, CharArray, (Boolean) -> Unit) -> Unit,
    onExportVault: (CharArray, (String?) -> Unit) -> Unit,
    onImportVault: (String, CharArray, (Boolean) -> Unit) -> Unit,
    onLockVault: () -> Unit,
    onWipeVault: () -> Unit,
    onCopyExportedJson: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    val autoLockMinutes = metadata?.autoLockMinutes ?: 5
    val clipboardSeconds = metadata?.clipboardClearSeconds ?: 30

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            exportedJsonText = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vault Security Section
        Text(
            text = "Security & Access",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = CyberCyan
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingsItemRow(
                    title = "Change Master Password",
                    subtitle = "Re-encrypts all credentials with a new key",
                    icon = Icons.Default.Key,
                    iconColor = CyberCyan,
                    onClick = { showChangePasswordDialog = true }
                )

                if (isBiometricSupported) {
                    SettingsToggleRow(
                        title = "Biometric Authentication",
                        subtitle = if (isBiometricEnabled) "Hardware-backed biometric unlock enabled" else "Use fingerprint or face recognition to unlock",
                        icon = Icons.Default.Fingerprint,
                        iconColor = CyberCyan,
                        checked = isBiometricEnabled,
                        onCheckedChange = onToggleBiometrics
                    )
                }

                SettingsItemRow(
                    title = "Auto-Lock Timeout",
                    subtitle = if (autoLockMinutes > 0) "Locks after $autoLockMinutes minutes of inactivity" else "Never auto-lock",
                    icon = Icons.Default.Timer,
                    iconColor = CyberEmerald,
                    onClick = { showAutoLockDialog = true }
                )

                SettingsItemRow(
                    title = "Clipboard Clear Timeout",
                    subtitle = if (clipboardSeconds > 0) "Wipes clipboard after $clipboardSeconds seconds" else "Never auto-clear",
                    icon = Icons.Default.Security,
                    iconColor = CyberAmber,
                    onClick = { showClipboardDialog = true },
                    isLast = true
                )
            }
        }

        // Backup & Migration Section
        Text(
            text = "Backup & Data Portability",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = CyberCyan
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingsItemRow(
                    title = "Export Encrypted Backup",
                    subtitle = "Generate authenticated container-encrypted JSON backup",
                    icon = Icons.Default.Download,
                    iconColor = CyberCyan,
                    onClick = {
                        showExportPasswordDialog = true
                    }
                )

                SettingsItemRow(
                    title = "Import Backup",
                    subtitle = "Restore passwords and links from HexaPass backup",
                    icon = Icons.Default.Upload,
                    iconColor = CyberEmerald,
                    onClick = { showImportDialog = true },
                    isLast = true
                )
            }
        }

        // Technical Cryptographic Specs
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Technical Cryptographic Architecture",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                CryptoSpecRow("Encryption Algorithm", "AES-256 GCM (Authenticated)")
                CryptoSpecRow("Key Derivation (KDF)", "PBKDF2-HMAC-SHA256 (100,000 iter)")
                CryptoSpecRow("Key Length & Salt", "256-bit Key, 128-bit Random Salt")
                CryptoSpecRow("Initialization Vector", "96-bit SecureRandom IV per record")
                CryptoSpecRow("Storage Paradigm", "Encrypted SQLite Room (Client-only)")
                CryptoSpecRow("Cloud Telemetry", "Zero network transmission, zero AI logs")
            }
        }

        // Danger Zone
        Text(
            text = "Vault Controls",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = CyberRed
        )

        Button(
            onClick = onLockVault,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("settings_lock_vault_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberSurface,
                contentColor = CyberCyan
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lock Vault Immediately", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { showWipeConfirmDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("wipe_vault_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberRed.copy(alpha = 0.15f),
                contentColor = CyberRed
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Erase & Reset Vault Data", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Change Password Dialog
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onSubmit = { oldPass, newPass, cb ->
                onChangeMasterPassword(oldPass, newPass) { success ->
                    cb(success)
                    if (success) showChangePasswordDialog = false
                }
            }
        )
    }

    // Auto Lock Dialog
    if (showAutoLockDialog) {
        OptionSelectorDialog(
            title = "Auto-Lock Timeout",
            options = listOf(
                1 to "1 Minute",
                2 to "2 Minutes",
                5 to "5 Minutes (Recommended)",
                15 to "15 Minutes",
                0 to "Never (Not recommended)"
            ),
            currentValue = autoLockMinutes,
            onDismiss = { showAutoLockDialog = false },
            onSelect = {
                onUpdateAutoLock(it)
                showAutoLockDialog = false
            }
        )
    }

    // Clipboard Timeout Dialog
    if (showClipboardDialog) {
        OptionSelectorDialog(
            title = "Clipboard Clear Timeout",
            options = listOf(
                15 to "15 Seconds",
                30 to "30 Seconds (Recommended)",
                60 to "60 Seconds",
                0 to "Never auto-clear"
            ),
            currentValue = clipboardSeconds,
            onDismiss = { showClipboardDialog = false },
            onSelect = {
                onUpdateClipboardTimeout(it)
                showClipboardDialog = false
            }
        )
    }

    // Export Password Prompt Dialog
    if (showExportPasswordDialog) {
        ExportPasswordDialog(
            onDismiss = { showExportPasswordDialog = false },
            onExport = { pass, cb ->
                onExportVault(pass) { json ->
                    cb(json)
                }
            },
            onSuccess = { json ->
                exportedJsonText = json
                showExportPasswordDialog = false
                showExportDialog = true
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                exportedJsonText = null
            },
            title = { Text("Vault Export JSON", color = CyberCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Your sensitive credentials remain AES-256 GCM encrypted inside this JSON backup. It can only be unlocked with your master password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberSurfaceElevated)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = exportedJsonText?.take(300) + if ((exportedJsonText?.length ?: 0) > 300) "..." else "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        exportedJsonText?.let { onCopyExportedJson(it) }
                        showExportDialog = false
                        exportedJsonText = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberObsidian)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Backup JSON")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showExportDialog = false
                    exportedJsonText = null
                }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = CyberSurface
        )
    }

    // Import Dialog
    if (showImportDialog) {
        ImportBackupDialog(
            onDismiss = { showImportDialog = false },
            onImport = { json, password, cb ->
                onImportVault(json, password) { success ->
                    cb(success)
                    if (success) showImportDialog = false
                }
            }
        )
    }

    // Wipe Confirm Dialog
    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            title = { Text("Erase All Data?", color = CyberRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This action permanently deletes all stored passwords, links, notes, and encryption keys from this device. You will return to the first-time setup screen.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirmDialog = false
                        onWipeVault()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("Permanently Erase", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CyberSurface
        )
    }
}

@Composable
fun SettingsItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberObsidian,
                checkedTrackColor = CyberCyan,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = CyberSurfaceElevated
            )
        )
    }
}

@Composable
fun CryptoSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@Composable
fun OptionSelectorDialog(
    title: String,
    options: List<Pair<Int, String>>,
    currentValue: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))
                options.forEach { (value, label) ->
                    val isSelected = value == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyberCyan.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) CyberCyan else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (CharArray, CharArray, (Boolean) -> Unit) -> Unit
) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    var oldPassVisible by remember { mutableStateOf(false) }
    var newPassVisible by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val clearSensitiveFields = {
        oldPass = ""
        newPass = ""
        confirmPass = ""
        oldPassVisible = false
        newPassVisible = false
        error = null
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            clearSensitiveFields()
        }
    }

    Dialog(onDismissRequest = {
        clearSensitiveFields()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Change Master Password",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "HexaPass will decrypt and re-encrypt all stored vault records with your new key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberCyan
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it; error = null },
                    label = { Text("Current Master Password") },
                    singleLine = true,
                    visualTransformation = if (oldPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { oldPassVisible = !oldPassVisible }) {
                            Icon(if (oldPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan, unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it; error = null },
                    label = { Text("New Master Password") },
                    singleLine = true,
                    visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPassVisible = !newPassVisible }) {
                            Icon(if (newPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan, unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it; error = null },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan, unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = CyberRed, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = {
                        clearSensitiveFields()
                        onDismiss()
                    }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (oldPass.isBlank() || newPass.isBlank()) {
                                error = "Please fill in all fields."
                                return@Button
                            }
                            if (newPass.length < 8) {
                                error = "New password must be at least 8 characters."
                                return@Button
                            }
                            if (newPass != confirmPass) {
                                error = "New passwords do not match."
                                return@Button
                            }
                            isProcessing = true
                            onSubmit(oldPass.toCharArray(), newPass.toCharArray()) { success ->
                                isProcessing = false
                                if (success) {
                                    clearSensitiveFields()
                                } else {
                                    error = "Current master password is incorrect."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberObsidian),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyberObsidian)
                        } else {
                            Text("Re-encrypt & Update", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportPasswordDialog(
    onDismiss: () -> Unit,
    onExport: (CharArray, (String?) -> Unit) -> Unit,
    onSuccess: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val clearSensitiveFields = {
        password = ""
        passwordVisible = false
        error = null
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            clearSensitiveFields()
        }
    }

    Dialog(onDismissRequest = {
        clearSensitiveFields()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Export Encrypted Backup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Enter your Master Password to derive the container encryption key (PBKDF2-HMAC-SHA256) and generate an authenticated backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Master Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan, unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = CyberRed, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = {
                        clearSensitiveFields()
                        onDismiss()
                    }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (password.isBlank()) {
                                error = "Please enter your master password."
                                return@Button
                            }
                            isProcessing = true
                            onExport(password.toCharArray()) { json ->
                                isProcessing = false
                                if (json != null) {
                                    clearSensitiveFields()
                                    onSuccess(json)
                                } else {
                                    error = "Export failed. Verify master password."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberObsidian),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyberObsidian)
                        } else {
                            Text("Export Backup", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportBackupDialog(
    onDismiss: () -> Unit,
    onImport: (String, CharArray, (Boolean) -> Unit) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val clearSensitiveFields = {
        jsonText = ""
        password = ""
        passwordVisible = false
        error = null
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            clearSensitiveFields()
        }
    }

    Dialog(onDismissRequest = {
        clearSensitiveFields()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Import HexaPass Backup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Paste the encrypted JSON backup text and master password below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it; error = null },
                    placeholder = { Text("{\"app\": \"HexaPass\", ...}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Backup Master Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextSecondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = CyberRed, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = {
                        clearSensitiveFields()
                        onDismiss()
                    }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (jsonText.isBlank()) {
                                error = "Please paste JSON backup text."
                                return@Button
                            }
                            if (password.isBlank()) {
                                error = "Please enter backup master password."
                                return@Button
                            }
                            isProcessing = true
                            val jsonVal = jsonText.trim()
                            val passChars = password.toCharArray()
                            onImport(jsonVal, passChars) { success ->
                                isProcessing = false
                                if (success) {
                                    clearSensitiveFields()
                                } else {
                                    error = "Failed to authenticate or restore backup. Verify master password and container integrity."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = CyberObsidian),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyberObsidian)
                        } else {
                            Text("Restore Data", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
