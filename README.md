# Extrair APK

App Android nativo (Kotlin + Jetpack Compose) para listar todos os apps instalados no aparelho e extrair o `.apk` de qualquer um deles — próprio, sem anúncios, sem SDKs de rastreamento/analytics.

## Por que este projeto existe

Foi construído a partir da análise do APK "APK Extractor" (`com.toralabs.apkextractor`) enviado como referência. A função central desse app é simples — listar pacotes via `PackageManager` e copiar o APK de origem usando um `FileProvider` — mas o arquivo original tinha quase 1000 entradas e 3 `classes.dex` (~16 MB) porque embutia SDKs de anúncios e rastreamento (AdMob, AppLovin MAX, Facebook Audience Network, Yandex Ads, Vungle, Firebase Analytics/Crashlytics/Messaging, Google Play Billing, AppMetrica, Singular) em cima dessa função simples.

Este projeto reimplementa a mesma função — e melhora alguns pontos — como um app limpo, com pacote próprio (`com.extrairapk.app`, diferente do original), sem nenhuma dessas dependências de terceiros.

## Funcionalidades

- Lista todos os apps instalados (nome, ícone, pacote, versão, tamanho).
- Busca por nome ou nome de pacote.
- Filtro por Todos / Apps de usuário / Apps de sistema.
- Ordenação por nome, tamanho ou data de atualização.
- Extração de apps com **APK dividido (App Bundle / split APKs)**: o app detecta os splits e ou instala tudo de volta via uma única sessão do `PackageInstaller`, ou empacota tudo em um `.apks` para guardar/compartilhar.
- Após extrair: **Compartilhar** (Intent padrão do Android), **Salvar em Downloads/ExtrairAPK** (via `MediaStore`, sem precisar de permissão em Android 10+) ou **Instalar** diretamente.
- Seleção múltipla (toque longo) para **exportar vários apps de uma vez** para Downloads.
- Tema Material 3 com suporte a cor dinâmica (Android 12+) e modo escuro.

## Como gerar o APK

Este ambiente de desenvolvimento não tem acesso ao Android SDK / Google Maven, então a compilação roda no GitHub Actions (que tem acesso total à internet):

1. Faça push desta branch (ou qualquer branch) para o GitHub — o workflow `.github/workflows/build-apk.yml` roda automaticamente.
2. Acompanhe em **Actions → Build APK**.
3. No fim da execução, baixe o artefato `extrair-apk-debug` — ele contém o `app-debug.apk`, já assinado com a chave de debug e pronto para instalar (habilite "instalar de fontes desconhecidas" no aparelho).

Também é possível compilar localmente com o Android Studio (Giraffe ou mais novo): abra a pasta do projeto e rode `Run`, ou `./gradlew assembleDebug` em um terminal com o Android SDK instalado.

## Permissões usadas

| Permissão | Motivo |
|---|---|
| `QUERY_ALL_PACKAGES` | necessária para enxergar todos os apps instalados, não só os que se comunicam com este app |
| `REQUEST_INSTALL_PACKAGES` | permite oferecer o botão "Instalar" a partir do APK extraído |
| `WRITE_EXTERNAL_STORAGE` (só até Android 9) | salvar em Downloads em versões antigas; a partir do Android 10 isso é feito via `MediaStore`, sem essa permissão |

Nenhuma permissão de rede é declarada — o app não acessa a internet.

## Estrutura

```
app/src/main/kotlin/com/extrairapk/app/
├── data/            # modelos e leitura do PackageManager
├── util/            # extração de APK/splits, exportação, instalação
├── ui/theme/        # tema Material 3
├── ui/components/   # item de lista, bottom sheet de detalhes
├── ui/screens/      # tela principal
├── MainActivity.kt
└── AppListViewModel.kt
```
