@file:OptIn(ExperimentalMaterial3Api::class)

package com.callguard.whitelist.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.whitelist.core.PhoneNormalizer
import com.callguard.whitelist.data.WhitelistEntry
import com.callguard.whitelist.data.WhitelistWithCategory
import com.callguard.whitelist.ui.NO_CATEGORY
import com.callguard.whitelist.ui.SortMode
import com.callguard.whitelist.ui.WhitelistViewModel
import com.callguard.whitelist.ui.appViewModel
import com.callguard.whitelist.ui.components.Avatar
import com.callguard.whitelist.ui.components.ColorDot
import com.callguard.whitelist.ui.components.ConfirmDialog
import com.callguard.whitelist.ui.components.EmptyState
import com.callguard.whitelist.ui.components.EntryEditorDialog
import com.callguard.whitelist.ui.components.Pill
import com.callguard.whitelist.ui.components.ScreenScaffold

/** Estado do editor: null = fechado; entry null = novo registro. */
private data class EditorState(val entry: WhitelistEntry?)

@Composable
fun WhitelistScreen(showMessage: (String) -> Unit) {
    val vm = appViewModel { WhitelistViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.categoryFilter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<EditorState?>(null) }
    var toDelete by remember { mutableStateOf<WhitelistWithCategory?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Whitelist ($total)",
        actions = {
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m == sort) "✓  ${m.label}" else m.label) },
                            onClick = { vm.sort.value = m; sortMenu = false },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editor = EditorState(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Pesquisar por nome ou número") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { vm.categoryFilter.value = null }, label = { Text("Todas") })
                FilterChip(
                    selected = filter == NO_CATEGORY,
                    onClick = { vm.categoryFilter.value = NO_CATEGORY },
                    label = { Text("Sem categoria") },
                )
                categories.forEach { c ->
                    FilterChip(
                        selected = filter == c.id,
                        onClick = { vm.categoryFilter.value = if (filter == c.id) null else c.id },
                        label = { Text(c.name) },
                        leadingIcon = { ColorDot(c.color, 10) },
                    )
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    Icons.Filled.VerifiedUser,
                    if (total == 0) "Nenhum número autorizado" else "Nada encontrado",
                    if (total == 0) "Toque em Adicionar ou importe da agenda em Ajustes." else "Tente outro termo ou filtro.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(items, key = { it.entry.id }) { item ->
                        WhitelistRow(
                            item = item,
                            onEdit = { editor = EditorState(item.entry) },
                            onDelete = { toDelete = item },
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }

    editor?.let { st ->
        val e = st.entry
        EntryEditorDialog(
            title = if (e == null) "Novo número autorizado" else "Editar número",
            categories = categories,
            initialNumber = e?.number.orEmpty(),
            initialName = e?.name.orEmpty(),
            initialCategoryId = e?.categoryId,
            initialNotes = e?.notes.orEmpty(),
            onDismiss = { editor = null },
            onSave = { number, name, catId, notes ->
                val err = vm.save(e, number, name, catId, notes)
                if (err == null) showMessage(if (e == null) "Número autorizado." else "Alterações salvas.")
                err
            },
        )
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = "Remover da whitelist?",
            message = "${item.entry.name ?: PhoneNormalizer.format(item.entry.normalized)} deixará de conseguir ligar.",
            confirmLabel = "Remover",
            onConfirm = { vm.delete(item.entry.id); showMessage("Número removido.") },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun WhitelistRow(item: WhitelistWithCategory, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val formatted = PhoneNormalizer.format(item.entry.normalized)
    val title = item.entry.name ?: formatted
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        leadingContent = { Avatar(title, item.categoryColor) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (item.entry.name != null) Text(formatted, style = MaterialTheme.typography.bodyMedium)
                if (item.categoryName != null || item.entry.notes != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.categoryName?.let { Pill(it, item.categoryColor) }
                        item.entry.notes?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remover") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
    )
    Spacer(Modifier.width(0.dp))
}
