@file:OptIn(ExperimentalMaterial3Api::class)

package com.callguard.whitelist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.whitelist.core.PhoneNormalizer
import com.callguard.whitelist.data.CategoryEntity
import com.callguard.whitelist.data.CategoryWithCount
import com.callguard.whitelist.data.WhitelistWithCategory
import com.callguard.whitelist.ui.CategoriesViewModel
import com.callguard.whitelist.ui.appViewModel
import com.callguard.whitelist.ui.components.CategoryEditorDialog
import com.callguard.whitelist.ui.components.ColorDot
import com.callguard.whitelist.ui.components.ConfirmDialog
import com.callguard.whitelist.ui.components.EmptyState
import com.callguard.whitelist.ui.components.ScreenScaffold

private data class CategoryEditor(val category: CategoryEntity?)

@Composable
fun CategoriesScreen(showMessage: (String) -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<CategoryEditor?>(null) }
    var toDelete by remember { mutableStateOf<CategoryWithCount?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    ScreenScaffold(
        title = "Categorias",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editor = CategoryEditor(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova categoria") },
            )
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(Icons.Filled.Category, "Sem categorias", "Categorias servem só para organizar. Elas nunca afetam o bloqueio.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Categorias são apenas organização visual. Excluir ou renomear uma categoria nunca remove números autorizados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(categories, key = { it.category.id }) { cat ->
                    CategoryCard(
                        item = cat,
                        expanded = expandedId == cat.category.id,
                        members = if (expandedId == cat.category.id) entries.filter { it.entry.categoryId == cat.category.id } else emptyList(),
                        onToggle = { expandedId = if (expandedId == cat.category.id) null else cat.category.id },
                        onEdit = { editor = CategoryEditor(cat.category) },
                        onDelete = { toDelete = cat },
                    )
                }
            }
        }
    }

    editor?.let { st ->
        val c = st.category
        CategoryEditorDialog(
            title = if (c == null) "Nova categoria" else "Editar categoria",
            initialName = c?.name.orEmpty(),
            initialColor = c?.color ?: com.callguard.whitelist.data.WhitelistRepository.DEFAULT_CATEGORY_COLORS[categories.size % 12],
            onDismiss = { editor = null },
            onSave = { name, color -> vm.save(c, name, color) },
        )
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = "Excluir \"${item.category.name}\"?",
            message = if (item.entryCount > 0)
                "${item.entryCount} número(s) continuarão autorizados, apenas ficarão sem categoria."
            else "A categoria não tem números associados.",
            confirmLabel = "Excluir",
            onConfirm = { vm.delete(item.category.id); showMessage("Categoria excluída.") },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun CategoryCard(
    item: CategoryWithCount,
    expanded: Boolean,
    members: List<WhitelistWithCategory>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(item.category.color, 18)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    when (item.entryCount) { 0 -> "Nenhum número"; 1 -> "1 número"; else -> "${item.entryCount} números" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renomear / cor") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        if (expanded) {
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (members.isEmpty()) {
                    Text("Nenhum número nesta categoria.", style = MaterialTheme.typography.bodySmall)
                }
                members.forEach { m ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text(m.entry.name ?: "—", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(PhoneNormalizer.format(m.entry.normalized), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
