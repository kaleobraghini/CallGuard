@file:OptIn(ExperimentalMaterial3Api::class)

package com.callguard.whitelist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.callguard.whitelist.core.DecisionReason
import com.callguard.whitelist.core.PhoneNormalizer
import com.callguard.whitelist.ui.HistoryFilter
import com.callguard.whitelist.ui.HistoryRow
import com.callguard.whitelist.ui.HistoryViewModel
import com.callguard.whitelist.ui.appViewModel
import com.callguard.whitelist.ui.components.ConfirmDialog
import com.callguard.whitelist.ui.components.EmptyState
import com.callguard.whitelist.ui.components.EntryEditorDialog
import com.callguard.whitelist.ui.components.ScreenScaffold
import com.callguard.whitelist.ui.theme.DangerRed
import com.callguard.whitelist.ui.theme.SafeGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm:ss")

fun reasonLabel(reason: String): String = when (runCatching { DecisionReason.valueOf(reason) }.getOrNull()) {
    DecisionReason.WHITELISTED -> "Na whitelist"
    DecisionReason.NOT_WHITELISTED -> "Fora da whitelist"
    DecisionReason.PROTECTION_OFF -> "Proteção desativada"
    DecisionReason.EMPTY_WHITELIST -> "Whitelist vazia (nada é bloqueado)"
    DecisionReason.HIDDEN_ALLOWED -> "Número oculto — permitido pela configuração"
    DecisionReason.HIDDEN_REJECTED -> "Número oculto — rejeitado pela configuração"
    DecisionReason.LOOKUP_FAILED_ALLOWED -> "Falha na consulta — permitida pela política de segurança"
    DecisionReason.LOOKUP_FAILED_REJECTED -> "Falha na consulta — rejeitada pela política de segurança"
    DecisionReason.SETTINGS_UNAVAILABLE -> "Configurações indisponíveis — permitida"
    DecisionReason.BLOCKED -> "Na lista de bloqueados"
    null -> reason
}

@Composable
fun HistoryScreen(showMessage: (String) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    var adding by remember { mutableStateOf<HistoryRow?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Histórico",
        actions = {
            IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Limpar histórico")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { vm.filter.value = f }, label = { Text(f.label) })
                }
            }
            if (rows.isEmpty()) {
                EmptyState(
                    Icons.Filled.History,
                    "Nenhuma chamada registrada",
                    "Aparecem aqui as chamadas que o Android encaminhou para análise. " +
                        "Ligações de contatos da agenda não passam pelo filtro e não aparecem.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(rows, key = { it.event.id }) { row ->
                        val e = row.event
                        val hidden = e.normalized == null
                        val icon = when {
                            hidden -> Icons.Filled.VisibilityOff
                            e.allowed -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Block
                        }
                        val tint = if (e.allowed) SafeGreen else DangerRed
                        val time = dateFormat.format(Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault()))
                        ListItem(
                            leadingContent = { Icon(icon, contentDescription = if (e.allowed) "Permitida" else "Bloqueada", tint = tint) },
                            headlineContent = {
                                Text(row.whitelistName ?: PhoneNormalizer.format(e.normalized))
                            },
                            supportingContent = {
                                Column {
                                    if (row.whitelistName != null) Text(PhoneNormalizer.format(e.normalized))
                                    Text(time, style = MaterialTheme.typography.bodySmall)
                                    if (row.blocked) Text("Na lista de bloqueados", style = MaterialTheme.typography.bodySmall, color = DangerRed)
                                    Text(
                                        (if (e.allowed) "Permitida · " else "Rejeitada · ") + reasonLabel(e.reason),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tint,
                                    )
                                }
                            },
                            trailingContent = {
                                if (!row.inWhitelist && e.normalized != null) {
                                    Row {
                                        // Botão 1: bloquear / desbloquear
                                        IconButton(onClick = {
                                            vm.toggleBlock(row)
                                            showMessage(if (row.blocked) "Número desbloqueado." else "Número bloqueado.")
                                        }) {
                                            Icon(
                                                if (row.blocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                                                contentDescription = if (row.blocked) "Desbloquear" else "Bloquear",
                                                tint = if (row.blocked) MaterialTheme.colorScheme.primary else DangerRed,
                                            )
                                        }
                                        // Botão 2: adicionar à whitelist
                                        IconButton(onClick = { adding = row }) {
                                            Icon(Icons.Filled.PersonAdd, contentDescription = "Adicionar à whitelist")
                                        }
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

    adding?.let { row ->
        EntryEditorDialog(
            title = "Autorizar número",
            categories = categories,
            initialNumber = row.event.normalized?.let { if (it.length >= 10) "+$it" else it }.orEmpty(),
            onDismiss = { adding = null },
            onSave = { number, name, catId, notes ->
                val err = vm.addToWhitelist(number, name, catId, notes)
                if (err == null) showMessage("Número adicionado à whitelist.")
                err
            },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Limpar histórico?",
            message = "Todos os registros de chamadas analisadas serão apagados. A whitelist não é alterada.",
            confirmLabel = "Limpar",
            onConfirm = { vm.clear(); showMessage("Histórico apagado.") },
            onDismiss = { confirmClear = false },
        )
    }
}
