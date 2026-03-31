# GhostPin Sprint Backlog Completo

## Escopo
Este backlog foi derivado da inspecao tecnica consolidada em `GHOSTPIN_TECHNICAL_ANALYSIS.md`.

Objetivo:
1. Transformar todos os achados em itens executaveis.
2. Distribuir os itens por sprint de forma realista.
3. Nao deixar nenhum bug, issue, task, risco, investigacao, gap de teste ou inconsistencia de documentacao sem destino.

Observacao de rigor:
1. Este backlog e exaustivo em relacao aos achados observados no codigo inspecionado.
2. Itens marcados como investigacao existem porque a evidencia ainda nao fecha 100 por cento o problema em runtime, mas o risco existe e precisa ser validado.

## Convencoes
- Tipo: `Bug`, `Issue`, `Task`, `Investigation`, `Test`, `Docs`.
- Prioridade: `Critica`, `Alta`, `Media`, `Baixa`.
- Todos os itens abaixo ja estao alocados em sprint.

## Mapa de Epicos
- `RT`: Runtime de simulacao e orquestracao.
- `DATA`: persistencia, modelo de sessao e integridade de dados.
- `UI`: fluxo do usuario, Compose, navegacao e gerenciamento de estado.
- `ENG`: alinhamento entre `:app`, `:core`, `:engine` e `:realism-lab`.
- `INT`: integracoes externas e contratos de entrada.
- `SEC`: seguranca, superficies exportadas, compliance e distribuicao.
- `BLD`: build, variantes, CI e previsibilidade de release.
- `TST`: testes.
- `DOC`: documentacao e comentarios.

# Sprint 0 - Estabilizacao Critica

## Objetivo da sprint
Remover bugs que hoje quebram funcionalidades centrais, geram estados incorretos ou bloqueiam qualquer estrategia seria de release.

### RT-01 - Corrigir limpeza indevida da rota GPX
Tipo: Bug
Prioridade: Critica
Evidencia: `app/src/main/kotlin/com/ghostpin/app/ui/SimulationViewModel.kt` pre-carrega rota via `repository.emitRoute(route)` em `loadGpxFromUri()`. `app/src/main/kotlin/com/ghostpin/app/service/SimulationService.kt` executa `repository.emitRoute(null)` no inicio de `startSimulation()` antes do branch `AppMode.GPX`.
Tarefas:
1. Nao limpar `repository.route` quando o modo for `AppMode.GPX`.
2. Tornar explicito no service se a rota foi passada por preload, por Room ou por fetch remoto.
3. Emitir erro claro se o usuario iniciar GPX sem rota carregada.
4. Cobrir o fluxo com teste.
Criterios de aceite:
1. O fluxo picker GPX -> Start funciona sem corrida.
2. O service nao apaga a rota antes de usa-la.

### RT-02 - Ligar o joystick da overlay ao runtime real
Tipo: Bug
Prioridade: Critica
Evidencia: `app/src/main/kotlin/com/ghostpin/app/ui/overlay/JoystickView.kt` expoe `state`. `app/src/main/kotlin/com/ghostpin/app/data/SimulationRepository.kt` tem `updateJoystickState()`. `app/src/main/kotlin/com/ghostpin/app/service/SimulationService.kt` le `repository.joystickState.value` em `runJoystickLoop()`. Nao ha caller de `updateJoystickState()` no codigo inspecionado.
Tarefas:
1. Coletar `JoystickView.state` dentro de `FloatingBubbleService`.
2. Publicar o estado no `SimulationRepository`.
3. Garantir reset para magnitude zero quando a overlay for removida.
4. Adicionar teste do elo `JoystickView -> repository -> service`.
Criterios de aceite:
1. O modo joystick move a posicao simulada quando o thumb e arrastado.
2. Soltar o thumb zera a velocidade.

### RT-03 - Corrigir contagem de tempo de simulacao
Tipo: Bug
Prioridade: Critica
Evidencia: `SimulationService.kt` incrementa `elapsedSec++` por frame no loop principal e em `runJoystickLoop()`.
Tarefas:
1. Substituir o contador por tempo real com base em relogio monotono.
2. Separar `frameCount` de `elapsedTimeSec`.
3. Ajustar UI, historico e pausas para o novo contrato.
4. Cobrir com teste em 1 Hz, 5 Hz e 60 Hz.
Criterios de aceite:
1. `elapsedTimeSec` reflete tempo real.
2. Mudar a frequencia nao distorce o tempo exibido.

### RT-04 - Parar atualizacao de widget por frame
Tipo: Bug
Prioridade: Critica
Evidencia: `app/src/main/kotlin/com/ghostpin/app/widget/GhostPinWidget.kt` documenta atualizacao por mudanca de estado, mas `SimulationService.kt` chama `GhostPinWidget.updateAll(...)` a cada frame no loop classico e no loop joystick.
Tarefas:
1. Atualizar widget apenas em transicoes semanticas de estado.
2. Definir contrato de atualizacao para `Idle`, `FetchingRoute`, `Running`, `Paused`, `Error`.
3. Remover atualizacoes por frame do loop.
4. Adicionar teste basico de transicoes.
Criterios de aceite:
1. O widget nao recebe atualizacao por frame.
2. O estado exibido continua correto.

### RT-05 - Mover o loop da simulacao para dispatcher apropriado
Tipo: Issue
Prioridade: Critica
Evidencia: `SimulationService.kt` usa `lifecycleScope.launch` sem dispatcher explicito para o loop principal.
Tarefas:
1. Executar simulacao em `Dispatchers.Default` ou runner dedicado.
2. Manter apenas publicacao de estado/UI side effects na main thread quando necessario.
3. Medir impacto no mapa e no service.
Criterios de aceite:
1. O loop pesado nao roda na main thread.
2. Nao ha regressao funcional em pause/stop/resume.

### RT-06 - Eliminar corrida entre stop, onDestroy e fechamento de historico
Tipo: Bug
Prioridade: Critica
Evidencia: `SimulationService.stopSimulation()` chama `finishActiveHistory()` de forma assincrona e em seguida `stopSelf()`. `onDestroy()` tambem chama `stopSimulation()`.
Tarefas:
1. Garantir que fechamento do historico seja idempotente.
2. Remover dupla chamada destrutiva entre `stopSimulation()` e `onDestroy()`.
3. Definir ciclo de vida unico para cleanup.
4. Cobrir interrupcao, erro e encerramento normal com testes.
Criterios de aceite:
1. O historico fecha exatamente uma vez.
2. O status final fica correto em stop, erro e fim natural.

### RT-07 - Impedir sessao infinita com `speedRatio = 0`
Tipo: Bug
Prioridade: Alta
Evidencia: `SimulationService.kt` aceita `EXTRA_SPEED_RATIO` em `0.0..1.0`. No loop classico, `speedMs = profile.maxSpeedMs * speedRatio`, o que pode zerar o progresso indefinidamente.
Tarefas:
1. Decidir se `0.0` e invalido ou representa pausa explicita.
2. Se for invalido, rejeitar com erro antes de iniciar.
3. Se for valido, tratar como estado pausado controlado.
4. Cobrir com teste.
Criterios de aceite:
1. Nao existe simulacao infinita sem progresso por configuracao invalida.

### UI-01 - Parar `fitCamera()` e redesenho de rota a cada frame
Tipo: Bug
Prioridade: Critica
Evidencia: `app/src/main/kotlin/com/ghostpin/app/ui/InteractiveMap.kt` reage a `simulationState` e chama `MapController.updateRoute(...)` durante `Running`. `app/src/main/kotlin/com/ghostpin/app/ui/MapController.kt` executa `fitCamera()` em `updateRoute(route)`.
Tarefas:
1. Separar atualizacao de rota da atualizacao de posicao.
2. Fazer `fitCamera()` apenas em eventos de carregamento/edicao de rota.
3. Garantir que durante `Running` apenas a posicao e atualizada.
4. Cobrir com teste de comportamento e benchmark simples.
Criterios de aceite:
1. O mapa nao recentraliza durante a simulacao por frame.
2. A posicao continua animando corretamente.

### SEC-01 - Fechar ou proteger `ScheduleReceiver`
Tipo: Issue
Prioridade: Critica
Evidencia: `app/src/main/AndroidManifest.xml` declara `app/src/main/kotlin/com/ghostpin/app/scheduling/ScheduleReceiver.kt` como `android:exported="true"` sem permissao.
Tarefas:
1. Tornar o receiver nao-exportado se so for usado por `AlarmManager` interno.
2. Se precisar ser exportado, adicionar permissao adequada.
3. Validar se `BOOT_COMPLETED` continua funcionando com a configuracao escolhida.
4. Cobrir com teste de manifest.
Criterios de aceite:
1. A superficie publica desnecessaria deixa de existir.

### SEC-02 - Separar imediatamente a variante `playstore` da `nonplay`
Tipo: Issue
Prioridade: Critica
Evidencia: `app/build.gradle.kts` define flavors, mas nao existem source sets `app/src/playstore` e `app/src/nonplay`; o `AndroidManifest.xml` principal continua unico.
Tarefas:
1. Criar source sets/manifests especificos por flavor.
2. Remover do flavor `playstore` componentes e permissoes incompatíveis com a estrategia de publicacao.
3. Ajustar onboarding e UX por flavor.
4. Adicionar validacao de variantes em CI.
Criterios de aceite:
1. `playstore` e `nonplay` geram manifests e comportamento efetivamente diferentes.

# Sprint 1 - Modelo de Sessao e Persistencia Confiavel

## Objetivo da sprint
Tornar favoritos, replay, agendamento, widget e tile capazes de reconstruir uma simulacao correta, persistente e auditavel.

### DATA-01 - Substituir `SimulationConfig` por um modelo completo de sessao
Tipo: Issue
Prioridade: Critica
Evidencia: `app/src/main/kotlin/com/ghostpin/app/data/SimulationConfig.kt` persiste apenas `profileName`, `startLat`, `startLng`, `routeId`, `repeatPolicy`, `repeatCount`.
Tarefas:
1. Criar `SimulationLaunchPlan` ou nome equivalente.
2. Incluir `mode`, `start`, `end`, `waypoints`, `routeId`, `repeat`, `speedRatio`, `frequencyHz`, `waypointPauseSec` e origem da rota.
3. Migrar todos os consumidores do `SimulationConfig` atual.
4. Definir politica de backward compatibility para dados existentes.
Criterios de aceite:
1. Um unico modelo representa a configuracao completa de uma sessao.

### DATA-02 - Tornar favoritos autossuficientes
Tipo: Bug
Prioridade: Critica
Evidencia: `FavoriteSimulationEntity` guarda `speedRatio` e `frequencyHz`, mas `resolveFavoriteConfig()` em `SimulationRepository.kt` ignora esses campos e reconstrui inicio via fallback/default.
Tarefas:
1. Persistir plano completo no favorito.
2. Parar de depender de `lastUsedConfig` para reconstruir favorito.
3. Validar favoritos de rota persistida e de rota nao persistida.
4. Adicionar testes de favoritos validos e invalidos.
Criterios de aceite:
1. Um favorito inicia a simulacao correta mesmo apos morte de processo.

### DATA-03 - Tornar replay de historico fiel
Tipo: Bug
Prioridade: Critica
Evidencia: `SimulationViewModel.applyReplayConfig()` usa `startLat/startLng` atuais e `SimulationHistoryEntity` nao guarda fim, modo nem waypoints.
Tarefas:
1. Persistir plano completo da sessao no historico.
2. Reproduzir o plano original no replay.
3. Diferenciar replay de sessao ephemera e replay de sessao com rota persistida.
4. Cobrir com testes de replay classico, GPX e waypoints.
Criterios de aceite:
1. O replay nao depende dos pinos atuais da UI.

### DATA-04 - Carregar rota persistida real por `routeId`
Tipo: Bug
Prioridade: Critica
Evidencia: `SimulationService.kt` recebe `EXTRA_ROUTE_ID`, mas nao injeta `RouteRepository` nem busca rota persistida.
Tarefas:
1. Injetar `RouteRepository` ou provider de planos persistidos no service.
2. Carregar a rota por `routeId` antes de decidir fetch remoto.
3. Definir precedencia entre rota persistida, GPX preload e fetch OSRM.
4. Cobrir com teste.
Criterios de aceite:
1. `routeId` deixa de ser decorativo.

### DATA-05 - Decidir como lidar com rotas runtime nao persistidas
Tipo: Issue
Prioridade: Alta
Evidencia: rotas geradas por OSRM/GPX no runtime usam IDs transitorios e nao sao salvas em `RouteDao`; favoritos e historico podem guardar `routeId` invalido.
Tarefas:
1. Decidir se rotas runtime devem ser persistidas automaticamente.
2. Alternativamente, impedir salvar favorito com `routeId` efemero.
3. Exibir feedback claro ao usuario.
4. Cobrir com teste.
Criterios de aceite:
1. Nao existem favoritos/historicos com `routeId` intrinsecamente invalido sem explicacao.

### DATA-06 - Enriquecer `ScheduleEntity` para representar a sessao real
Tipo: Issue
Prioridade: Alta
Evidencia: `app/src/main/kotlin/com/ghostpin/app/scheduling/ScheduleEntity.kt` persiste apenas start, stop, profile, start coords, speed e frequency.
Tarefas:
1. Persistir plano completo da sessao agendada.
2. Permitir modo, rota, repeat e waypoints quando aplicavel.
3. Ajustar `ScheduleViewModel`, `ScheduleReceiver` e `ScheduleManager`.
4. Cobrir rearm e execucao completa em teste.
Criterios de aceite:
1. Agendamento consegue iniciar a mesma sessao configurada pelo usuario.

### DATA-07 - Limpar estado em memoria obsoleto ao resetar repositorio
Tipo: Bug
Prioridade: Alta
Evidencia: `SimulationRepository.reset()` limpa apenas `_state` e `_route`, deixando `lastUsedConfig`, `isManualMode` e `joystickState` intactos.
Tarefas:
1. Definir exatamente o que deve sobreviver a `reset()`.
2. Separar estado de sessao ativa de estado lembrado para quick-start.
3. Garantir reset de joystick/manual quando apropriado.
4. Cobrir com teste.
Criterios de aceite:
1. Nao ha vazamento de estado antigo entre sessoes.

### DATA-08 - Persistir ultimo plano utilizavel alem do processo atual
Tipo: Issue
Prioridade: Alta
Evidencia: `SimulationRepository.lastUsedConfig` e apenas `MutableStateFlow` em memoria; tile/widget/favoritos degradam apos morte de processo.
Tarefas:
1. Persistir o ultimo plano valido em Room ou DataStore.
2. Reidratar no boot do app/service.
3. Diferenciar plano valido de plano parcial/invalido.
4. Cobrir com teste de morte de processo simulada.
Criterios de aceite:
1. Quick-start surfaces funcionam mesmo apos cold start.

### DATA-09 - Migrar `profileIdOrName` para IDs estaveis
Tipo: Issue
Prioridade: Alta
Evidencia: `ProfileManager.kt` define IDs estaveis de built-ins, mas `SimulationService`, `SimulationViewModel`, favoritos e historico persistem nome humano.
Tarefas:
1. Definir identificador canonico de perfil.
2. Migrar favoritos, historico e schedules para ID estavel.
3. Manter estrategia de resolucao para dados antigos.
4. Cobrir com testes de migracao e resolucao.
Criterios de aceite:
1. Renomear perfil nao quebra persistencia.

### DATA-10 - Adicionar FKs e indices onde a integridade exigir
Tipo: Issue
Prioridade: Media
Evidencia: `app/schemas/com.ghostpin.app.data.db.GhostPinDatabase/4.json` mostra `indices: []` e `foreignKeys: []` para todas as tabelas.
Tarefas:
1. Definir relacionamentos reais entre rotas, historico, favoritos e perfis.
2. Adicionar indices para consultas principais.
3. Avaliar cascatas versus validacao manual.
4. Adicionar migracoes correspondentes.
Criterios de aceite:
1. O schema reflete as dependencias reais do dominio.

### DATA-11 - Restaurar trilha completa de schema exportado e cobrir migracoes
Tipo: Issue
Prioridade: Alta
Evidencia: `GhostPinDatabase` esta na versao 4, mas o diretorio de schemas nao contem `3.json`.
Tarefas:
1. Regenerar e commitar o schema faltante.
2. Adicionar testes de migracao `1->2->3->4`.
3. Validar identidade de schema na CI.
Criterios de aceite:
1. Todas as versoes persistidas tem schema exportado e testado.

### DATA-12 - Decidir estrategia para blobs JSON em `RouteEntity`
Tipo: Issue
Prioridade: Media
Evidencia: `RouteEntity.kt` usa `waypointsJson` e `segmentsJson` sem schema versionado.
Tarefas:
1. Decidir entre manter blobs com versionamento interno ou normalizar tabelas.
2. Se blobs forem mantidos, adicionar versao de payload e parser robusto.
3. Cobrir round-trip e backward compatibility.
Criterios de aceite:
1. Evolucao de rotas persistidas deixa de ser ad hoc.

# Sprint 2 - Fluxo do Usuario, UI e Estado

## Objetivo da sprint
Reduzir fragilidade de estado na UI principal, concluir fluxos incompletos e alinhar experiencia real do usuario com as features expostas.

### UI-02 - Unificar o fluxo de permissoes e onboarding
Tipo: Issue
Prioridade: Alta
Evidencia: `MainActivity.kt` pede permissoes em `requestPermissions()`, enquanto `OnboardingScreen.kt` possui um passo inteiro para permissoes.
Tarefas:
1. Escolher um unico ponto de orquestracao de permissoes.
2. Garantir que onboarding mostre o estado real sem duplicar prompts.
3. Ajustar UX por flavor.
Criterios de aceite:
1. Nao existem prompts duplicados ou fluxo contraditorio.

### UI-03 - Impedir que `initializeLocation()` sobrescreva configuracao do usuario
Tipo: Bug
Prioridade: Alta
Evidencia: `AppNavigation.kt` chama `initializeLocation()` ao entrar em `MAIN` e `GhostPinScreen.kt` chama novamente quando o estado volta a `Idle`; `SimulationViewModel.initializeLocation()` muta start/end.
Tarefas:
1. Tornar bootstrap de localizacao idempotente e one-shot.
2. Nao sobrescrever pinos/manualmente configurados.
3. Diferenciar estado inicial de estado editado pelo usuario.
4. Cobrir com teste.
Criterios de aceite:
1. Parar uma simulacao nao reseta os pinos do usuario indevidamente.

### UI-04 - Fatiar coleta de estado em `GhostPinScreen`
Tipo: Issue
Prioridade: Alta
Evidencia: `GhostPinScreen.kt` coleta um conjunto grande de `StateFlow`s na raiz, inclusive `simulationState` que muda por frame.
Tarefas:
1. Quebrar a tela em observadores menores.
2. Conter recomposicoes em subarvores apropriadas.
3. Medir recomposicoes antes/depois.
Criterios de aceite:
1. A tela principal nao recompõe tudo em cada frame.

### UI-05 - Tornar o Start/Stop consciente do modo e da prontidao
Tipo: Issue
Prioridade: Alta
Evidencia: o FAB global de `GhostPinScreen.kt` usa `onStartSimulation(selectedProfile, 0.0)` sem diferenciar readiness de `GPX`, `WAYPOINTS` e `CLASSIC`.
Tarefas:
1. Definir requisitos de inicio por modo.
2. Desabilitar ou adaptar o CTA quando o plano estiver incompleto.
3. Exibir motivo de bloqueio na UI.
Criterios de aceite:
1. O usuario nao inicia sessao invalida por falta de contexto.

### UI-06 - Conectar o route editor ao fluxo principal de simulacao
Tipo: Bug
Prioridade: Alta
Evidencia: `RouteEditorScreen.kt` tem `onRouteReady`, mas `AppNavigation.kt` instancia `RouteEditorScreen(onBack = ...)` sem passar callback.
Tarefas:
1. Ligar `onRouteReady` ao `SimulationViewModel` e ao fluxo de start.
2. Decidir se rota editada deve virar `routeId` persistido, preload em memoria ou novo modo.
3. Cobrir com teste de navegacao.
Criterios de aceite:
1. "Iniciar com esta rota" realmente inicia uma simulacao.

### UI-07 - Implementar import/export real no route editor
Tipo: Issue
Prioridade: Alta
Evidencia: `RouteEditorViewModel` prepara export/import, mas `RouteEditorScreen.kt` apenas limpa `exportContent` sem acionar share/save real.
Tarefas:
1. Implementar picker/saver/share reais no host Android.
2. Ligar import de arquivo ao `RouteEditorViewModel.importFromContent()`.
3. Exibir feedback de sucesso/erro.
Criterios de aceite:
1. Export e import funcionam de ponta a ponta.

### UI-08 - Decidir o destino da feature de perfis customizados
Tipo: Issue
Prioridade: Alta
Evidencia: `ProfileManager`, `ProfileEntity` e `ProfileManagerViewModel` existem, mas `SimulationViewModel.profiles` usa apenas built-ins e nao ha tela conectada de gestao.
Tarefas:
1. Decidir se perfis customizados entram no produto agora.
2. Se sim, expor na UI principal e na gestao dedicada.
3. Se nao, reduzir backend/UI mortos.
Criterios de aceite:
1. A feature deixa de estar parcialmente implementada.

### UI-09 - Tratar estados mortos ou incompletos da UI/runtime
Tipo: Issue
Prioridade: Media
Evidencia: `lowMemorySignal`, `previewPlayhead`, `isManualMode`, `SHIZUKU_ENABLED`, `ProfileManagerViewModel` nao participam do fluxo principal concluido.
Tarefas:
1. Remover o que for de fato morto.
2. Implementar o que for desejado.
3. Atualizar comentarios e docs.
Criterios de aceite:
1. Nao existem estados expostos sem uso ou promessa falsa.

### UI-10 - Alinhar UX de favoritos, historico, agendamento e rotas salvas
Tipo: Issue
Prioridade: Media
Evidencia: as superficies existem, mas hoje usam modelos diferentes e planos parciais.
Tarefas:
1. Unificar nomenclatura e comportamento.
2. Tornar claro quando uma sessao depende de rota persistida, preload ou fetch remoto.
3. Padronizar mensagens e validacoes.
Criterios de aceite:
1. Todas as superficies de quick-start operam sobre o mesmo contrato de sessao.

# Sprint 3 - Alinhamento com Core, Engine e Realism Lab

## Objetivo da sprint
Fazer o app usar de verdade o motor puro que o repositorio ja possui, reduzindo duplicacao e aumentando confiabilidade da simulacao.

### ENG-01 - Substituir interpolacao manual por `RouteInterpolator`
Tipo: Issue
Prioridade: Alta
Evidencia: `SimulationService.kt` possui `interpolate()`, `computeBearing()` e `haversineMeters()` locais; `engine/src/main/kotlin/com/ghostpin/engine/interpolation/RouteInterpolator.kt` nao e usado no app.
Tarefas:
1. Encapsular o calculo de frame na engine.
2. Migrar bearing/progresso para `RouteInterpolator`.
3. Remover duplicacao local.
Criterios de aceite:
1. O service nao implementa interpolacao propria.

### ENG-02 - Unificar politica de velocidade com `SpeedController`
Tipo: Issue
Prioridade: Alta
Evidencia: `Route.estimateDuration()` usa 80 por cento da velocidade maxima; `SpeedController` usa 65 por cento e nao participa do runtime.
Tarefas:
1. Escolher uma politica canonica de velocidade.
2. Usar `SpeedController` no runtime ou remover a divergencia.
3. Ajustar ETA, UI e testes.
Criterios de aceite:
1. ETA, execucao real e testes usam o mesmo modelo de velocidade.

### ENG-03 - Aplicar `TrajectoryValidator` antes do start
Tipo: Issue
Prioridade: Alta
Evidencia: `TrajectoryValidator` e injetado em `SimulationService.kt` mas nao e usado.
Tarefas:
1. Validar rotas e overrides antes de iniciar a sessao.
2. Exibir warnings ou bloqueios dependendo da gravidade.
3. Cobrir com testes.
Criterios de aceite:
1. Rotas fisicamente absurdas sao detectadas antes do loop.

### ENG-04 - Integrar `LayeredNoiseModel` ao runtime
Tipo: Issue
Prioridade: Alta
Evidencia: `LayeredNoiseModel` existe em `:engine`, e `SimulationModule.kt` o prove, mas `SimulationService` injeta localizacao limpa.
Tarefas:
1. Definir pipeline `RouteInterpolator -> SpeedController -> LayeredNoiseModel -> MockLocationInjector`.
2. Configurar por `MovementProfile`.
3. Permitir ligamento/desligamento controlado para debug.
4. Cobrir com testes e comparativos.
Criterios de aceite:
1. O runtime usa o modelo de ruido real do projeto.

### ENG-05 - Decidir como `realism-lab` participa do produto
Tipo: Issue
Prioridade: Media
Evidencia: `realism-lab` nao aparece em `:app`.
Tarefas:
1. Decidir se ele vira diagnostico interno, modo debug, ferramenta de QA ou modulo apenas offline.
2. Integrar minimamente onde fizer sentido.
3. Documentar a decisao.
Criterios de aceite:
1. O modulo tem papel claro no produto.

### ENG-06 - Suportar altitude e `SegmentOverrides` no runtime
Tipo: Issue
Prioridade: Media
Evidencia: `RouteEditorViewModel.kt` persiste `segments` e `SegmentOverrides`; `SimulationService.kt` ignora altitude e overrides, e `estimateAltitude()` retorna `0.0`.
Tarefas:
1. Definir semantica real de altitude no runtime.
2. Aplicar speed override, pause e loop de segmento se a feature continuar no produto.
3. Se nao continuar, remover da UI/dominio para reduzir mentira arquitetural.
Criterios de aceite:
1. Os dados do route editor ou sao usados de verdade, ou deixam de ser prometidos.

### ENG-07 - Resolver inconsistencias de distancia geoespacial
Tipo: Issue
Prioridade: Media
Evidencia: `core/model/Route.kt` usa raio `6378137.0`; `core/math/GeoMath.kt` usa `6371000.0`.
Tarefas:
1. Eleger implementacao canonica de distancia.
2. Centralizar todo calculo em `GeoMath`.
3. Ajustar testes e tolerancias.
Criterios de aceite:
1. Nao existem duas APIs de distancia com respostas divergentes sem motivo.

### ENG-08 - Resolver inconsistencia semantica de `pauseDurationSec`
Tipo: Issue
Prioridade: Media
Evidencia: `Route.estimateDuration()` exclui pausas; `TrajectoryValidator.kt` interpreta `pauseDurationSec` como se determinasse velocidade requerida.
Tarefas:
1. Definir contrato unico para `pauseDurationSec`.
2. Ajustar validator, UI e docs.
3. Adicionar testes.
Criterios de aceite:
1. O significado de pausa e unico no projeto inteiro.

### ENG-09 - Corrigir mismatch entre doc e implementacao do `RouteInterpolator`
Tipo: Issue
Prioridade: Media
Evidencia: o arquivo documenta parametrizacao por arc length uniforme na curva, mas a implementacao usa distancias acumuladas por waypoints e Catmull-Rom local.
Tarefas:
1. Ou implementar reparametrizacao real por arc length.
2. Ou corrigir a documentacao para o comportamento real.
3. Adicionar teste de uniformidade se a claim for mantida.
Criterios de aceite:
1. Documentacao e implementacao dizem a mesma coisa.

### ENG-10 - Revisar parametrizacao de tunel/signal loss
Tipo: Investigation
Prioridade: Media
Evidencia: `MovementProfile.kt` nomeia `tunnelDurationMeanSec` como segundos; `TunnelNoiseModel.kt` usa esses valores como `mu` lognormal.
Tarefas:
1. Validar se os parametros foram calibrados em espaco log ou linear.
2. Corrigir nomes, docs ou formula.
3. Ajustar testes de ruido conforme a decisao.
Criterios de aceite:
1. O significado numerico dos parametros de tunel e inequívoco.

### ENG-11 - Decidir o destino de `SegmentOverrides.loop`
Tipo: Issue
Prioridade: Baixa
Evidencia: `SegmentOverrides.loop` existe em `core/model/Route.kt`, mas nao participa do runtime nem da engine observada.
Tarefas:
1. Implementar a feature se ela fizer parte do produto.
2. Ou remover o campo e migrar dados.
Criterios de aceite:
1. Nao existe campo de dominio sem semantica operacional.

### ENG-12 - Eliminar `Route.durationSeconds` como footgun
Tipo: Issue
Prioridade: Media
Evidencia: `Route.durationSeconds` em `core/model/Route.kt` sempre retorna `0.0` por compatibilidade.
Tarefas:
1. Substituir usos remanescentes por `estimateDuration()`.
2. Planejar remocao ou encapsulamento claro.
3. Documentar de forma mais forte enquanto existir.
Criterios de aceite:
1. Nenhum fluxo novo usa a propriedade enganosa.

### ENG-13 - Revisar penalidades duplicadas de acuracia no modelo de ruido
Tipo: Investigation
Prioridade: Baixa
Evidencia: `LayeredNoiseModel.kt` e `SensorCoherenceFilter.kt` aplicam penalidades ligadas a jump/velocidade.
Tarefas:
1. Confirmar se a duplicacao e intencional.
2. Ajustar formula ou docs.
3. Adicionar testes dedicados do pipeline.
Criterios de aceite:
1. A degradacao de acuracia e explicavel e testada.

# Sprint 4 - Integracoes Externas e Contratos de Entrada

## Objetivo da sprint
Fortalecer resiliencia de integracoes externas, unificar pipelines de arquivo e fechar inconsistencias de automacao.

### INT-01 - Unificar `GpxParser` e `RouteFileParser`
Tipo: Issue
Prioridade: Alta
Evidencia: `GpxParser.kt` e `RouteFileParser.kt` coexistem e sao usados por fluxos diferentes.
Tarefas:
1. Definir um parser canonico por formato.
2. Reaproveitar streaming ou DOM conforme a necessidade real.
3. Garantir consistencia de nome, altitude, validacao e erros.
4. Ajustar testes.
Criterios de aceite:
1. GPX importado pela UI e por automacao segue o mesmo contrato.

### INT-02 - Hardening de OSRM
Tipo: Issue
Prioridade: Media
Evidencia: `OsrmRouteProvider.kt` usa o demo publico do OSRM, com timeout e fallback, sem retry, cache ou backoff.
Tarefas:
1. Definir estrategia para rate limit/indisponibilidade.
2. Adicionar observabilidade minima e retry/backoff controlado.
3. Decidir se havera backend proprio ou isso continuara best-effort.
Criterios de aceite:
1. Falhas de OSRM nao degradam a UX de forma opaca.

### INT-03 - Hardening de Nominatim e UX de erro
Tipo: Issue
Prioridade: Media
Evidencia: `GeocodingProvider.kt` documenta limite de 1 req/s; `SimulationViewModel.searchAddress()` usa debounce de 400 ms e nao ha rate limiter real.
Tarefas:
1. Implementar rate limiter coerente com a politica do provedor.
2. Melhorar feedback de erro para o usuario.
3. Adicionar cache simples de sugestoes repetidas se fizer sentido.
Criterios de aceite:
1. O app nao spamma Nominatim acima do limite pretendido.

### INT-04 - Definir estrategia de mapa remoto e fallback offline
Tipo: Issue
Prioridade: Media
Evidencia: `MapController.kt` usa estilo remoto `https://tiles.openfreemap.org/styles/liberty` sem fallback observado.
Tarefas:
1. Definir comportamento offline/degradado.
2. Tratar falha de carregamento de estilo.
3. Documentar dependencia externa.
Criterios de aceite:
1. Falha do provider de tiles nao derruba a usabilidade sem explicacao.

### INT-05 - Alinhar contrato real de automacao
Tipo: Issue
Prioridade: Alta
Evidencia: `AutomationReceiver.kt` diz ser para ADB/Tasker e cita protection level incorreto; o manifest define permissao `signature`.
Tarefas:
1. Decidir se a automacao sera signature-only, adb-only, app-companion ou aberta.
2. Ajustar implementacao, docs e exemplos ao contrato real.
3. Se Tasker nao for suportado, remover a promessa do codigo/docs.
Criterios de aceite:
1. O contrato de automacao descrito bate com o comportamento real.

### INT-06 - Validar inputs externos e permissao de URI em `ACTION_SET_ROUTE`
Tipo: Issue
Prioridade: Alta
Evidencia: `SimulationService.kt` abre `contentResolver.openInputStream(uri)` ao receber `ACTION_SET_ROUTE` sem validacao adicional de permissao concedida pelo emissor.
Tarefas:
1. Validar esquemas de URI suportados.
2. Tratar explicitamente falta de permissao/grant temporario.
3. Sanitizar mensagens de erro e logs.
4. Cobrir com teste.
Criterios de aceite:
1. Entradas externas malformadas nao quebram o service.

### INT-07 - Validar requisitos de exact alarms
Tipo: Investigation
Prioridade: Media
Evidencia: `ScheduleManager.kt` usa `setExactAndAllowWhileIdle()`, e o manifest nao declara permissao especifica de exact alarm.
Tarefas:
1. Verificar comportamento em Android recentes.
2. Decidir se o app precisa de permissao especial, fluxo de consentimento ou fallback.
3. Documentar a decisao.
Criterios de aceite:
1. A estrategia de agendamento e compatível com as APIs alvo suportadas.

### INT-08 - Validar compatibilidade Android de `ProviderProperties`
Tipo: Investigation
Prioridade: Media
Evidencia: `MockLocationInjector.kt` usa `android.location.provider.ProviderProperties` com `minSdk = 26`; esse ponto precisa de validacao pratica/compilacao direcionada.
Tarefas:
1. Confirmar compatibilidade da API com o `minSdk` real.
2. Ajustar implementacao se necessario.
3. Cobrir com build/testes de variante.
Criterios de aceite:
1. O caminho de mock provider e seguro para o `minSdk` suportado.

# Sprint 5 - Seguranca, Distribuicao e Build

## Objetivo da sprint
Fechar riscos de publicacao, tornar o build previsivel e endurecer superficies expostas.

### SEC-03 - Remover componentes e permissoes nao compativeis do flavor `playstore`
Tipo: Issue
Prioridade: Critica
Evidencia: hoje o manifest principal concentra `ACCESS_MOCK_LOCATION`, `SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`, widget, tile e receivers exportados.
Tarefas:
1. Definir manifest dedicado do `playstore`.
2. Remover ou neutralizar componentes nao compativeis.
3. Ajustar UX e strings para o flavor.
Criterios de aceite:
1. O artefato `playstore` nao declara capacidades que a estrategia de publicacao nao suporta.

### SEC-04 - Sanitizar logs de forma consistente
Tipo: Issue
Prioridade: Alta
Evidencia: `SimulationService.kt` e `AutomationReceiver.kt` usam `LogSanitizer` em alguns pontos; `OsrmRouteProvider.kt`, `GeocodingProvider.kt` e outros logs nao seguem a mesma disciplina.
Tarefas:
1. Definir politica central de logging sensivel.
2. Aplicar a politica nos pontos de geocoding, routing, parsing e runtime.
3. Cobrir com testes onde possivel.
Criterios de aceite:
1. Coordenadas sensiveis nao vazam em logs operacionais indevidamente.

### SEC-05 - Revisar todas as superficies exportadas e intents publicas
Tipo: Issue
Prioridade: Alta
Evidencia: alem do `ScheduleReceiver`, ha `AutomationReceiver`, QS tile, broadcasts e intents de service que precisam de matriz clara de exposicao.
Tarefas:
1. Inventariar cada action publica.
2. Definir permissao, origem esperada e validacao por componente.
3. Endurecer o que estiver exposto alem do necessario.
Criterios de aceite:
1. Toda superficie exportada tem razao explicita e protecao adequada.

### BLD-01 - Tornar testes do `:app` bloqueantes na CI
Tipo: Issue
Prioridade: Alta
Evidencia: `.github/workflows/ci.yml` usa `continue-on-error: true` para `:app:testNonplayDebugUnitTest`.
Tarefas:
1. Corrigir o que hoje impede tornar o job bloqueante.
2. Remover `continue-on-error`.
3. Adicionar reports claros de falha.
Criterios de aceite:
1. Regressao em `:app` quebra CI.

### BLD-02 - Compilar e testar `playstoreDebug` na CI
Tipo: Issue
Prioridade: Alta
Evidencia: a CI so compila `:app:compileNonplayDebugKotlin`.
Tarefas:
1. Adicionar compile do flavor `playstoreDebug`.
2. Adicionar testes pertinentes ou smoke checks por flavor.
Criterios de aceite:
1. Divergencias de flavor aparecem na CI.

### BLD-03 - Incluir `realism-lab` na esteira de CI
Tipo: Issue
Prioridade: Media
Evidencia: `.github/workflows/ci.yml` nao executa `:realism-lab:test`.
Tarefas:
1. Adicionar job ou etapa de teste do modulo.
2. Publicar report como artifact.
Criterios de aceite:
1. O modulo nao fica fora da validacao automatica.

### BLD-04 - Tornar lint realmente acionavel
Tipo: Issue
Prioridade: Media
Evidencia: o job de ktlint usa `|| true`, logo nunca falha o workflow.
Tarefas:
1. Decidir se lint passa a ser gate.
2. Corrigir baseline necessaria.
3. Remover `|| true` quando viavel.
Criterios de aceite:
1. O lint tem efeito real na qualidade do branch.

### BLD-05 - Remover ou implementar `SHIZUKU_ENABLED`
Tipo: Issue
Prioridade: Media
Evidencia: `app/build.gradle.kts` define `SHIZUKU_ENABLED`, mas nao ha uso no codigo Kotlin inspecionado.
Tarefas:
1. Confirmar se a feature faz parte do roadmap.
2. Se sim, implementar com source set adequado.
3. Se nao, remover a flag e comentarios correlatos.
Criterios de aceite:
1. O build nao carrega feature flags mortas.

# Sprint 6 - Testes de Alto Valor e Documentacao

## Objetivo da sprint
Cobrir o caminho critico com testes reais, parar de validar copias locais de logica e fechar lacunas de documentacao operacional.

### TST-01 - Testes do `SimulationService`
Tipo: Test
Prioridade: Critica
Cobertura minima:
1. GPX preload.
2. Joystick bridge.
3. Stop/pause/resume.
4. Fechamento de historico.
5. Carga por `routeId`.
6. `speedRatio` invalido.
7. Atualizacao de widget apenas por transicao.

### TST-02 - Testes de repositorio e reconstrucao de plano
Tipo: Test
Prioridade: Critica
Cobertura minima:
1. Favoritos autossuficientes.
2. Replay fiel.
3. Rehidratacao do ultimo plano apos cold start.
4. Invalidacao de rotas/perfis inexistentes.

### TST-03 - Testes de schedule
Tipo: Test
Prioridade: Alta
Cobertura minima:
1. `createSchedule()`.
2. conflito de horarios.
3. `cancelSchedule()`.
4. `rearmPersistedSchedules()`.
5. start e stop reais via receiver.

### TST-04 - Testes de migracao e persistencia Room
Tipo: Test
Prioridade: Alta
Cobertura minima:
1. `1->2->3->4`.
2. rotas com blobs JSON.
3. perfis built-in e custom.
4. favoritos, historico e schedules com novos campos.

### TST-05 - Testes de manifest e comportamento por flavor
Tipo: Test
Prioridade: Alta
Cobertura minima:
1. `nonplay` possui capacidades esperadas.
2. `playstore` nao herda componentes proibidos.
3. onboarding e UX por flavor.

### TST-06 - Parar de testar reimplementacoes locais da logica de producao
Tipo: Test
Prioridade: Alta
Evidencia: `OsrmRouteProviderTest.kt`, `GeocodingProviderTest.kt` e `ProfileManagerSemverTest.kt` duplicam a logica em helpers locais.
Tarefas:
1. Expor pontos testaveis de producao de forma controlada.
2. Reescrever testes para exercitar a implementacao real.
Criterios de aceite:
1. Os testes do `:app` falham quando a implementacao real quebra.

### TST-07 - Adicionar testes do pipeline `LayeredNoiseModel`
Tipo: Test
Prioridade: Media
Evidencia: ha testes de componentes individuais, mas nao foi observado teste dedicado do pipeline completo.
Tarefas:
1. Cobrir freeze em tunel.
2. Cobrir quantizacao.
3. Cobrir coerencia de acuracia e monotonicidade temporal.

### TST-08 - Cobrir validacao de turn rate
Tipo: Test
Prioridade: Media
Evidencia: `TrajectoryValidatorTest.kt` nao cobre o branch de bearing/turn rate.
Tarefas:
1. Adicionar casos validos e invalidos para curvas impossiveis.

### TST-09 - Cobrir uniformidade de velocidade da interpolacao spline
Tipo: Test
Prioridade: Media
Evidencia: ha testes de monotonicidade e no-NaN, mas nao de uniformidade da spline.
Tarefas:
1. Se a claim de uniformidade for mantida, adicionar teste dedicado.

### DOC-01 - Preencher `README.md`
Tipo: Docs
Prioridade: Alta
Evidencia: `README.md` esta praticamente vazio.
Tarefas:
1. Descrever produto, modulos, variantes, build, testes e limitacoes.
2. Explicar diferenca entre `playstore` e `nonplay`.

### DOC-02 - Criar `AUTOMATION.md`
Tipo: Docs
Prioridade: Alta
Evidencia: `AutomationReceiver.kt` referencia `AUTOMATION.md`, mas o arquivo nao existe.
Tarefas:
1. Documentar actions, extras, permissoes e exemplos reais.
2. Explicitar o modelo de seguranca.

### DOC-03 - Sincronizar comentarios e KDoc com o runtime real
Tipo: Docs
Prioridade: Alta
Evidencia: ha comentarios stale em `AutomationReceiver.kt`, `RouteEditorViewModel.kt`, `FloatingBubbleService.kt`, `GhostPinWidget.kt` e outros.
Tarefas:
1. Revisar comentarios que prometem comportamento nao entregue.
2. Corrigir KDoc para refletir o fluxo real.
3. Remover historico de sprint que confunda mais do que ajuda.
Criterios de aceite:
1. O codigo comenta o que realmente faz hoje.

# Inventario Mestre de Itens

## Runtime e orquestracao
- `RT-01` GPX preloaded route apagada antes do consumo.
- `RT-02` Joystick overlay sem bridge para o repositorio.
- `RT-03` Tempo de simulacao contado por frame.
- `RT-04` Widget atualizado por frame.
- `RT-05` Loop de simulacao no dispatcher errado.
- `RT-06` Corrida no fechamento de historico e stop.
- `RT-07` Sessao infinita possivel com `speedRatio = 0`.

## Persistencia e modelo de sessao
- `DATA-01` Modelo de sessao incompleto.
- `DATA-02` Favoritos nao autossuficientes.
- `DATA-03` Replay de historico nao fiel.
- `DATA-04` `routeId` nao carregado de verdade.
- `DATA-05` Rotas runtime efemeras em favoritos/historico.
- `DATA-06` Schedule estreito demais.
- `DATA-07` `reset()` nao limpa tudo que deveria ou mistura concerns.
- `DATA-08` Ultimo plano nao sobrevive a morte de processo.
- `DATA-09` Persistencia por nome humano de perfil.
- `DATA-10` Falta de FKs e indices.
- `DATA-11` Falta de schema exportado completo e testes de migracao.
- `DATA-12` Blobs JSON de rota sem estrategia formal de evolucao.

## UI e fluxo do usuario
- `UI-01` `fitCamera()` e update de rota por frame.
- `UI-02` Fluxo de permissao/onboarding duplicado.
- `UI-03` `initializeLocation()` sobrescrevendo contexto do usuario.
- `UI-04` Coleta de estado excessiva na raiz.
- `UI-05` CTA de Start sem prontidao por modo.
- `UI-06` Route editor nao inicia simulacao de fato.
- `UI-07` Import/export incompletos na UI.
- `UI-08` Perfis customizados parcialmente implementados.
- `UI-09` Estados mortos ou incompletos (`lowMemorySignal`, `previewPlayhead`, `isManualMode`, `SHIZUKU_ENABLED`, `ProfileManagerViewModel`).
- `UI-10` Superficies de quick-start com UX divergente.

## Engine e dominio
- `ENG-01` App nao usa `RouteInterpolator`.
- `ENG-02` App nao usa `SpeedController` e politicas de velocidade divergem.
- `ENG-03` `TrajectoryValidator` injetado e nao usado.
- `ENG-04` `LayeredNoiseModel` nao integrado ao runtime.
- `ENG-05` `realism-lab` sem papel no app.
- `ENG-06` Altitude e `SegmentOverrides` nao chegam ao runtime.
- `ENG-07` Inconsistencia de distancia geoespacial.
- `ENG-08` Inconsistencia semantica de `pauseDurationSec`.
- `ENG-09` Mismatch entre doc e implementacao da spline.
- `ENG-10` Parametrizacao de tunel precisa revisao.
- `ENG-11` `SegmentOverrides.loop` sem semantica operacional.
- `ENG-12` `Route.durationSeconds` e footgun.
- `ENG-13` Possivel penalidade duplicada de acuracia no pipeline de ruido.

## Integracoes externas
- `INT-01` Dois parsers de GPX/arquivo com contratos diferentes.
- `INT-02` OSRM demo sem resiliencia suficiente.
- `INT-03` Nominatim sem rate limit real nem boa UX de erro.
- `INT-04` Mapa remoto sem fallback observado.
- `INT-05` Contrato de automacao inconsistente com seguranca real.
- `INT-06` Inputs externos e URIs precisam validacao melhor.
- `INT-07` Exact alarms precisam validacao de compliance.
- `INT-08` Compatibilidade de `ProviderProperties` precisa validacao.

## Seguranca, distribuicao e build
- `SEC-01` `ScheduleReceiver` exportado sem protecao.
- `SEC-02` Flavors sem separacao real.
- `SEC-03` `playstore` precisa manifest e comportamento proprios.
- `SEC-04` Logs sensiveis sanitizados de forma inconsistente.
- `SEC-05` Matriz de exposicao de componentes/intents precisa endurecimento.
- `BLD-01` Testes do app nao bloqueiam CI.
- `BLD-02` `playstore` nao compila/testa em CI.
- `BLD-03` `realism-lab` fora da CI.
- `BLD-04` lint nao bloqueia nada.
- `BLD-05` feature flag `SHIZUKU_ENABLED` morta.

## Testes e documentacao
- `TST-01` Falta suite de service tests.
- `TST-02` Falta suite de repositorio/plano.
- `TST-03` Falta suite de schedule.
- `TST-04` Falta suite de migracao/persistencia.
- `TST-05` Falta suite por flavor/manifest.
- `TST-06` Testes do `:app` validam copias locais da logica.
- `TST-07` Falta teste do pipeline completo de ruido.
- `TST-08` Falta teste de turn rate no validator.
- `TST-09` Falta teste de uniformidade da spline.
- `DOC-01` README vazio.
- `DOC-02` `AUTOMATION.md` ausente.
- `DOC-03` KDoc/comentarios stale.

# Matriz de Rastreabilidade dos Achados

## Achados de runtime principal
- Bug GPX -> `RT-01`.
- Bug joystick -> `RT-02`.
- `elapsedTimeSec` errado -> `RT-03`.
- Widget por frame -> `RT-04`.
- Loop no dispatcher inadequado -> `RT-05`.
- Corrida em stop/history -> `RT-06`.
- `speedRatio = 0` -> `RT-07`.

## Achados de UI/estado
- Refit do mapa por frame -> `UI-01`.
- Permissoes duplicadas entre activity e onboarding -> `UI-02`.
- `initializeLocation()` sobrescrevendo estado -> `UI-03`.
- Coleta excessiva de flows -> `UI-04`.
- Start sem prontidao por modo -> `UI-05`.
- Route editor sem callback real -> `UI-06`.
- Export/import incompletos -> `UI-07`.
- Perfis customizados orfaos -> `UI-08`.
- `lowMemorySignal`, `previewPlayhead`, `isManualMode`, `SHIZUKU_ENABLED`, `ProfileManagerViewModel` -> `UI-09` e `BLD-05`.
- UX inconsistente entre quick-start surfaces -> `UI-10`.

## Achados de persistencia e repositorio
- `SimulationConfig` insuficiente -> `DATA-01`.
- Favoritos incompletos -> `DATA-02`.
- Replay nao fiel -> `DATA-03`.
- `routeId` decorativo -> `DATA-04`.
- Rotas efemeras persistidas indevidamente -> `DATA-05`.
- Schedule estreito -> `DATA-06`.
- `reset()` deixando estado stale -> `DATA-07`.
- Ultimo plano so em memoria -> `DATA-08`.
- `profileIdOrName` ambiguo -> `DATA-09`.
- Sem FKs/indices -> `DATA-10`.
- `3.json` ausente e migracoes sem cobertura -> `DATA-11`.
- Blobs JSON sem estrategia -> `DATA-12`.

## Achados de engine e dominio
- Runtime nao usa `RouteInterpolator` -> `ENG-01`.
- Runtime nao usa `SpeedController` -> `ENG-02`.
- `TrajectoryValidator` nao usado -> `ENG-03`.
- `LayeredNoiseModel` nao usado -> `ENG-04`.
- `realism-lab` sem papel -> `ENG-05`.
- Altitude e overrides ignorados -> `ENG-06`.
- Distancia inconsistente -> `ENG-07`.
- `pauseDurationSec` inconsistente -> `ENG-08`.
- Doc da spline inconsistente -> `ENG-09`.
- Tunel/lognormal -> `ENG-10`.
- `loop` sem uso -> `ENG-11`.
- `durationSeconds` footgun -> `ENG-12`.
- Penalidade duplicada -> `ENG-13`.

## Achados de integracao externa
- Dois parsers -> `INT-01`.
- OSRM fragil para producao -> `INT-02`.
- Nominatim sem limitacao adequada -> `INT-03`.
- Estilo remoto sem fallback -> `INT-04`.
- Automacao/documentacao divergentes -> `INT-05` e `DOC-02`.
- Validacao fraca de inputs externos -> `INT-06`.
- Exact alarm precisa validacao -> `INT-07`.
- `ProviderProperties` precisa validacao -> `INT-08`.

## Achados de seguranca e release
- `ScheduleReceiver` exportado -> `SEC-01`.
- Flavors nao separados -> `SEC-02`.
- `playstore` herda capacidades sensiveis -> `SEC-03`.
- Logs inconsistentes -> `SEC-04`.
- Superficies exportadas/intents -> `SEC-05`.

## Achados de build e CI
- Testes do app nao bloqueiam -> `BLD-01`.
- `playstore` fora da CI -> `BLD-02`.
- `realism-lab` fora da CI -> `BLD-03`.
- lint advisory -> `BLD-04`.
- `SHIZUKU_ENABLED` morta -> `BLD-05`.

## Achados de testes e docs
- Falta de service tests -> `TST-01`.
- Falta de testes de repositorio/plano -> `TST-02`.
- Falta de testes de schedule -> `TST-03`.
- Falta de migracoes/persistencia -> `TST-04`.
- Falta de testes por flavor -> `TST-05`.
- Testes com reimplementacao local -> `TST-06`.
- Falta de teste de `LayeredNoiseModel` -> `TST-07`.
- Falta de teste de turn rate -> `TST-08`.
- Falta de teste de uniformidade -> `TST-09`.
- README vazio -> `DOC-01`.
- `AUTOMATION.md` ausente -> `DOC-02`.
- Comentarios stale -> `DOC-03`.

# Resultado Esperado Apos as 7 Sprints
1. O fluxo principal classico, GPX, joystick, favoritos, replay, widget, tile e schedule passa a operar sobre o mesmo modelo de sessao.
2. O `SimulationService` deixa de ser um arquivo monolitico com bugs de tempo, corrida e estado.
3. `:app` passa a usar de verdade o que `:engine` e `:realism-lab` oferecem, ou pelo menos deixa claro o que ainda esta fora do runtime.
4. `playstore` e `nonplay` deixam de ser apenas flags de `BuildConfig` e passam a ser produtos tecnicamente diferentes.
5. A CI deixa de ser apenas informativa e passa a proteger o repositorio.
