@file:OptIn(ExperimentalMaterial3Api::class)

package com.callguard.whitelist.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.whitelist.core.FailurePolicy
import com.callguard.whitelist.core.HiddenPolicy
import com.callguard.whitelist.data.ThemeMode
import com.callguard.whitelist.screening.RoleHelper
import com.callguard.whitelist.ui.SettingsViewModel
import com.callguard.whitelist.ui.appViewModel
import com.callguard.whitelist.ui.components.ScreenScaffold
import com.callguard.whitelist.ui.components.SectionTitle
import com.callguard.whitelist.ui.theme.SafeGreen
import com.callguard.whitelist.ui.theme.WarnAmber
import java.time.LocalDate

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun notificationsGranted(context: Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)

/** Abre a tela nativa de bloqueio do Android (números bloqueados / desconhecidos / privados). */
private fun openSystemBlocking(context: Context): Boolean {
    val tm = context.getSystemService(TelecomManager::class.java)
    val candidates = listOfNotNull(
        runCatching { tm?.createManageBlockedNumbersIntent() }.getOrNull(),
        Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) {
            // tenta a próxima opção
        }
    }
    return false
}

@Composable
fun SettingsScreen(onImportContacts: () -> Unit, showMessage: (String) -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let { showMessage(it); vm.consumeMessage() }
    }

    var roleHeld by remember { mutableStateOf(RoleHelper.isHeld(context)) }
    var notifGranted by remember { mutableStateOf(notificationsGranted(context)) }
    var contactsGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.READ_CONTACTS)) }
    LifecycleResumeEffect(Unit) {
        roleHeld = RoleHelper.isHeld(context)
        notifGranted = notificationsGranted(context)
        contactsGranted = hasPermission(context, Manifest.permission.READ_CONTACTS)
        onPauseOrDispose { }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = RoleHelper.isHeld(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = granted
        if (!granted) showMessage("Sem permissão, o app não mostra avisos de bloqueio. O bloqueio continua funcionando.")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    var country by remember(settings.countryCode) { mutableStateOf(settings.countryCode) }
    var area by remember(settings.areaCode) { mutableStateOf(settings.areaCode) }

    ScreenScaffold(title = "Ajustes") { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------- Permissões ----------
            SectionTitle("Permissões e funções do sistema")
            Card(Modifier.fillMaxWidth()) {
                StatusRow(
                    title = "Filtragem de chamadas",
                    ok = roleHeld,
                    okText = "Concedida — o Android consulta o CallGuard a cada ligação.",
                    missingText = "Obrigatória. Sem ela nenhuma chamada é analisada.",
                    action = if (!roleHeld) ({
                        val intent = RoleHelper.requestIntent(context)
                        if (intent != null) roleLauncher.launch(intent)
                        else showMessage("Função indisponível neste aparelho.")
                    }) else null,
                    actionLabel = "Conceder",
                )
                HorizontalDivider()
                StatusRow(
                    title = "Notificações",
                    ok = notifGranted,
                    okText = "Permitidas (opcional).",
                    missingText = "Opcional — usada só para avisar sobre chamadas bloqueadas.",
                    action = if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ({
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) else null,
                    actionLabel = "Permitir",
                )
                HorizontalDivider()
                StatusRow(
                    title = "Contatos",
                    ok = contactsGranted,
                    okText = "Permitido — usado apenas na tela de importação.",
                    missingText = "Opcional — só é pedido ao importar contatos.",
                    action = null,
                    actionLabel = "",
                )
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                ) { Text("Abrir configurações do app") }
            }

            // ---------- Bloqueio ----------
            SectionTitle("Política de segurança em falhas")
            Text(
                "Se o CallGuard não conseguir consultar a whitelist (erro ou demora), o que fazer com a chamada? " +
                    "Uma falha nunca é tratada como \"número não autorizado\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Choice(
                options = listOf("Permitir (recomendado)" to FailurePolicy.ALLOW, "Rejeitar" to FailurePolicy.REJECT),
                selected = settings.failurePolicy,
                onSelect = vm::setFailurePolicy,
            )

            SectionTitle("Números privados / ocultos")
            Text(
                "Quando o Android encaminha uma chamada sem número identificado, o CallGuard aplica esta escolha. " +
                    "Alguns aparelhos não encaminham essas chamadas para apps de filtragem; nesse caso, " +
                    "use o bloqueio nativo do app Telefone (em Samsung: Telefone › Configurações › Bloquear números › " +
                    "Bloquear chamadas desconhecidas/privadas).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Choice(
                options = listOf("Rejeitar" to HiddenPolicy.REJECT, "Permitir" to HiddenPolicy.ALLOW),
                selected = settings.hiddenPolicy,
                onSelect = vm::setHiddenPolicy,
            )
            OutlinedButton(
                onClick = { if (!openSystemBlocking(context)) showMessage("Abra o app Telefone › Configurações › Bloquear números.") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Abrir bloqueio nativo do Android") }

            // ---------- Região ----------
            SectionTitle("Região dos números")
            Text(
                "Usado para entender números sem código do país ou sem DDD. Ex.: com país 55 e DDD 47, " +
                    "\"99999-9999\", \"(47) 99999-9999\" e \"+55 47 99999-9999\" são o mesmo número.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it.filter(Char::isDigit).take(3) },
                    label = { Text("País") },
                    prefix = { Text("+") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it.filter(Char::isDigit).take(4) },
                    label = { Text("DDD padrão") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.saveRegion(country, area) },
                    enabled = country != settings.countryCode || area != settings.areaCode,
                ) { Text("Salvar") }
            }

            // ---------- Histórico e avisos ----------
            SectionTitle("Histórico e notificações")
            SwitchRow("Avisar quando bloquear uma chamada", settings.notifyOnBlock, vm::setNotify)
            SwitchRow("Registrar também chamadas permitidas", settings.logAllowedCalls, vm::setLogAllowed)

            // ---------- Dados ----------
            SectionTitle("Dados")
            OutlinedButton(onClick = onImportContacts, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Contacts, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Importar contatos da agenda")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("callguard-whitelist-${LocalDate.now()}.json") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importar")
                }
            }

            // ---------- Aparência ----------
            SectionTitle("Aparência")
            Choice(
                options = listOf("Sistema" to ThemeMode.SYSTEM, "Claro" to ThemeMode.LIGHT, "Escuro" to ThemeMode.DARK),
                selected = settings.themeMode,
                onSelect = vm::setTheme,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Cores do papel de parede (Material You)", settings.dynamicColor, vm::setDynamicColor)
            }

            // ---------- Sobre ----------
            SectionTitle("Privacidade")
            Text(
                "O CallGuard não tem permissão de internet: seus números nunca saem do aparelho. " +
                    "Não usa acessibilidade, root nem APIs privadas de fabricantes. " +
                    "O backup do Android está desativado para este app; use Exportar para guardar uma cópia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    ok: Boolean,
    okText: String,
    missingText: String,
    action: (() -> Unit)?,
    actionLabel: String,
) {
    ListItem(
        leadingContent = {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ok) SafeGreen else WarnAmber,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(if (ok) okText else missingText) },
        trailingContent = {
            if (action != null) TextButton(onClick = action) { Text(actionLabel) }
        },
    )
}

@Composable
private fun <T> Choice(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, value) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
