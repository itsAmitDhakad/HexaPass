package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import com.example.util.UrlSecurityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.crypto.StrengthLevel
import com.example.data.crypto.VaultCrypto
import com.example.data.model.DecryptedPasswordItem
import com.example.data.model.PASSWORD_CATEGORIES
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberObsidian
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.PasswordSortOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PasswordsScreen(
    passwords: List<DecryptedPasswordItem>,
    searchQuery: String,
    selectedCategory: String,
    favoritesOnly: Boolean,
    sortOption: PasswordSortOption,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSortSelect: (PasswordSortOption) -> Unit,
    onCopyUsername: (String) -> Unit,
    onCopyPassword: (String) -> Unit,
    onToggleFavorite: (DecryptedPasswordItem) -> Unit,
    onSavePassword: (Long, String, String, String, String, String, String, List<String>, Boolean, (Boolean) -> Unit) -> Unit,
    onDeletePassword: (Long) -> Unit,
    initialAddOpen: Boolean = false,
    onAddDialogClosed: () -> Unit = {}
) {
    var editingItem by remember { mutableStateOf<DecryptedPasswordItem?>(null) }
    var isAddingNew by remember { mutableStateOf(initialAddOpen) }
    var viewingItem by remember { mutableStateOf<DecryptedPasswordItem?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var showLimitDialog by remember { mutableStateOf(false) }

    var showSortMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VAULT PASSWORDS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CyberCyan
                )
                Text(
                    text = "${passwords.size}/10 entries",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (passwords.size >= 10) CyberAmber else TextSecondary
                )
            }
            // Search & Sort Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("password_search_input"),
                    placeholder = { Text("Search website, username, tags...", color = TextSecondary) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = CyberSurface,
                        unfocusedContainerColor = CyberSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberSurface)
                            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = CyberCyan)
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = CyberSurfaceElevated
                    ) {
                        PasswordSortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        color = if (sortOption == option) CyberCyan else TextPrimary,
                                        fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSortSelect(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorites Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (favoritesOnly) CyberAmber.copy(alpha = 0.2f) else CyberSurface)
                        .border(
                            1.dp,
                            if (favoritesOnly) CyberAmber else CyberBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onToggleFavoritesOnly() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Default.Star else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (favoritesOnly) CyberAmber else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (favoritesOnly) CyberAmber else TextSecondary
                        )
                    }
                }

                PASSWORD_CATEGORIES.forEach { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) CyberCyan.copy(alpha = 0.15f) else CyberSurface)
                            .border(
                                1.dp,
                                if (isSelected) CyberCyan else CyberBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onCategorySelect(category) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) CyberCyan else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Password Cards List
            if (passwords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || favoritesOnly || selectedCategory != "All") "No matching passwords found" else "No passwords stored yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Click the + button below to add your first encrypted credential.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(passwords, key = { it.id }) { item ->
                        PasswordCard(
                            item = item,
                            onCardClick = { viewingItem = item },
                            onEdit = { editingItem = item },
                            onDelete = { deletingId = item.id },
                            onToggleFavorite = { onToggleFavorite(item) },
                            onCopyUsername = { onCopyUsername(item.username) },
                            onCopyPassword = { onCopyPassword(item.password) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                if (passwords.size >= 10) {
                    showLimitDialog = true
                } else {
                    isAddingNew = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_password_fab"),
            containerColor = CyberCyan,
            contentColor = CyberObsidian,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Password")
        }
    }

    // Limit Reached Dialog
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = {
                Text(
                    text = "Limit Reached",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Password limit reached. Maximum 10 password entries are allowed.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("OK", color = CyberCyan)
                }
            },
            containerColor = CyberSurfaceElevated
        )
    }

    // Add / Edit Dialog
    if (isAddingNew || editingItem != null) {
        PasswordEditDialog(
            item = editingItem,
            onDismiss = {
                isAddingNew = false
                editingItem = null
                onAddDialogClosed()
            },
            onSave = { name, url, user, pass, notes, category, tags, fav ->
                onSavePassword(
                    editingItem?.id ?: 0L,
                    name,
                    url,
                    user,
                    pass,
                    notes,
                    category,
                    tags,
                    fav
                ) { success ->
                    if (success) {
                        isAddingNew = false
                        editingItem = null
                        onAddDialogClosed()
                    } else if (editingItem == null) {
                        showLimitDialog = true
                    }
                }
            }
        )
    }

    // View Details Dialog
    viewingItem?.let { item ->
        PasswordDetailDialog(
            item = item,
            onDismiss = { viewingItem = null },
            onEdit = {
                viewingItem = null
                editingItem = item
            },
            onCopyUsername = { onCopyUsername(item.username) },
            onCopyPassword = { onCopyPassword(item.password) }
        )
    }

    // Confirm Delete Dialog
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete Password?", color = CyberRed, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove this password from your vault? This cannot be undone.", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePassword(id)
                        deletingId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingId = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CyberSurface
        )
    }
}

@Composable
fun PasswordCard(
    item: DecryptedPasswordItem,
    onCardClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val strengthColor = when (item.strength.level) {
        StrengthLevel.VERY_WEAK -> CyberRed
        StrengthLevel.WEAK -> CyberAmber
        StrengthLevel.FAIR -> Color(0xFFFBBF24)
        StrengthLevel.STRONG -> CyberCyan
        StrengthLevel.VERY_STRONG -> CyberEmerald
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("password_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Logo/Letter, Name, Category Pill, Favorite, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(CyberCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.websiteName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.websiteName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyberSurfaceElevated)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        if (item.url.isNotBlank()) {
                            Text(
                                text = item.url.removePrefix("https://").removePrefix("http://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) CyberAmber else TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = CyberSurfaceElevated
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Entry", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CyberCyan) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Entry", color = CyberRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CyberRed) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Username Row
            if (item.username.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCopyUsername,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Username",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Password Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberSurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (passwordVisible) item.password else "••••••••••••••",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = if (passwordVisible) 0.sp else 2.sp
                    ),
                    color = if (passwordVisible) CyberCyan else TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Visibility",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onCopyPassword,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Password",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Strength Badge & Tags Row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(strengthColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.strength.level.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = strengthColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (item.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.take(2).forEach { tag ->
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordEditDialog(
    item: DecryptedPasswordItem?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, List<String>, Boolean) -> Unit
) {
    var websiteName by remember { mutableStateOf(item?.websiteName ?: "") }
    var url by remember { mutableStateOf(item?.url ?: "") }
    var username by remember { mutableStateOf(item?.username ?: "") }
    var password by remember { mutableStateOf(item?.password ?: "") }
    var notes by remember { mutableStateOf(item?.notes ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "Login") }
    var tagsInput by remember { mutableStateOf(item?.tags?.joinToString(", ") ?: "") }
    var isFavorite by remember { mutableStateOf(item?.isFavorite ?: false) }

    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val clearSensitiveFields = {
        websiteName = ""
        url = ""
        username = ""
        password = ""
        notes = ""
        tagsInput = ""
        passwordVisible = false
        errorMessage = null
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            clearSensitiveFields()
        }
    }

    val strength = remember(password) { VaultCrypto.evaluateStrength(password) }

    Dialog(onDismissRequest = {
        clearSensitiveFields()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (item == null) "Add Password Entry" else "Edit Password Entry",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Encrypted in memory with AES-256 GCM before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberCyan
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Website / App Name
                OutlinedTextField(
                    value = websiteName,
                    onValueChange = { websiteName = it },
                    label = { Text("Website or App Name *") },
                    placeholder = { Text("e.g. GitHub, Google, Amazon") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL / Website Address") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Username / Email
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or Email") },
                    placeholder = { Text("user@example.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_username_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Password with Live Strength & Generator Shortcut
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password *") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_password_input"),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                password = VaultCrypto.generatePassword(16)
                            }) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Generate",
                                    tint = CyberCyan
                                )
                            }
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val strengthColor = when (strength.level) {
                        StrengthLevel.VERY_WEAK -> CyberRed
                        StrengthLevel.WEAK -> CyberAmber
                        StrengthLevel.FAIR -> Color(0xFFFBBF24)
                        StrengthLevel.STRONG -> CyberCyan
                        StrengthLevel.VERY_STRONG -> CyberEmerald
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Strength: ${strength.level.name.replace("_", " ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = strengthColor
                        )
                        Text(
                            text = "${strength.entropyBits} bits",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    LinearProgressIndicator(
                        progress = { strength.score / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = strengthColor,
                        trackColor = CyberBorder
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Selection Chips
                Text("Category", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PASSWORD_CATEGORIES.filter { it != "All" }.forEach { cat ->
                        val isSelected = category == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) CyberCyan.copy(alpha = 0.2f) else CyberSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) CyberCyan else CyberBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) CyberCyan else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tags
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("work, personal, dev") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Secure Notes (Encrypted)") },
                    placeholder = { Text("Recovery codes, security questions...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Favorite Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFavorite = !isFavorite }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) CyberAmber else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Favorites", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let {
                        Text(text = it, color = CyberRed, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions
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
                            if (websiteName.isBlank()) {
                                errorMessage = "Please enter a website or app name."
                                return@Button
                            }
                            if (password.isBlank()) {
                                errorMessage = "Password cannot be empty."
                                return@Button
                            }
                            val tagList = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val siteNameVal = websiteName.trim()
                            val urlVal = url.trim()
                            val userVal = username.trim()
                            val passVal = password
                            val notesVal = notes.trim()
                            val catVal = category
                            val favVal = isFavorite
                            clearSensitiveFields()
                            onSave(
                                siteNameVal,
                                urlVal,
                                userVal,
                                passVal,
                                notesVal,
                                catVal,
                                tagList,
                                favVal
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = CyberObsidian
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_password_entry_button")
                    ) {
                        Text("Save Credential", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordDetailDialog(
    item: DecryptedPasswordItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            passwordVisible = false
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.websiteName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyberCyan)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Website URL & Launch
                if (item.url.isNotBlank()) {
                    Text("URL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            UrlSecurityManager.launchSafeUrl(context, item.url)
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", tint = CyberCyan)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Username
                if (item.username.isNotBlank()) {
                    Text("Username / Email", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.username,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onCopyUsername) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Password
                Text("Password", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (passwordVisible) item.password else "••••••••••••••••",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = if (passwordVisible) 0.sp else 2.sp
                        ),
                        color = if (passwordVisible) CyberCyan else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle",
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = onCopyPassword) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes
                if (item.notes.isNotBlank()) {
                    Text("Secure Notes", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Dates
                Text(
                    text = "Created: ${dateFormat.format(Date(item.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Text(
                    text = "Last updated: ${dateFormat.format(Date(item.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                ) {
                    Text("Close", color = TextPrimary)
                }
            }
        }
    }
}
