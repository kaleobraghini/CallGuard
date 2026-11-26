package com.callguard.whitelist.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.whitelist.core.PhoneNormalizer
import com.callguard.whitelist.data.BlockedNumber
import com.callguard.whitelist.ui.BlockedViewModel
import com.callguard.whitelist.ui.appViewModel
import com.callguard.whitelist.ui.components.EmptyState
import com.callguard.whitelist.ui.components.EntryEditorDialog
import com.callguard.whitelist.ui.components.ScreenScaffold
import com.callguard.whitelist.ui.theme.DangerRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val blockedDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Lista de bloqueados: cada número tem dois botões separados, Desbloquear e Adicionar à whitelist. */
@Composable
fun BlockedScreen(onBack: () -> Unit, showMessage: (String) -> Unit) {
    val vm = appViewModel { BlockedViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var authorizing by remember { mutableStateOf<BlockedNumber?>(null) }
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Bloqueados (${items.size})",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Bloquear número") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.Block, "Nenhum número bloqueado", "Números rejeitados aparecem aqui quando o bloqueio automático está ligado.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(items, key = { it.normalized }) { b ->
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Block, null, tint = DangerRed) },
                            headlineContent = { Text(PhoneNormalizer.format(b.normalized)) },
                            supportingContent = {
                                Text(
                                    (if (b.auto) "Bloqueado automaticamente" else "Bloqueado por você") + " · " +
                                        blockedDate.format(Instant.ofEpochMilli(b.createdAt).atZone(ZoneId.systemDefault())),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { vm.unblock(b.normalized); showMessage("Número desbloqueado.") }) {
                                        Icon(Icons.Filled.LockOpen, contentDescription = "Desbloquear", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { authorizing = b }) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = "Adicionar à whitelist")
                                    }
                                }
                            },
                        )
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }

    authorizing?.let { b ->
        EntryEditorDialog(
            title = "Autorizar número",
            categories = categories,
            initialNumber = if (b.normalized.length >= 10) "+" + b.normalized else b.normalized,
            onDismiss = { authorizing = null },
            onSave = { number, name, catId, notes ->
                val err = vm.addToWhitelist(number, name, catId, notes)
                if (err == null) showMessage("Desbloqueado e adicionado à whitelist.")
                err
            },
        )
    }

    if (adding) {
        com.callguard.whitelist.ui.components.EntryEditorDialog(
            title = "Bloquear número",
            categories = emptyList(),
            onDismiss = { adding = false },
            onSave = { number, _, _, _ ->
                val err = vm.block(number)
                if (err == null) showMessage("Número bloqueado.")
                err
            },
        )
    }
}
