package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import com.example.util.UrlSecurityManager
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.LINK_CATEGORIES
import com.example.data.model.LinkItem
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
fun LinksScreen(
    links: List<LinkItem>,
    searchQuery: String,
    selectedCategory: String,
    favoritesOnly: Boolean,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onToggleFavorite: (LinkItem) -> Unit,
    onSaveLink: (Long, String, String, String, String, List<String>, Boolean, (Boolean) -> Unit) -> Unit,
    onDeleteLink: (Long) -> Unit,
    initialAddOpen: Boolean = false,
    onAddDialogClosed: () -> Unit = {}
) {
    val context = LocalContext.current
    var editingItem by remember { mutableStateOf<LinkItem?>(null) }
    var isAddingNew by remember { mutableStateOf(initialAddOpen) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var showLimitDialog by remember { mutableStateOf(false) }

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
                    text = "VAULT LINKS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CyberEmerald
                )
                Text(
                    text = "${links.size}/10 entries",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (links.size >= 10) CyberAmber else TextSecondary
                )
            }
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("link_search_input"),
                placeholder = { Text("Search links, descriptions, tags...", color = TextSecondary) },
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
                    focusedBorderColor = CyberEmerald,
                    unfocusedBorderColor = CyberBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = CyberSurface,
                    unfocusedContainerColor = CyberSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

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

                LINK_CATEGORIES.forEach { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) CyberEmerald.copy(alpha = 0.15f) else CyberSurface)
                            .border(
                                1.dp,
                                if (isSelected) CyberEmerald else CyberBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onCategorySelect(category) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) CyberEmerald else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Link List
            if (links.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || favoritesOnly || selectedCategory != "All") "No matching links found" else "No saved links yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Store important bookmarks, docs, and secure URLs.",
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
                    items(links, key = { it.id }) { link ->
                        LinkCard(
                            item = link,
                            onEdit = { editingItem = link },
                            onDelete = { deletingId = link.id },
                            onToggleFavorite = { onToggleFavorite(link) },
                            onCopyUrl = { onCopyUrl(link.url) },
                            onOpenUrl = {
                                UrlSecurityManager.launchSafeUrl(context, link.url)
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = {
                if (links.size >= 10) {
                    showLimitDialog = true
                } else {
                    isAddingNew = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_link_fab"),
            containerColor = CyberEmerald,
            contentColor = CyberObsidian,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Link")
        }
    }

    // Limit Reached Dialog
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = {
                Text(
                    text = "Limit Reached",
                    color = CyberEmerald,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Link limit reached. Maximum 10 link entries are allowed.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("OK", color = CyberEmerald)
                }
            },
            containerColor = CyberSurfaceElevated
        )
    }

    // Add / Edit Dialog
    if (isAddingNew || editingItem != null) {
        LinkEditDialog(
            item = editingItem,
            onDismiss = {
                isAddingNew = false
                editingItem = null
                onAddDialogClosed()
            },
            onSave = { name, url, desc, cat, tags, fav ->
                onSaveLink(
                    editingItem?.id ?: 0L,
                    name,
                    url,
                    desc,
                    cat,
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

    // Confirm Delete
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete Link?", color = CyberRed, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove this saved link?", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLink(id)
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
fun LinkCard(
    item: LinkItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenUrl: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("link_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .background(CyberEmerald.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.name,
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

                        Text(
                            text = item.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberEmerald,
                            maxLines = 1
                        )
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
                                contentDescription = "Menu",
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
                                text = { Text("Open in Browser", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CyberCyan) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenUrl()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Link", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyberEmerald) },
                                onClick = {
                                    menuExpanded = false
                                    onCopyUrl()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Link", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CyberCyan) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Link", color = CyberRed) },
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

            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCopyUrl,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy URL",
                        tint = CyberEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onOpenUrl,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open Browser",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LinkEditDialog(
    item: LinkItem?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, List<String>, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var url by remember { mutableStateOf(item?.url ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "Personal") }
    var tagsInput by remember { mutableStateOf(item?.tags?.joinToString(", ") ?: "") }
    var isFavorite by remember { mutableStateOf(item?.isFavorite ?: false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val clearSensitiveFields = {
        name = ""
        url = ""
        description = ""
        category = "Personal"
        tagsInput = ""
        isFavorite = false
        errorMessage = null
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
                    text = if (item == null) "Add Secure Link" else "Edit Saved Link",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Link Name *") },
                    placeholder = { Text("e.g. Server Dashboard, Cloud Console") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL *") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes") },
                    placeholder = { Text("Purpose or documentation...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Category", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LINK_CATEGORIES.filter { it != "All" }.forEach { cat ->
                        val isSelected = category == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) CyberEmerald.copy(alpha = 0.2f) else CyberSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) CyberEmerald else CyberBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) CyberEmerald else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Tags") },
                    placeholder = { Text("dev, reference, prod") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

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

                errorMessage?.let {
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
                            if (name.isBlank()) {
                                errorMessage = "Please enter a name for the link."
                                return@Button
                            }
                            if (url.isBlank()) {
                                errorMessage = "URL cannot be empty."
                                return@Button
                            }
                            val tagList = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val nameVal = name.trim()
                            val urlVal = url.trim()
                            val descVal = description.trim()
                            val catVal = category
                            val favVal = isFavorite
                            clearSensitiveFields()
                            onSave(
                                nameVal,
                                urlVal,
                                descVal,
                                catVal,
                                tagList,
                                favVal
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberEmerald,
                            contentColor = CyberObsidian
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_link_entry_button")
                    ) {
                        Text("Save Link", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
