# CallGuard — bloqueio de chamadas por whitelist (Android)

App Android nativo (Kotlin + Jetpack Compose + Material 3) que rejeita automaticamente qualquer
chamada recebida de número **fora da sua lista de autorizados**. Funciona 100% offline: o app
nem tem permissão de internet.

> **Princípio central:** o número telefônico é o único critério de autorização.
> Nomes, categorias, cores e observações servem só para organizar a lista.

---

## 1. Gerar o APK

### Opção A — GitHub Actions (sem instalar nada no PC)

1. Crie um repositório no GitHub (pode ser privado).
2. Envie **o conteúdo desta pasta** para ele (no navegador do computador: *Add file › Upload files*
   e arraste todos os arquivos e pastas, inclusive `.github`; ou com `git push`).
3. Abra a aba **Actions**. O workflow **Build APK** roda sozinho em cada envio (leva de 5 a 8 min).
4. Quando terminar, abra a execução e baixe o artefato **CallGuard-apk**. Dentro dele:
   - `CallGuard.apk` — versão otimizada (use esta);
   - `CallGuard-debug.apk` — versão de depuração (alternativa).
5. Para um link de download fixo, crie uma tag: `git tag v1.0.0 && git push --tags`. O APK é
   anexado automaticamente em **Releases**.

### Opção B — Android Studio

1. Abra a pasta do projeto no Android Studio (Ladybug ou mais novo).
2. Aguarde o *Gradle Sync*.
3. Menu **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
4. O arquivo fica em `app/build/outputs/apk/`.

### Opção C — Linha de comando

Requer JDK 17 e Android SDK (variável `ANDROID_HOME` configurada):

```bash
./gradlew testDebugUnitTest      # testes unitários
./gradlew assembleRelease        # APK em app/build/outputs/apk/release/app-release.apk
```

### Assinatura (recomendado para atualizar sem perder dados)

Sem configuração, o APK é assinado com uma chave de depuração gerada a cada build no GitHub.
Isso funciona, mas para **instalar uma versão nova por cima** a assinatura precisa ser sempre a
mesma. Crie sua chave uma vez:

```bash
keytool -genkeypair -v -keystore callguard.jks -alias callguard -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 callguard.jks   # copie a saída
```

No GitHub: *Settings › Secrets and variables › Actions* e crie `KEYSTORE_BASE64` (a saída acima),
`KEYSTORE_PASSWORD`, `KEY_ALIAS` (`callguard`) e `KEY_PASSWORD`. Guarde o `.jks` em local seguro e
nunca o envie ao repositório.

Antes de reinstalar com outra assinatura, use **Ajustes › Exportar** para salvar a whitelist.

---

## 2. Instalar e ativar

1. Copie o `CallGuard.apk` para o celular e abra. Autorize *Instalar apps desconhecidos* para o
   app que abriu o arquivo (navegador, Arquivos, etc.).
2. Abra o CallGuard e toque em **Ativar filtragem de chamadas**. Escolha o CallGuard como
   *App de ID de chamadas e spam*.
   - Samsung: se a janela não aparecer, vá em *Configurações › Aplicativos › Escolher apps padrão ›
     App de ID de chamadas e spam* e selecione CallGuard (no lugar do Smart Call).
3. Em **Ajustes › Região**, confira o país (55) e informe seu **DDD padrão**.
4. Adicione números na aba **Whitelist** ou importe contatos em **Ajustes › Importar contatos**.

O app **não** precisa ser o discador padrão.

---

## 3. Limitações reais do Android (importante)

| Situação | O que acontece |
|---|---|
| Ligação de número **salvo na agenda** do celular | O Android não encaminha essas chamadas para apps de filtragem que não são o discador. **Elas sempre tocam**, mesmo fora da whitelist. Para bloquear um contato, remova-o da agenda. |
| Número **privado/oculto/indisponível** | Quando o Android encaminha, o app aplica a sua escolha em *Ajustes › Números ocultos*. Alguns aparelhos não encaminham; use também o bloqueio nativo do app Telefone (*Bloquear chamadas desconhecidas/privadas*). |
| Prazo de resposta | O Android espera no máximo 5 s. O CallGuard responde em milissegundos e, no pior caso, desiste em 3 s e aplica a **política de falha**. |
| Celular reiniciado, ainda **sem o primeiro desbloqueio** | O Android não libera o armazenamento do app; as chamadas nesse intervalo tocam normalmente. Após desbloquear, tudo volta a funcionar sozinho, sem precisar abrir o app. |
| Whitelist vazia | Nada é bloqueado (proteção contra bloquear tudo por engano). |
| Proteção desativada pelo botão | Todas as chamadas tocam; elas aparecem no histórico como "Proteção desativada". |

Na maioria dos aparelhos, as chamadas rejeitadas também aparecem no registro do app Telefone como bloqueadas.

---

## 4. Arquitetura

```
app/src/main/java/com/callguard/whitelist/
├── core/                       Kotlin puro, sem Android (testável na JVM)
│   ├── PhoneNormalizer.kt      Normaliza qualquer formato -> chave E.164 só com dígitos
│   └── CallDecisionEngine.kt   Decide ALLOW/REJECT só com a chave numérica + políticas
├── data/
│   ├── Entities.kt / Daos.kt   Room: whitelist (índice único em "normalized"), categorias, histórico
│   ├── WhitelistIndex.kt       HashSet O(1) em memória, recarregado do Room quando o processo é
│   │                           recriado e invalidado automaticamente a cada escrita na tabela
│   ├── SettingsRepository.kt   DataStore: preferências e políticas
│   ├── WhitelistRepository.kt  Cadastro, duplicados, importação, categorias
│   ├── BackupManager.kt        Exportar/importar JSON via seletor de arquivos
│   └── ContactsReader.kt       Leitura da agenda (somente na tela de importação)
├── screening/
│   ├── WhitelistScreeningService.kt   CallScreeningService
│   ├── RoleHelper.kt                  RoleManager.ROLE_CALL_SCREENING
│   └── BlockedCallNotifier.kt         Aviso opcional de bloqueio
└── ui/                         Compose + MVVM (ViewModels com StateFlow)
```

**Caminho de uma chamada:** Android vincula o serviço → lê o número de `Call.Details.handle` →
`PhoneNormalizer` gera a chave → `WhitelistIndex` (HashSet) responde → `respondToCall` →
só depois grava histórico e notificação. Nenhuma consulta a nomes, categorias ou agenda.

**Falhas:** erro no banco ou demora > 3 s → aplica a *política de falha* (padrão: permitir),
registrada no histórico como "Falha na consulta", nunca como "Fora da whitelist".

**Normalização (Brasil):** `+55 47 99999-9999`, `5547999999999`, `(47) 99999-9999`,
`047999999999`, `0 21 47 99999-9999` (com operadora) e `99999-9999` (com DDD padrão 47) geram a
mesma chave `5547999999999`. Ao mudar país/DDD, as chaves salvas são recalculadas.

---

## 5. Testes

- **Unitários** (`app/src/test`): normalização em vários formatos e o motor de decisão (whitelist,
  ocultos, políticas de falha, tempo esgotado, whitelist vazia).
  `./gradlew testDebugUnitTest`
- **Integração** (`app/src/androidTest`): Room real no aparelho + índice + motor: liberação,
  bloqueio, remoção, duplicados, exclusão de categoria e reconstrução após reinício de processo.
  `./gradlew connectedDebugAndroidTest` (com aparelho ou emulador conectado). No GitHub, rode
  manualmente em *Actions › Build APK › Run workflow*.

`Call.Details` não pode ser instanciado fora do sistema, por isso o serviço é testado pelo mesmo
motor e índice que ele usa em produção.

---

## 6. Requisitos

- Android 10 (API 29) ou superior.
- Permissões: *Contatos* (opcional, só para importar) e *Notificações* (opcional).
- Sem internet, sem acessibilidade, sem root, sem APIs privadas de fabricante.
