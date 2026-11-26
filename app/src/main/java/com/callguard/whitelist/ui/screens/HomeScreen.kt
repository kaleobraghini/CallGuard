@file:OptIn(ExperimentalMaterial3Api::class)

package com.callguard.whitelist.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.whitelist.screening.RoleHelper
import com.callguard.whitelist.ui.HomeViewModel
import com.callguard.whitelist.ui.appViewModel
import com.callguard.whitelist.ui.components.ScreenScaffold
import com.callguard.whitelist.ui.theme.DangerRed
import com.callguard.whitelist.ui.theme.SafeGreen
import com.callguard.whitelist.ui.theme.WarnAmber

@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenBlocked: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val blockedNumbers by vm.blockedNumbers.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var roleHeld by remember { mutableStateOf(RoleHelper.isHeld(context)) }
    LifecycleResumeEffect(Unit) {
        roleHeld = RoleHelper.isHeld(context)
        onPauseOrDispose { }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = RoleHelper.isHeld(context)
        if (roleHeld) showMessage("Filtragem de chamadas ativada.")
        else showMessage("A função não foi concedida. A proteção não funciona sem ela.")
    }
    val requestRole = {
        val intent = RoleHelper.requestIntent(context)
        if (intent != null) roleLauncher.launch(intent)
        else showMessage("Este aparelho não oferece a função de filtragem de chamadas.")
    }

    val active = state.protectionEnabled && roleHeld
    val (statusColor, statusIcon, title, subtitle) = when {
        !roleHeld -> Quad(WarnAmber, Icons.Filled.GppMaybe, "Configuração pendente", "Conceda a função de filtragem de chamadas para o bloqueio funcionar.")
        !state.protectionEnabled -> Quad(DangerRed, Icons.Filled.GppBad, "Proteção desativada", "Todas as chamadas tocam normalmente.")
        state.allowedCount == 0 -> Quad(WarnAmber, Icons.Filled.GppMaybe, "Whitelist vazia", "Adicione números autorizados. Com a lista vazia nada é bloqueado.")
        else -> Quad(SafeGreen, Icons.Filled.GppGood, "Proteção ativa", "Só números da whitelist conseguem ligar.")
    }

    ScreenScaffold(title = "CallGuard") { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(112.dp).background(statusColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    if (!roleHeld) {
                        Button(onClick = requestRole, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Ativar filtragem de chamadas")
                        }
                    } else {
                        Button(
                            onClick = { vm.setProtection(!state.protectionEnabled) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = if (active) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) else ButtonDefaults.buttonColors(),
                        ) {
                            Text(if (state.protectionEnabled) "Desativar proteção" else "Ativar proteção")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Números permitidos", state.allowedCount, SafeGreen, Modifier.weight(1f), onOpenWhitelist)
                StatCard("Chamadas bloqueadas", state.blockedCount, DangerRed, Modifier.weight(1f), onOpenHistory)
            }

            OutlinedButton(onClick = onOpenBlocked, modifier = Modifier.fillMaxWidth()) {
                Text("Números bloqueados ($blockedNumbers)")
            }

            FilledTonalButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ver histórico de chamadas")
            }

            if (state.allowedCount == 0) {
                OutlinedButton(onClick = onOpenWhitelist, modifier = Modifier.fillMaxWidth()) {
                    Text("Adicionar números autorizados")
                }
            }

            InfoCard(
                Icons.Filled.Info,
                "Como funciona",
                "Ao receber uma ligação, o Android pergunta ao CallGuard se ela pode tocar. " +
                    "O app compara apenas o número com a whitelist e responde em milissegundos, sem internet.",
            )
            InfoCard(
                Icons.Filled.Warning,
                "Limitações do Android",
                "• Ligações de números salvos na agenda do celular não passam pelo filtro: o Android as deixa tocar sempre.\n" +
                    "• Números privados/ocultos dependem do aparelho. Para garantir, use também a opção nativa " +
                    "\"Bloquear números desconhecidos/privados\" do app Telefone (veja Ajustes).\n" +
                    "• Antes do primeiro desbloqueio após reiniciar, o Android ainda não libera o acesso ao app; " +
                    "as chamadas nesse intervalo tocam normalmente.",
            )
        }
    }
}

private data class Quad(val color: Color, val icon: ImageVector, val title: String, val subtitle: String)

@Composable
private fun StatCard(label: String, value: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
