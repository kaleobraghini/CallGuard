# Room, Compose, DataStore e Coroutines já trazem suas próprias regras de consumo.
# Mantém o serviço declarado no Manifest (o R8 já preserva, regra explícita por segurança).
-keep class com.callguard.whitelist.screening.WhitelistScreeningService { *; }
