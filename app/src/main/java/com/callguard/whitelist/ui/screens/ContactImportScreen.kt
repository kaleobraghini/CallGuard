@file:OptIn(ExperimentalMaterial3Api::class)

package com.callguard.whitelist.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.whitelist.core.PhoneNormalizer
import com.callguard.whitelist.ui.ContactImportViewModel
import com.callguard.whitelist.ui.appViewModel
import com.callguard.whitelist.ui.components.CategoryPicker
import com.callguard.whitelist.ui.components.EmptyState
import com.callguard.whitelist.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun ContactImportScreen(onBack: () -> Unit, showMessage: (String) -> Unit) {
    val vm = appViewModel { ContactImportViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val rows by vm.visibleRows.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) showMessage("Sem acesso à agenda. Você ainda pode adicionar números manualmente.")
    }
    LaunchedEffect(granted) {
        if (granted && !state.loaded) vm.load()
    }
    var importing by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Importar contatos",
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
        },
        actions = {
            if (granted && state.loaded) {
                TextButton(onClick = vm::selectAllVisible) { Text("Todos") }
                TextButton(onClick = vm::clearSelection) { Text("Nenhum") }
            }
        },
        bottomBar = {
            if (granted && state.loaded) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Categoria para os importados (opcional)", style = MaterialTheme.typography.labelMedium)
                        CategoryPicker(categories, state.categoryId, vm::setCategory, Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                importing = true
                                scope.launch {
                                    val msg = vm.importSelected()
                                    importing = false
                                    showMessage(msg)
                                }
                            },
                            enabled = state.selected.isNotEmpty() && !importing,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Text("Autorizar ${state.selected.size} selecionado(s)")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !granted -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
                    Text(
                        "O CallGuard copia para a whitelist apenas os contatos que você marcar. " +
                            "Depois da importação, o bloqueio não consulta mais a agenda.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.padding(top = 20.dp),
                    ) { Text("Permitir acesso à agenda") }
                }
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> EmptyState(Icons.Filled.Contacts, "Erro", state.error.orEmpty())
                else -> Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("Pesquisar contato") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Text(
                        "Nenhum contato vem marcado: escolha quem pode ligar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (rows.isEmpty()) {
                        EmptyState(Icons.Filled.Contacts, "Nenhum contato", "Nenhum contato com número foi encontrado.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(rows, key = { it.key }) { row ->
                                val checked = row.alreadyAuthorized || row.key in state.selected
                                ListItem(
                                    modifier = Modifier.clickable(enabled = !row.alreadyAuthorized) { vm.toggle(row.key) },
                                    headlineContent = { Text(row.name) },
                                    supportingContent = {
                                        Row {
                                            Text(PhoneNormalizer.format(row.key))
                                            if (row.alreadyAuthorized) {
                                                Text("  · já autorizado", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { vm.toggle(row.key) },
                                            enabled = !row.alreadyAuthorized,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
