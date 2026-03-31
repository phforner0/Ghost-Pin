# 1. Resumo executivo
- Backlog derivado desta analise: `GHOSTPIN_SPRINT_BACKLOG.md`.

- O `GhostPin` e um app Android em Kotlin para simulacao de GPS/mock location com mapa, roteamento ponto-a-ponto, waypoints, importacao GPX, controle por overlay/joystick, widget, Quick Settings tile, automacao por broadcast e agendamento. Isso e sustentado por `app/src/main/kotlin/com/ghostpin/app/ui/MainActivity.kt`, `.../ui/GhostPinScreen.kt`, `.../service/SimulationService.kt`, `.../widget/GhostPinWidget.kt`, `.../service/GhostPinQsTile.kt`, `.../automation/AutomationReceiver.kt` e `.../scheduling/ScheduleReceiver.kt`.
- O repositorio esta bem organizado em modulos no build graph: `:core`, `:engine`, `:realism-lab` e `:app` em `settings.gradle.kts`; `:engine` depende de `:core`; `:realism-lab` depende de `:core` e `:engine`; `:app` depende de `:core` e `:engine`. Isso e um bom sinal arquitetural.
- O estado geral e desigual: os modulos puros (`:core`, `:engine`, `:realism-lab`) estao mais coesos e testados; o runtime real do app cresce por acumulo de sprint e centraliza complexidade em `SimulationService`, `SimulationViewModel` e `SimulationRepository`.
- Os maiores riscos tecnicos reais hoje sao:
  - GPX provavelmente quebrado no fluxo principal: `SimulationViewModel.loadGpxFromUri()` pre-carrega a rota no `SimulationRepository`, mas `SimulationService.startSimulation()` limpa `repository.emitRoute(null)` antes de entrar no branch `AppMode.GPX`, entao o service apaga a propria rota que deveria consumir.
  - Joystick provavelmente quebrado: `JoystickView` expoe `state`, `SimulationService.runJoystickLoop()` le `repository.joystickState`, `SimulationRepository.updateJoystickState()` existe, mas nao ha chamada ligando a view ao repositorio.
  - Favoritos, replay, tile, widget e schedule nao conseguem reconstruir a simulacao completa porque `SimulationConfig`, `FavoriteSimulationEntity` e `SimulationHistoryEntity` nao persistem `endLat/endLng`, `mode`, `waypoints`, `waypointPauseSec`, `speedRatio` efetivo, `frequencyHz` efetivo nem rota carregavel de fato.
  - O runtime ignora boa parte do motor: `SimulationService` nao usa `RouteInterpolator`, `SpeedController`, `LayeredNoiseModel`, `TrajectoryValidator` nem `realism-lab`; usa interpolacao manual e injeta posicoes deterministicas.
  - A variante `playstore` nao esta realmente isolada da `nonplay`: `app/build.gradle.kts` so muda `BuildConfig`, e eu nao encontrei `app/src/playstore` nem `app/src/nonplay`; o `AndroidManifest.xml` continua unico e declara as mesmas permissoes/componentes sensiveis.
- Os principais meritos reais sao:
  - Isolamento Android-free de `:core`, `:engine` e `:realism-lab`.
  - Bom conjunto de testes nos modulos puros, especialmente `GeoMath`, interpolacao, ruido e metricas.
  - Uso consistente de Kotlin DSL, Gradle wrapper, Java toolchain 21, KSP, Hilt, Room e DataStore.
  - Hardening pontual correto em parsing XML contra XXE em `RouteFileParser.parseXml()`.
- Fato metodologico: a analise foi estatica; nao rodei Gradle/testes nesta sessao por restricao read-only. Onde a conclusao depende de comportamento de plataforma Android, eu marco como hipotese.

# 2. Entendimento do produto
- O app faz, de fato, spoofing/simulacao de posicao GPS no Android por um foreground service (`SimulationService`) que injeta localizacoes mock via `MockLocationInjector.registerProvider()` e `inject()` em `app/src/main/kotlin/com/ghostpin/app/location/MockLocationInjector.kt`.
- O fluxo principal de produto e:
  - escolher origem/destino no mapa em `GhostPinScreen` + `InteractiveMap`;
  - selecionar perfil de movimento em `SimulationViewModel.selectedProfile`;
  - opcionalmente escolher modo `CLASSIC`, `JOYSTICK`, `WAYPOINTS` ou `GPX` em `AppMode`;
  - iniciar o service que resolve rota e injeta localizacoes.
- Casos de uso principais observados no codigo:
  - Simulacao classica ponto-a-ponto com OSRM ou fallback em linha reta: `OsrmRouteProvider.fetchRoute()` e `SimulationService.startSimulation()`.
  - Multi-stop/waypoints: `SimulationViewModel.addWaypoint()/removeWaypoint()` e `OsrmRouteProvider.fetchMultiRoute()`.
  - GPX importado do picker: `MainActivity.gpxFilePickerLauncher` e `SimulationViewModel.loadGpxFromUri()`.
  - Controle manual por joystick/overlay: `FloatingBubbleService`, `JoystickView`, `SimulationService.runJoystickLoop()`.
- Funcionalidades auxiliares:
  - Favoritos: `SimulationRepository.saveFavorite()/resolveFavoriteConfig()`.
  - Historico: `SimulationRepository.startHistory()/finishHistory()` e `HistoryScreen`.
  - Schedule com `AlarmManager`: `ScheduleManager` e `ScheduleReceiver`.
  - Widget e QS tile: `GhostPinWidget` e `GhostPinQsTile`.
  - Automacao por broadcast: `AutomationReceiver`.
- Funcionalidades experimentais/laboratoriais:
  - `:engine` contem interpolacao, ruido, controle de velocidade e validacao de trajetoria.
  - `:realism-lab` contem metricas de realismo e relatorio agregado.
  - Fato importante: essas capacidades existem, mas nao estao integradas ao runtime principal do app.
- Limitacoes reais:
  - Custom profiles existem no backend (`ProfileManager`, `ProfileEntity`), mas a UI principal usa apenas built-ins em `SimulationViewModel.profiles`.
  - Saved routes/route editor existem, mas o fluxo "iniciar com esta rota" nao esta conectado ao `AppNavHost`.
  - Export/import de rotas esta incompleto na UI.
  - O runtime atual simula trajetoria, mas nao materializa a camada de "realismo" prometida pelos modulos puros.

# 3. Arquitetura do repositorio
- Mapa de modulos:
  - `:core`: modelos puros (`Route`, `Waypoint`, `MovementProfile`, `MockLocation`, `AppMode`), matematica geografica (`GeoMath`) e sanitizacao de logs (`LogSanitizer`).
  - `:engine`: interpolacao (`RouteInterpolator`, `RepeatTraversalController`), controle de velocidade, ruido e validacao de trajetoria.
  - `:realism-lab`: metricas e relatorios de realismo sobre sequencias de localizacao.
  - `:app`: Android framework, Compose UI, Hilt, Room, DataStore, MapLibre, service, widget, tile, automacao e scheduling.
- Dependencias entre modulos:
  - `engine/build.gradle.kts`: `implementation(project(":core"))`.
  - `realism-lab/build.gradle.kts`: `implementation(project(":core"))` e `implementation(project(":engine"))`.
  - `app/build.gradle.kts`: `implementation(project(":core"))` e `implementation(project(":engine"))`.
- Em termos de estilo arquitetural, o repositorio e uma mistura pragmatica de camadas com "clean-ish boundaries" no build e "service-oriented app core" no runtime.
- O que esta coeso:
  - A fronteira Android vs Kotlin puro esta limpa.
  - Persistencia Room esta concentrada em `data/db`.
  - Integracoes externas estao separadas em `routing`.
  - Componentes Android sensiveis estao segmentados em `service`, `automation`, `scheduling` e `widget`.
- O que esta acoplado demais:
  - `SimulationService.kt` concentra parsing de intents, resolucao de rota, execucao da simulacao, pausa/stop/resume, historico, widget, overlay e atalhos.
  - `SimulationRepository.kt` mistura state store em memoria, favoritos, historico, joystick state e validacao de config.
  - `SimulationViewModel.kt` mistura bootstrap de localizacao, geocoding, GPX, mapa, favoritos, replay, modos e eventos efemeros.
- Violacoes arquiteturais relevantes:
  - O app nao reutiliza o motor que ele mesmo define em `:engine`: `SimulationService` implementa `interpolate()`, `computeBearing()` e `haversineMeters()` localmente.
  - `TrajectoryValidator` e injetado em `SimulationService`, mas nao e usado.
  - `LayeredNoiseModel` e provido em `SimulationModule`, mas nao e usado pelo app.
  - `realism-lab` nao aparece no fluxo do `:app`.
- Sinais claros de crescimento por sprint:
  - Muitos comentarios "Sprint X / Task Y".
  - Estado morto ou nao conectado: `SimulationRepository.isManualMode`, `lowMemorySignal`, `previewPlayhead`, `SHIZUKU_ENABLED`, `ProfileManagerViewModel`.
  - Comentarios prometem mais do que o wiring atual entrega, especialmente em GPX, joystick, route editor e automacao.

# 4. Fluxo ponta a ponta
1. Inicializacao do app:
   - `GhostPinApp.onCreate()` cria o canal de notificacao e dispara `ProfileManager.seedBuiltInsIfNeeded()` em background.
2. Entrada em `MainActivity`:
   - `MainActivity.onCreate()` chama `MapLibre.getInstance(this)`, pede permissoes de localizacao/notificacao com `requestPermissions()` e so entao compoe a UI.
3. Onboarding:
   - `OnboardingDataStore.isComplete` decide o `startDestination` em `AppNavHost`.
   - `OnboardingScreen` conduz 3 passos via `OnboardingViewModel`: permissoes, mock location e setup inicial.
4. Problema de fluxo ja aqui:
   - `MainActivity` pede permissoes antes do onboarding, enquanto `OnboardingScreen` tambem tem passo explicito de permissoes. O fluxo fica duplicado.
5. Entrada na tela principal:
   - `AppNavHost` navega para `GhostPinScreen` e chama `viewModel.initializeLocation(...)`.
   - `GhostPinScreen` tambem chama `initializeLocation(context)` toda vez que o estado volta a `Idle`.
6. Interacao com mapa:
   - `InteractiveMap` instancia `MapView` e `MapController`.
   - O long press do mapa chama `SimulationViewModel.onMapLongPress()`.
   - Em `CLASSIC`, alterna start/end.
   - Em `WAYPOINTS`, adiciona waypoint.
7. Selecao de perfil e modo:
   - `GhostPinScreen` renderiza `ProfileSelector`, `ClassicModePanel`, `WaypointsModePanel`, `JoystickModePanel` e `GpxModePanel`.
   - `SimulationViewModel` mantem `selectedProfile`, `selectedMode`, `repeatPolicy`, `repeatCount`, waypoints e estado GPX.
8. Carregamento de rota:
   - `CLASSIC`: `SimulationService` usa `OsrmRouteProvider.fetchRoute()`.
   - `WAYPOINTS`: usa `OsrmRouteProvider.fetchMultiRoute()`.
   - `GPX`: `SimulationViewModel.loadGpxFromUri()` pre-carrega a rota no repositorio.
   - `JOYSTICK`: nao precisa de rota.
9. Inicio da simulacao:
   - `MainActivity.startSimulation()` monta o intent com profile, coords, mode, waypoints, pause e repeat.
   - `SimulationService.onStartCommand()` faz parsing do intent, valida build flavor, inicia overlay se puder, chama `startForeground(...)` e entra em `startSimulation(...)`.
10. Loop de atualizacao:
   - O service registra mock provider em `MockLocationInjector.registerProvider()`.
   - No loop, calcula posicao, bearing e `MockLocation`, injeta via `MockLocationInjector.inject()`, emite `SimulationState.Running` no `SimulationRepository` e atualiza widget.
11. Pause/stop/resume:
   - `ACTION_PAUSE` vira `SimulationState.Paused`.
   - `ACTION_STOP` chama `stopSimulation()`.
   - Resume depende de `repository.state is Paused` e ausencia de `EXTRA_START_LAT`.
12. Persistencia:
   - Historico e iniciado em `startHistory()` e encerrado em `finishHistory()`.
   - Favoritos vao para `favorite_simulations`.
   - Onboarding usa DataStore.
13. Atualizacao de widget/overlay/tile/automation:
   - Widget e atualizado por `GhostPinWidget.updateAll(...)`.
   - Overlay observa `simulationRepository.state`.
   - QS tile le o mesmo repositorio.
   - Automation e scheduling convergem em intents para `SimulationService`.
14. Onde o fluxo quebra hoje:
   - GPX: a rota pre-carregada e apagada antes do uso.
   - Replay/favorite/tile/widget: o modelo persistido nao contem dados suficientes para reproduzir a simulacao.
   - Joystick: o input nao chega ao repositorio.

# 5. Analise tecnica detalhada por camada
## 5.1 UI
- `GhostPinScreen.kt` esta relativamente "fina" em regra de negocio, mas pesada em coleta de estado. Ela coleta `selectedProfile`, `selectedMode`, `simulationState`, `isBusy`, coordenadas, rota, `deviceLocation`, favoritos, waypoints, sugestoes, ETA, repeat e GPX state no mesmo composable raiz.
- Fato: `SimulationService` emite `SimulationState.Running` a cada frame, e `GhostPinScreen` observa esse `StateFlow` na raiz. Isso implica recomposicao ampla e frequente.
- Fato critico: `InteractiveMap` usa `LaunchedEffect(simulationState, startLat, startLng, endLat, endLng, waypoints, appMode, route, previewPlayhead)` e, em cada estado `Running`, chama `controller.updateRoute(...)` antes de `controller.updatePosition(...)`.
- Fato critico: `MapController.updateRoute(route)` faz `fitCamera(...)`. Isso sugere re-fit de camera durante a execucao, potencialmente a cada frame, alem de redesenho da rota inteira.
- Isso e um gargalo claro de performance/UX e fonte de jitter visual.
- `MainActivity.onLowMemory()` gera `_lowMemorySignal`, mas `InteractiveMap` recebe `lowMemorySignal` e nao usa. Isso e divida visivel.
- `previewPlayhead` existe em `InteractiveMap`, mas nao ha uso fora dela. Outro sinal de feature incompleta.
- O onboarding esta semanticamente correto como tela propria (`OnboardingScreen`), mas o fluxo real e inconsistente:
  - `MainActivity` dispara pedidos de permissao imediatamente.
  - `OnboardingScreen` tambem modela um passo de permissoes.
  - `AppNavHost` e `GhostPinScreen` chamam `initializeLocation()` e podem sobrescrever coordenadas escolhidas no onboarding.
- `GhostPinScreen` mantem um FAB global de Start/Stop que nao e suficientemente sensivel ao modo:
  - em `GPX`, permite start mesmo quando a rota carregada nao esta garantida;
  - em `WAYPOINTS`, ignora `waypointPauseSec` porque esse FAB sempre chama `onStartSimulation(selectedProfile, 0.0)`.
- Pontos bons a preservar:
  - `GhostPinScreen` passa dados + callbacks aos filhos em vez de injetar `ViewModel` em tudo.
  - `RouteEditorScreen` tem cuidado explicito com preview simplificado em dispositivos low-RAM.
- Recomendacao pratica:
  - separar coleta de estado por folha;
  - tornar `InteractiveMap` observador apenas de posicao e rota, em fluxos distintos;
  - parar de chamar `updateRoute()/fitCamera()` em toda atualizacao de `simulationState`;
  - tornar o FAB dependente do modo e do readiness da configuracao.

## 5.2 ViewModel
- `SimulationViewModel.kt` e funcionalmente util, mas inchado.
- Responsabilidades hoje concentradas nele:
  - geocoding (`searchAddress`, `addWaypointFromGeoResult`);
  - bootstrap de localizacao (`initializeLocation`);
  - perfis e modo (`selectProfile`, `setAppMode`);
  - repeat (`setRepeatPolicy`, `setRepeatCount`);
  - GPX (`loadGpxFromUri`, `clearGpxRoute`);
  - favoritos (`saveCurrentAsFavorite`, `applyFavoriteById`);
  - replay de historico (`applyReplayConfig`);
  - mapa e waypoints (`setStartLat`, `onMapLongPress`, `addWaypoint`, `removeWaypoint`).
- Isso e um claro smell de `ViewModel` orquestrador demais.
- O `ViewModel` tambem carrega dependencia de plataforma Android por `Context` em `initializeLocation()` e `loadGpxFromUri()`. Isso reduz testabilidade e mistura UI orchestration com IO/platform edges.
- O modelo de perfis na tela principal nao usa `ProfileManager` nem Room; usa uma lista hardcoded de built-ins em `SimulationViewModel.profiles`. Isso torna `ProfileManager`, `ProfileEntity` e `ProfileManagerViewModel` infraestrutura parcialmente orfa.
- `applyReplayConfig()` e `applyFavoriteById()` forcam `AppMode.CLASSIC`. Isso evidencia que replay/favorite nao preservam o modo original.
- `applyReplayConfig()` usa `history.routeId`, mas recria `SimulationConfig` com `startLat/startLng` atuais, nao os do historico. Como `SimulationHistoryEntity` tambem nao guarda fim/modo/waypoints, o replay nao e replay real.
- Recomendacao pratica:
  - dividir em um `SimulationSessionViewModel` para estado da sessao atual;
  - um `SavedSimulationsViewModel` ou use cases para favoritos/historico;
  - um `LocationBootstrapper`;
  - um `GpxImportUseCase`;
  - substituir `SimulationConfig` por um objeto mais completo, como `SimulationLaunchPlan`.

## 5.3 Services
- `SimulationService.kt` e o arquivo mais critico do projeto.
- Ele concentra:
  - parsing de comandos externos;
  - controle de foreground service;
  - overlays;
  - favoritos;
  - parsing de arquivos;
  - resolucao de rota;
  - loop de interpolacao;
  - injecao mock;
  - historico;
  - widget.
- Fato critico: GPX esta logicamente quebrado.
  - `SimulationViewModel.loadGpxFromUri()` faz `repository.emitRoute(route)`.
  - `SimulationService.startSimulation()` faz `if (resumeState == null) repository.emitRoute(null)`.
  - depois, no branch `appMode == AppMode.GPX`, ele espera `repository.route.first { it != null }`.
  - Resultado: o service limpa a rota pre-carregada antes de consumi-la.
- Fato critico: joystick esta logicamente quebrado.
  - `JoystickView` mantem `state: StateFlow<JoystickState>`.
  - `SimulationRepository` expoe `updateJoystickState(...)`.
  - `SimulationService.runJoystickLoop()` le `repository.joystickState.value`.
  - Eu nao encontrei nenhum caller de `updateJoystickState(...)`.
  - `FloatingBubbleService.showJoystick()` so adiciona a view ao `WindowManager`; nao coleta nem propaga o estado.
- Fato critico: `elapsedTimeSec` esta errado.
  - No loop principal e no loop joystick, `elapsedSec++` acontece por frame, nao por segundo.
  - Em 5 Hz, o app reporta 5 segundos por segundo real; em 60 Hz, 60 segundos por segundo real.
- Fato critico: o loop roda em `lifecycleScope.launch` sem dispatcher explicito, entao a logica de execucao fica no dispatcher principal do service.
- Isso e especialmente ruim porque o loop tambem:
  - injeta localizacao;
  - emite estado;
  - atualiza widget;
  - processa skip de waypoint;
  - pode fazer delays de pausa.
- Fato critico: `GhostPinWidget.updateAll(...)` e chamado por frame no loop de rota e no loop joystick, embora o proprio comentario do widget diga que a atualizacao seria "on simulation state changes, not per-frame".
- Fato critico: `stopSimulation()` encerra historico via `lifecycleScope.launch { finishActiveHistory(...) }` e logo depois chama `stopSelf()`. Como `onDestroy()` tambem chama `stopSimulation()`, ha risco real de corrida, dupla tentativa ou perda do fechamento do historico.
- Fato: `TrajectoryValidator` e injetado e nao usado.
- Fato: `RouteFileParser` e usado para `ACTION_SET_ROUTE`, mas esse fluxo nao entra no path principal de simulacao se o modo nao for GPX.
- Fato: `routeId` e carregado em config/historico/favoritos, mas o service nao injeta `RouteRepository` nem busca rota persistida por `routeId`.
- Fato: `estimateAltitude()` sempre retorna `0.0`, embora GPX/route editor preservem altitude.
- Fato: os `segments` e `SegmentOverrides` do route editor nao sao usados no runtime.
- Fato: `speedRatio` aceita `0.0`; no loop classico isso pode gerar simulacao infinita sem progresso.
- Recomendacao pratica:
  - quebrar o service em `CommandHandler`, `RouteResolver`, `SimulationRunner`, `HistoryRecorder` e `SurfaceUpdater`;
  - mover o loop para `Dispatchers.Default`;
  - corrigir GPX antes de qualquer outra evolucao;
  - ligar joystick ao repositorio;
  - substituir `elapsedSec++` por tempo real derivado de relogio monotonico;
  - parar atualizacoes por frame de widget;
  - introduzir carregamento real de rota persistida por `routeId`.

## 5.4 Domain/Core/Engine
- A separacao de dominio e o melhor pedaco tecnico do repositorio.
- `:core` e `:engine` estao limpos de Android framework e tem boa testabilidade.
- Ha evidencia de qualidade real:
  - `GeoMath.kt` com testes em `GeoMathTest.kt`;
  - `LogSanitizer.kt` com testes em `LogSanitizerTest.kt`;
  - `RouteInterpolator.kt` com `RouteInterpolatorTest.kt`;
  - `SpeedController.kt` com `SpeedControllerTest.kt`;
  - `TrajectoryValidator.kt` com `TrajectoryValidatorTest.kt`;
  - metricas de `realism-lab` com `RealismMetricsTest.kt`.
- O problema nao e a existencia desses modulos; e a nao integracao deles ao app.
- Fato: o runtime usa sua propria interpolacao em `SimulationService.interpolate()` e `computeBearing()`, nao `RouteInterpolator`.
- Fato: o runtime nao usa `LayeredNoiseModel`, entao a simulacao atual e essencialmente deterministica, com `MockLocation` "limpo".
- Fato: `realism-lab` e laboratorio mesmo; nao aparece no app.
- Inconsistencias tecnicas relevantes:
  - `Route.distanceMeters` usa raio da Terra `6378137.0`, enquanto `GeoMath` usa `6371000.0`.
  - `Route.estimateDuration()` usa `0.80 * maxSpeedMs`; `SpeedController` assume `0.65 * maxSpeedMs`.
  - `TrajectoryValidator.validate()` interpreta `pauseDurationSec` como base para velocidade requerida, mas `Route.estimateDuration()` explicitamente exclui pausas.
  - `RouteInterpolator` documenta parametrizacao por arc length "uniforme na curva", mas a implementacao e segmentada por distancia entre waypoints, nao por arc length da spline.
- Hipotese tecnica importante:
  - `TunnelNoiseModel` e os campos `tunnelDurationMeanSec/tunnelDurationSigmaSec` podem estar semanticamente mal nomeados ou mal parametrizados, porque o modelo usa LogNormal em espaco log.
- O que deve ser preservado:
  - a existencia dos modulos puros;
  - a suite de testes dos modulos puros;
  - `GeoMath` e `LogSanitizer`;
  - a disciplina de nao introduzir Android em `:core`/`:engine`.
- O que deve mudar primeiro:
  - definir se `:engine` e parte do produto ou so laboratorio;
  - se for parte do produto, o app precisa parar de duplicar matematica e passar a compor o runtime sobre essas APIs.

## 5.5 Data/Persistencia
- `SimulationRepository.kt` virou um "god repository".
- Ele mistura:
  - state store em memoria (`state`, `route`, `lastUsedConfig`);
  - joystick/manual mode;
  - historico;
  - favoritos;
  - resolucao parcial de config.
- O maior problema aqui e o modelo de config.
- `SimulationConfig.kt` so guarda:
  - `profileName`;
  - `startLat`;
  - `startLng`;
  - `routeId`;
  - `repeatPolicy`;
  - `repeatCount`.
- Isso e insuficiente para reproduzir qualquer simulacao classica completa, porque faltam `endLat/endLng`; e insuficiente para modos avancados porque faltam `mode`, `waypoints`, `waypointPauseSec`, `speedRatio`, `frequencyHz` e origem de arquivo.
- Esse erro se propaga para:
  - `FavoriteSimulationEntity`;
  - `SimulationHistoryEntity`;
  - `GhostPinQsTile`;
  - `GhostPinWidget`;
  - `AutomationReceiver`;
  - `ScheduleEntity`.
- Fato muito importante:
  - favoritos nem sequer guardam coordenadas de inicio/fim;
  - `resolveFavoriteConfig()` reconstrui `startLat/startLng` a partir de `fallback` ou `DefaultCoordinates`;
  - entao o favorito nao e autossuficiente.
- Fato importante:
  - `FavoriteSimulationEntity` persiste `speedRatio` e `frequencyHz`, mas `SimulationConfig` nao possui esses campos e `resolveFavoriteConfig()` os ignora.
- Fato importante:
  - `SimulationHistoryEntity` nao guarda `endLat/endLng`, `mode` nem waypoints; replay fiel e impossivel.
- Fato importante:
  - `ScheduleEntity` guarda apenas start coords, profile, speed e frequency; nao guarda fim, modo, rota nem repeat.
  - `ScheduleReceiver` monta start intent so com `EXTRA_START_LAT/LNG`, entao o service cai em `endLat/endLng` default.
- Fato importante:
  - `routeId` e quase decorativo hoje.
  - o service nao carrega rota por `routeId`;
  - rotas OSRM/GPX geradas em runtime tem IDs transitorios e nao sao salvas em `RouteDao`;
  - salvar favorito de rota nao persistida tende a criar favorito invalido.
- O banco Room tem bons elementos:
  - `GhostPinDatabase` centraliza entidades.
  - `exportSchema = true`.
  - migracoes `1_2`, `2_3`, `3_4`.
- Riscos claros de persistencia:
  - nao ha foreign keys nem indices nos schemas exportados.
  - o arquivo de schema `3.json` esta ausente.
  - ha ambiguidade persistida em `profileIdOrName`.
- `RouteRepository.kt` e razoavelmente coeso, mas hoje esta subutilizado pelo runtime principal.
- `RouteEntity` usa blobs JSON em `waypointsJson` e `segmentsJson`. Para escala pequena isso e pragmatico, mas aumenta risco de compatibilidade manual.
- `OnboardingDataStore.kt` e simples e correto para o problema que resolve.
- Recomendacao pratica:
  - criar um modelo persistido unico de sessao, por exemplo `SimulationPlanEntity`/`SimulationPlan`;
  - parar de usar `profileIdOrName` ambiguo e migrar para IDs estaveis;
  - decidir explicitamente se `routeId` deve apontar para rota persistida e entao carregar via `RouteRepository`;
  - adicionar testes de migracao e integridade de favoritos/historico/schedules.

## 5.6 Integracoes externas
- OSRM:
  - `OsrmRouteProvider.kt` usa o demo publico `https://router.project-osrm.org/route/v1`.
  - Ha timeout e fallback para rota em linha reta.
  - Nao ha retry, cache, backoff nem infraestrutura propria.
  - Isso e aceitavel para prototipo, fragil para producao.
- Nominatim:
  - `GeocodingProvider.kt` usa `https://nominatim.openstreetmap.org/search`.
  - A doc do proprio arquivo fala em limite de 1 req/s, mas o `SimulationViewModel.searchAddress()` usa debounce de 400 ms e nao ha rate limiter real.
  - Em falha, a UI fica silenciosa; apenas retorna lista vazia.
- MapLibre:
  - `MapController.kt` usa estilo remoto `https://tiles.openfreemap.org/styles/liberty`.
  - Nao vi fallback offline, cache proprio nem estrategia para indisponibilidade do provider.
- GPX/KML/TCX:
  - Ha duas pipelines de parsing GPX:
    - `GpxParser.kt`, streaming e simplificada, usada pelo file picker do modo GPX;
    - `RouteFileParser.kt`, DOM e multi-formato, usada por automacao e route editor.
  - Isso gera comportamento inconsistente entre fluxos de importacao.
- Automacao:
  - `AutomationReceiver` esta protegido por permissao signature no manifest.
  - O comentario do receiver fala em Tasker/ADB e em "normal protection level", mas isso diverge do manifest.
  - Inferencia: Tasker nao vai conseguir usar esse receiver em sua forma atual.
- Route editor / import-export:
  - `RouteEditorViewModel` suporta importar/exportar.
  - `RouteEditorScreen` mostra UI de export e botao "Iniciar com esta rota".
  - `AppNavHost` nao passa `onRouteReady`, entao o botao nao aciona o fluxo principal.
  - O export e limpo imediatamente em `LaunchedEffect(state.exportContent)` sem integracao real com share/save externo.
- Recomendacao pratica:
  - unificar parsers de arquivo;
  - definir claramente quais integracoes sao "produto" e quais sao "lab";
  - para producao, substituir demo OSRM/Nominatim por infraestrutura controlada ou tratar explicitamente como best-effort.

## 5.7 Build/Gradle/Toolchain
- O stack de build observado e consistente:
  - Gradle wrapper `8.14.3` em `gradle/wrapper/gradle-wrapper.properties`;
  - Kotlin `2.1.10` no root `build.gradle.kts`;
  - AGP `8.8.2`;
  - KSP `2.1.10-1.0.31`;
  - Java toolchain `21` em todos os modulos.
- Merito:
  - uso de Gradle Kotlin DSL;
  - wrapper pinado;
  - toolchain explicito;
  - migracao para KSP.
- O principal problema de build/release nao e compatibilidade de versoes; e previsibilidade de variantes e CI.
- Fato critico:
  - `app/build.gradle.kts` define flavors `nonplay` e `playstore`.
  - Eu nao encontrei `app/src/nonplay` nem `app/src/playstore`.
  - Portanto, o mesmo codigo e o mesmo manifest base alimentam as duas variantes.
- Isso torna a separacao de distribuicao superficial:
  - `MOCK_PROVIDER_ENABLED` e `SHIZUKU_ENABLED` mudam;
  - permissoes sensiveis, receivers exportados, overlay, tile e widget continuam os mesmos.
- `SHIZUKU_ENABLED` e flag morta: nao ha uso no codigo.
- CI em `.github/workflows/ci.yml` e fragil:
  - compila so `:app:compileNonplayDebugKotlin`;
  - nao compila `playstore`;
  - `:app:testNonplayDebugUnitTest` e `continue-on-error: true`;
  - `realism-lab` nao entra nos testes;
  - ktlint e nao bloqueante por `|| true`.
- Hipotese de compatibilidade:
  - `MockLocationInjector.kt` importa `android.location.provider.ProviderProperties` enquanto `minSdk` e 26. Eu nao validei o API level exato dessa classe; isso merece checagem explicita.
- Hipotese de scheduling:
  - `ScheduleManager` usa `setExactAndAllowWhileIdle()`, mas eu nao vi `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` nem fallback. Em Android recentes isso pode afetar previsibilidade.
- Recomendacao pratica:
  - tornar CI bloqueante para o que importa;
  - compilar/testar `playstoreDebug`;
  - separar manifest/source sets por flavor;
  - remover flags mortas ou implementa-las;
  - adicionar testes de migracao.

# 6. Seguranca, privacidade e compliance
- Permissoes declaradas em `AndroidManifest.xml`:
  - `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`;
  - `ACCESS_MOCK_LOCATION`;
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`;
  - `POST_NOTIFICATIONS`, `INTERNET`, `RECEIVE_BOOT_COMPLETED`;
  - `SYSTEM_ALERT_WINDOW`;
  - permissao custom `com.ghostpin.permission.AUTOMATION` com `protectionLevel="signature"`.
- Componentes exportados:
  - `MainActivity`: `exported=true`, normal.
  - `GhostPinQsTile`: `exported=true`, protegido por `BIND_QUICK_SETTINGS_TILE`.
  - `ScheduleReceiver`: `exported=true`, sem permissao.
  - `AutomationReceiver`: `exported=true`, protegido por permissao signature custom.
  - `SimulationService` e `FloatingBubbleService`: `exported=false`.
- Riscos reais de abuso:
  - `ScheduleReceiver` expoe `ACTION_SCHEDULE_EVENT` sem permissao. Mesmo com IDs dificeis de adivinhar, a superficie nao precisava estar publica.
  - Logs sao sanitizados de forma inconsistente:
    - `SimulationService` e `AutomationReceiver` usam `LogSanitizer` em alguns pontos;
    - `OsrmRouteProvider`, `GeocodingProvider` e varios logs genericos nao usam.
  - `ACTION_SET_ROUTE` aceita `Uri` externa e abre stream no service; isso precisa de validacao de origem/permissao mais explicita se a automacao for expandida.
- Play Store vs nonplay:
  - Fora da Play Store:
    - O conjunto `mock GPS + overlay + boot receiver + automacao + widget/tile` e tecnicamente aceitavel, desde que o projeto assuma distribuicao lateral e hardening basico.
    - Ainda assim, eu endureceria `ScheduleReceiver`, a politica de logs e a modelagem de intents.
  - Para Play Store:
    - O risco e alto.
    - O core do produto e GPS spoofing/mock location.
    - O manifest ainda declara `ACCESS_MOCK_LOCATION` e `SYSTEM_ALERT_WINDOW`.
    - A variante `playstore` nao remove componentes sensiveis do manifest.
    - O onboarding continua instruindo mock location e overlay porque nao ha wiring por flavor na UI.
    - Inferencia: a estrategia atual e insuficiente para uma publicacao Play previsivel.
- O que exige hardening imediato:
  - tornar `ScheduleReceiver` nao-exportado ou protegido;
  - separar manifest/source sets por flavor;
  - reduzir logs nao sanitizados;
  - decidir se automacao externa e signature-only de proposito ou se precisa de outro modelo;
  - alinhar documentacao e comportamento real.

## ALERTAS ESTRATEGICOS
- `playstore` e `nonplay` nao estao realmente separados no artefato Android. O build muda flags em `app/build.gradle.kts`, mas eu nao encontrei source sets/manifests especificos por flavor; entao a variante Play herda o mesmo `AndroidManifest.xml` com `ACCESS_MOCK_LOCATION`, `SYSTEM_ALERT_WINDOW`, `BOOT_COMPLETED`, widget, tile e receivers de automacao/schedule.
- O modo GPX esta logicamente inconsistente no fluxo principal: o `ViewModel` pre-carrega a rota, mas o `SimulationService` a apaga antes de usa-la.
- Favoritos, replay, tile, widget e schedule compartilham uma abstracao de configuracao insuficiente (`SimulationConfig`), entao varias superficies "rapidas" nao conseguem reconstruir a simulacao que o usuario configurou na UI principal.
- O modo joystick tem forte evidencia de estar incompleto: a overlay existe, o estado existe, o loop existe, mas o elo `JoystickView -> SimulationRepository.updateJoystickState()` nao existe no codigo inspecionado.
- O produto "promete" um motor de realismo mais sofisticado via `:engine` e `:realism-lab`, mas o runtime atual nao o utiliza. Hoje o valor real entregue e muito mais "mock GPS deterministico com superficies auxiliares" do que "simulacao GPS realista".

# 7. Testabilidade e qualidade
- Cobertura atual observada:
  - `:core`: boa para `GeoMath`, `Route` extensions e `LogSanitizer`.
  - `:engine`: boa para interpolacao, velocidade, filtros e validacao.
  - `:realism-lab`: boa para metricas.
  - `:app`: fraca e concentrada em parsers de rota/geocoding e um teste de semver.
- Lacunas importantes:
  - sem testes de `SimulationService`;
  - sem testes do fluxo `SimulationViewModel -> MainActivity -> Service`;
  - sem testes de Room migrations;
  - sem testes de favoritos/historico/schedule;
  - sem `androidTest`;
  - sem testes de manifests por flavor;
  - sem testes de integracao para overlay/widget/tile/automation.
- Ha um problema qualitativo na propria estrategia de testes do `:app`:
  - `OsrmRouteProviderTest` reimplementa o parser em helper local, em vez de exercitar o metodo real.
  - `GeocodingProviderTest` faz o mesmo.
  - `ProfileManagerSemverTest` replica a logica de `bumpPatch`.
- Isso enfraquece a garantia real; a cobertura parece maior do que e.
- Code smells concretos:
  - `SimulationService.kt` com 834 linhas.
  - `SimulationViewModel.kt` com 412 linhas e multiplos dominios.
  - `RouteEditorScreen.kt` e `ModePanels.kt` muito grandes.
  - `SimulationRepository.kt` acumulando responsabilidades heterogeneas.
  - Comentarios desalinhados com o codigo em `SimulationService`, `RouteEditorViewModel`, `AutomationReceiver` e `FloatingBubbleService`.
  - Estado morto/sem uso: `isManualMode`, `lowMemorySignal`, `previewPlayhead`, `SHIZUKU_ENABLED`, `ProfileManagerViewModel`.
- Os 10 testes de maior valor que faltam:
  1. `SimulationService` em `AppMode.GPX` deve consumir a rota pre-carregada sem limpa-la.
  2. `FloatingBubbleService`/`JoystickView` devem propagar `JoystickState` para `SimulationRepository`, e `SimulationService.runJoystickLoop()` deve reagir.
  3. `SimulationConfig`/favoritos/QS tile/widget devem reconstruir uma simulacao classica completa com inicio, fim, modo e repeat, ou falhar explicitamente.
  4. `History replay` deve reproduzir a configuracao original, nao os pinos atuais.
  5. `stopSimulation()` deve fechar historico exatamente uma vez e persistir status/duracao mesmo sob `stopSelf()/onDestroy()`.
  6. `elapsedTimeSec` deve refletir tempo real em 1 Hz, 5 Hz e 60 Hz.
  7. `InteractiveMap` nao deve chamar `updateRoute()/fitCamera()` a cada frame de `Running`.
  8. `SimulationService` deve carregar rota persistida por `routeId` quando presente.
  9. `ScheduleManager`/`ScheduleReceiver` devem validar conflito, start/stop e rearm apos reboot.
  10. A variante `playstore` deve ter manifest/comportamento diferentes da `nonplay`.
- Fato metodologico:
  - eu nao executei esses testes; a priorizacao vem da leitura do codigo e da criticidade do fluxo.

# 8. Tabela de problemas priorizados
| prioridade | achado | evidencia | impacto | esforco | recomendacao |
|---|---|---|---|---|---|
| critico | GPX e apagado antes do consumo no service | `SimulationViewModel.loadGpxFromUri()` faz `repository.emitRoute(route)`; `SimulationService.startSimulation()` limpa `repository.emitRoute(null)` antes do branch `AppMode.GPX` | quebra um modo principal do produto | baixo | nao limpar rota no start GPX; usar um `SimulationLaunchPlan` explicito |
| critico | Joystick nao esta ligado ao runtime | `JoystickView.state`, `SimulationRepository.updateJoystickState()`, `SimulationService.runJoystickLoop()`; nenhum caller de `updateJoystickState()` | modo joystick provavelmente nao funciona | baixo | coletar `JoystickView.state` em `FloatingBubbleService` e publicar no repositorio |
| critico | Configuracao persistida e insuficiente para replay/favorite/tile/widget | `SimulationConfig.kt`, `FavoriteSimulationEntity.kt`, `SimulationHistoryEntity.kt`, `GhostPinQsTile.kt`, `GhostPinWidget.kt` | varias superficies iniciam simulacao errada ou parcial | medio | substituir por um modelo persistido completo de sessao |
| critico | Service ignora `routeId` e nao usa rotas persistidas | `SimulationService` nao injeta `RouteRepository`; resolve rota so por coords/waypoints/`repository.route` | route editor, replay e favoritos nao convergem para o runtime | medio | carregar rota persistida por `routeId` no service |
| critico | Loop do service roda no main dispatcher e atualiza widget por frame | `SimulationService` usa `lifecycleScope.launch`; chama `GhostPinWidget.updateAll(...)` no loop | risco de jank, bateria, ANR e estado visual inconsistente | medio | mover runner para `Dispatchers.Default` e reduzir widget update para transicoes de estado |
| critico | `elapsedTimeSec` cresce por frame, nao por segundo | `elapsedSec++` no loop principal e no loop joystick | metricas/status/replay ficam incorretos | baixo | calcular a partir de relogio monotonico real |
| critico | Flavor `playstore` nao esta realmente isolado | `app/build.gradle.kts` so muda `BuildConfig`; nao ha `app/src/playstore`; `AndroidManifest.xml` continua unico | alto risco de publicacao/compliance | medio | separar manifest/source sets por flavor e remover componentes/permissoes do Play |
| importante | `ScheduleReceiver` exportado sem permissao | `AndroidManifest.xml` declara `android:exported="true"` sem protecao | aumenta superficie de ataque desnecessariamente | baixo | tornar receiver nao-exportado ou protege-lo |
| importante | Favoritos nao guardam coordenadas e ignoram speed/frequency | `FavoriteSimulationEntity` persiste `speedRatio/frequencyHz`; `resolveFavoriteConfig()` ignora esses campos e usa fallback/default para start | favoritos nao sao autossuficientes | medio | persistir origem/destino/modo e usar todos os campos salvos |
| importante | Replay de historico nao e replay real | `SimulationViewModel.applyReplayConfig()` usa `startLat/startLng` atuais; `SimulationHistoryEntity` nao guarda fim/modo | regressao funcional e expectativa errada do usuario | medio | persistir sessao completa e reconstrui-la no replay |
| importante | UI re-renderiza mapa/rota em excesso | `InteractiveMap` reage a `simulationState`; `MapController.updateRoute()` faz `fitCamera()` | degrada UX durante simulacao | medio | separar atualizacao de rota da atualizacao de posicao |
| importante | Runtime ignora `:engine` e `:realism-lab` | `SimulationService` usa interpolacao propria; grep sem uso de `RouteInterpolator`, `SpeedController`, `LayeredNoiseModel`, `RealismReport` no `:app` | arquitetura perde coesao e diferencial de produto | alto | integrar engine por etapas ou remover codigo morto do caminho critico |
| importante | Custom profiles existem no backend, nao no produto | `ProfileManager`, `ProfileEntity`, `ProfileManagerViewModel`; `SimulationViewModel.profiles` hardcoded | manutencao inutil e expectativa falsa de feature | medio | ou expor custom profiles na UI principal, ou simplificar o backend |
| importante | CI nao protege bem `:app` nem `playstore` | `.github/workflows/ci.yml` compila so `nonplay`, app tests com `continue-on-error`, lint nao bloqueante | regressões chegam facil | baixo | tornar CI bloqueante e incluir `playstoreDebug` |
| desejavel | Duas pipelines GPX/arquivo com comportamentos diferentes | `GpxParser.kt` vs `RouteFileParser.kt` | inconsistencia funcional e manutencao duplicada | medio | unificar parser/import pipeline |
| desejavel | Comentarios e docs divergentes do codigo | `AutomationReceiver` fala em Tasker/normal permission; `RouteEditorViewModel` promete validacao inline; `README.md` vazio; `AUTOMATION.md` ausente | aumenta custo cognitivo e erro operacional | baixo | revisar docs e remover promessas nao entregues |

# 9. Plano de acao
- 0-7 dias
  - Corrigir o bug GPX no `SimulationService`.
  - Corrigir o elo do joystick e bloquear `AppMode.JOYSTICK` sem overlay/bridge funcional.
  - Corrigir `elapsedTimeSec`.
  - Parar `GhostPinWidget.updateAll(...)` por frame.
  - Separar em `InteractiveMap` a atualizacao da rota da atualizacao da posicao.
  - Fechar `ScheduleReceiver` ou protege-lo.
  - Introduzir um `SimulationLaunchPlan` minimo com `mode`, `start`, `end`, `waypoints`, `routeRef`, `repeat`, `speedRatio`, `frequencyHz`.
  - Adicionar testes para GPX, joystick, stop/history e config reconstruction.
- 2-6 semanas
  - Refatorar `SimulationService` em componentes menores.
  - Fazer o service carregar rota persistida por `routeId`.
  - Integrar de verdade route editor/saved routes ao fluxo principal ou esconder a feature ate concluir.
  - Unificar `GpxParser` e `RouteFileParser`.
  - Tornar favoritos/historico/schedules baseados no novo `SimulationLaunchPlan`.
  - Expor custom profiles na UI principal ou remover o backlog morto.
  - Endurecer CI: `playstoreDebug`, app tests bloqueantes, migration tests.
- 2-3 meses
  - Integrar gradualmente `RouteInterpolator`, `SpeedController`, `TrajectoryValidator` e `LayeredNoiseModel` ao runtime.
  - Definir estrategia de produto por canal:
    - `nonplay`: versao completa de spoofing/overlay/automacao;
    - `playstore`: artefato realmente reduzido, com manifest e UX proprios.
  - Adicionar suite de testes instrumentados para service, widget, schedule e intents.
  - Revisar persistencia com chaves estaveis, FKs/indices e migracoes cobertas.
  - Decidir se `:realism-lab` entra no produto como diagnostico interno ou permanece so como ferramenta de engenharia.

# 10. Conclusao
- Decisao tecnica resumida:
  - O repositorio tem uma base modular boa e um nucleo Android funcional, mas o fluxo runtime principal precisa de correcao estrutural antes de novas features.
- O que preservar:
  - a divisao `:core` / `:engine` / `:realism-lab` / `:app`;
  - os modulos puros e seus testes;
  - `GeoMath`, `LogSanitizer`, `RouteFileParser` hardenizado;
  - o uso de wrapper, toolchain e KSP.
- O que refatorar primeiro:
  - o contrato de configuracao de simulacao;
  - o `SimulationService`;
  - o fluxo de integracao entre UI principal, favoritos/historico/schedule e rotas persistidas;
  - a separacao real entre `nonplay` e `playstore`.
- Julgamento final:
  - hoje o projeto parece mais um app Android de mock GPS com varias superficies auxiliares e um laboratorio tecnico paralelo do que uma plataforma plenamente coerente de "simulacao GPS realista".
  - O maior ganho nao esta em adicionar feature nova; esta em alinhar o runtime ao modelo de dominio ja existente.

**Top 10 arquivos mais criticos do projeto**
1. `app/src/main/kotlin/com/ghostpin/app/service/SimulationService.kt`
2. `app/src/main/kotlin/com/ghostpin/app/data/SimulationRepository.kt`
3. `app/src/main/kotlin/com/ghostpin/app/ui/SimulationViewModel.kt`
4. `app/src/main/kotlin/com/ghostpin/app/data/SimulationConfig.kt`
5. `app/src/main/kotlin/com/ghostpin/app/ui/InteractiveMap.kt`
6. `app/src/main/kotlin/com/ghostpin/app/ui/GhostPinScreen.kt`
7. `app/src/main/AndroidManifest.xml`
8. `app/src/main/kotlin/com/ghostpin/app/widget/GhostPinWidget.kt`
9. `app/src/main/kotlin/com/ghostpin/app/scheduling/ScheduleReceiver.kt`
10. `app/src/main/kotlin/com/ghostpin/app/ui/AppNavigation.kt`

**Top 5 refatoracoes com melhor custo-beneficio**
1. Criar um `SimulationLaunchPlan` completo e substituir `SimulationConfig`.
2. Extrair o loop e a resolucao de rota de `SimulationService` para classes dedicadas.
3. Corrigir o wiring de GPX/joystick e remover estados mortos.
4. Separar de verdade `playstore` e `nonplay` com source sets/manifests distintos.
5. Reduzir recomposicao/atualizacao per-frame em UI mapa e widget.

**Top 5 riscos de release/publicacao**
1. Variante `playstore` herdar permissoes e componentes de spoofing/overlay do manifest principal.
2. Uso explicito de mock location como proposta central do produto.
3. `SYSTEM_ALERT_WINDOW` e foreground service de localizacao iniciados por superficies secundarias.
4. `ScheduleReceiver` exportado sem protecao.
5. Dependencia de OSRM/Nominatim/OpenFreeMap publicos sem controle operacional.

**Top 5 testes que eu escreveria amanha**
1. `SimulationService` em GPX nao pode limpar a rota pre-carregada.
2. `FloatingBubbleService` deve propagar `JoystickView.state` para `SimulationRepository`.
3. `stopSimulation()` deve fechar historico uma unica vez e persistir o status correto.
4. Favorito/QS tile/widget devem reconstruir uma sessao completa ou falhar de forma explicita.
5. `InteractiveMap` nao deve refazer `fitCamera()` a cada `SimulationState.Running`.
