# Validação Técnica de Features — GhostPin

Este relatório substitui a auditoria anterior com foco em **validação funcional de features**, integração e completude de implementação.

---

## 1) Mapeamento de features (inventário)

| ID | Feature | Tipo | Evidência principal | Status |
|---|---|---|---|---|
| F-001 | Simulação GPS (start/stop/pause) | Explícita | `MainActivity.startSimulation`, `SimulationService.onStartCommand` | ✅ Completa (com ressalvas) |
| F-002 | Modos de operação (CLASSIC, GPX, JOYSTICK, WAYPOINTS) | Explícita | `AppMode`, `SimulationViewModel.selectedMode`, `SimulationService.startSimulation` | ⚠️ Parcial |
| F-003 | Importação GPX para simulação | Explícita | `SimulationViewModel.loadGpxFromUri`, `GpxParser.parse` | ⚠️ Parcial |
| F-004 | Roteamento OSRM + fallback linha reta | Explícita | `OsrmRouteProvider.fetchRoute/fetchMultiRoute/fallback*` | ✅ Completa |
| F-005 | Overlay flutuante + joystick | Explícita | `FloatingBubbleService`, `JoystickView`, integração no Service | ⚠️ Parcial |
| F-006 | Automação por Broadcast (ADB/Tasker) | Explícita | `AutomationReceiver`, `AUTOMATION.md`, Manifest receiver | ❌ Quebrada |
| F-007 | Quick Settings Tile (toggle simulação) | Explícita | `GhostPinQsTile` | ⚠️ Parcial |
| F-008 | Widget de home screen (status + start/stop) | Explícita | `GhostPinWidget` | 🔲 Stub/Placeholder |
| F-009 | Onboarding (permissões + setup inicial) | Explícita | `OnboardingScreen/ViewModel/DataStore`, fluxo em `MainActivity` | ✅ Completa |
| F-010 | Gerenciamento de perfis (seed/CRUD/versionamento) | Explícita | `ProfileManager`, `ProfileDao`, testes semver | ✅ Completa |
| F-011 | Editor de rotas (criar/importar/exportar/salvar) | Explícita | `RouteEditorScreen/ViewModel/RouteRepository` | 💀 Código morto (na app principal) |
| F-012 | Métricas de realismo (realism-lab) | Implícita (módulo técnico) | `realism-lab/*` + ausência de uso no app | 💀 Código morto (produto) |
| F-013 | Sanitização de logs sensíveis | Implícita | `LogSanitizer`, uso em serviços/repositórios | ✅ Completa |
| F-014 | Persistência de rota/perfil via Room | Explícita | `RouteRepository/ProfileManager`, DAOs, DB | ✅ Completa |
| F-015 | `SET_ROUTE` / `SET_PROFILE` no serviço | Planejada porém incompleta | constantes e docs existem, tratamento real ausente | 🔲 Stub/Placeholder |

---

## 2) Status por feature com justificativa

## F-001 — Simulação GPS (start/stop/pause) → ✅ Completa (com ressalvas)
- **Entrada**: FAB/UX chama `MainActivity.startSimulation`.  
- **Fluxo**: Activity -> Intent -> `SimulationService.onStartCommand` -> loop de injeção -> `SimulationRepository.emitState`.  
- **Saída**: mock location injetada + estado `Running/Paused/Idle`.
- **Evidências**: `MainActivity` envia extras de início; `SimulationService` trata `ACTION_STOP` e `ACTION_PAUSE`; loop principal em `startSimulation`.  
- **Ressalva**: há inconsistências nas ações de automação (não no caminho principal da UI).

## F-002 — Modos (CLASSIC/GPX/JOYSTICK/WAYPOINTS) → ⚠️ Parcial
- **CLASSIC/GPX/JOYSTICK** têm fluxo funcional.
- **WAYPOINTS** depende de `waypoints.size >= 2`; se menor, cai silenciosamente no fluxo `else` (clássico), sem erro/feedback claro.
- **Risco funcional**: usuário em modo waypoints pode iniciar esperando multi-stop e obter rota clássica sem aviso.

## F-003 — Importação GPX para simulação → ⚠️ Parcial
- **Entrada**: file picker em `MainActivity`.
- **Fluxo**: `SimulationViewModel.loadGpxFromUri` -> `GpxParser.parse` -> `repository.emitRoute` -> `SimulationService` usa rota preload em modo GPX.
- **Quebra parcial**:
  - parser GPX dedicado não valida faixa de coordenadas (aceita lat/lng inválidas);
  - existem dois parsers GPX no projeto (`GpxParser` e `RouteFileParser`) com regras diferentes.

## F-004 — OSRM + fallback → ✅ Completa
- `OsrmRouteProvider` trata perfil, timeout, status HTTP, parse e fallback quando erro.
- Para drone, já pula OSRM e usa fallback direto.

## F-005 — Overlay + Joystick → ⚠️ Parcial
- **Funciona**: abertura de bubble/joystick e leitura de joystick para deslocamento.
- **Fragilidade**: cálculo de longitude divide por `cos(lat)`; próximo de ±90°, pode gerar instabilidade numérica.

## F-006 — Automação Broadcast → ❌ Quebrada
- **Documentação** e receiver indicam suporte a `ACTION_SET_ROUTE` e `ACTION_SET_PROFILE`.
- **Problema real**:
  - nomes de action divergem entre `AutomationReceiver` e `SimulationService` (`ACTION_*` vs `action.*`);
  - `SimulationService.onStartCommand` não possui branch de execução para setar rota/perfil;
  - `EXTRA_SPEED_RATIO` é encaminhado mas nunca aplicado na simulação.
- Resultado: contrato de automação está inconsistente com implementação.

## F-007 — Quick Settings Tile → ⚠️ Parcial
- O tile lê `lastUsedConfig` para start rápido.
- `SimulationRepository.emitConfig` existe, mas não há chamada efetiva no fluxo de simulação para persistir esse `lastUsedConfig`.
- Efeito: tile pode ficar sem conseguir iniciar simulação (retorna cedo quando config é null).

## F-008 — Widget Home Screen → 🔲 Stub/Placeholder
- `GhostPinWidget.updateAll` diz “Called from SimulationService when state changes”, porém não há chamada no serviço.
- Assim, widget inicializa, mas não acompanha estado real em runtime (status tende a ficar estático até updates sistêmicos).

## F-009 — Onboarding → ✅ Completa
- Fluxo de 3 etapas com persistência em DataStore.
- `MainActivity` só entra na tela principal quando onboarding estiver completo.
- Valida coordenadas básicas e estado de permissões.

## F-010 — Perfis (seed/CRUD/semver) → ✅ Completa
- `ProfileManager` implementa seed idempotente, create/update/clone/delete, proteção de built-ins e bump de semver.
- Há teste específico de semver.

## F-011 — Route Editor → 💀 Código morto (na app principal)
- O editor existe (`RouteEditorScreen` e `RouteEditorViewModel`), mas não há navegação/invocação a partir da `MainActivity`.
- Na prática, feature implementada mas inacessível para usuário no app principal.

## F-012 — Realism Lab → 💀 Código morto (produto)
- Módulo com métricas e testes, porém sem integração com `:app` em fluxo de runtime.

## F-013 — Sanitização de logs → ✅ Completa
- `LogSanitizer` existe e é aplicado em logs de automação/rotas para reduzir exposição de dados sensíveis.

## F-014 — Persistência Room de rotas/perfis → ✅ Completa
- Repositórios e DAOs implementam leitura/escrita, serialização e desserialização com guardas básicos.

## F-015 — `SET_ROUTE` / `SET_PROFILE` no Service → 🔲 Stub/Placeholder
- As constantes existem e docs anunciam suporte, mas fluxo real não implementa carregamento/troca de perfil por action dedicada.

---

## 3) Fluxo técnico por feature (entrada -> camadas -> saída)

### F-001 Simulação principal
- **Entrada**: FAB -> `MainActivity.startSimulation`.
- **Camadas**: UI -> Intent -> `SimulationService` -> `OsrmRouteProvider`/fallback -> `MockLocationInjector` -> `SimulationRepository`.
- **Saída**: injeção de posição mock + estado de simulação.
- **Dependências**: permissões Android, mock provider habilitado, internet (para OSRM).

### F-003 GPX
- **Entrada**: OpenDocument picker.
- **Camadas**: `MainActivity` -> `SimulationViewModel.loadGpxFromUri` -> `GpxParser` -> `SimulationRepository.route` -> `SimulationService` em `AppMode.GPX`.
- **Saída**: rota preload usada sem OSRM.
- **Ponto frágil**: parser sem validação de range.

### F-006 Automação
- **Entrada**: Broadcast externo.
- **Camadas**: `AutomationReceiver` -> Intent para `SimulationService`.
- **Saída esperada**: start/stop/pause/set route/set profile.
- **Quebra**: mismatch de actions e ausência de handlers reais para set route/profile.

### F-007 Tile
- **Entrada**: click no QS Tile.
- **Camadas**: `GhostPinQsTile.onClick` -> `SimulationRepository.lastUsedConfig` -> `SimulationService`.
- **Saída esperada**: start/stop rápido.
- **Quebra**: `lastUsedConfig` não é alimentado pelo fluxo principal.

### F-008 Widget
- **Entrada**: botão do widget.
- **Camadas**: `PendingIntent` -> `SimulationService`.
- **Saída esperada**: start/stop + status sincronizado no widget.
- **Quebra**: rotina de atualização contínua (`updateAll`) não é acionada pelo Service.

---

## 4) Integração entre features (dependências e colisões)

1. **Automação vs Service contract** (F-006 + F-015)
- Receiver/docs publicam ações que o Service não implementa adequadamente -> quebra de integração.

2. **Tile/Widget vs estado compartilhado** (F-007 + F-008)
- Ambos dependem de estado consistente do `SimulationRepository` e callbacks de atualização.
- Sem persistência de config e sem atualização de widget no service, os atalhos ficam inconsistentes.

3. **GPX parser duplicado** (F-003 + F-011)
- `GpxParser` (simulação) e `RouteFileParser` (editor) possuem comportamentos distintos.
- Mesmo arquivo pode funcionar em uma feature e falhar/comportar diferente em outra.

---

## 5) Validações e casos extremos

- **Entradas inválidas**:
  - Simulação valida lat/lng de intent no service (ok).
  - Automação faz clamp de lat/lng/frequência (ok), mas contrato não fecha no destino.
  - GPX para simulação não valida bounds (gap).

- **Estados nulos/vazios**:
  - GPX mode em service aguarda rota preload e retorna erro se não houver (ok).
  - Tile sem config apenas retorna, sem fallback UX (parcial).

- **Rede/timeout/falha externa**:
  - OSRM tem timeout e fallback para linha reta (bom).

- **Fluxo de erro**:
  - Há emissão de estado `Error` em vários pontos; porém algumas quebras de contrato (automação, tile) falham de forma silenciosa para usuário final.

- **Diferença entre ambientes**:
  - Build flags `MOCK_PROVIDER_ENABLED` por flavor (`nonplay` vs `playstore`) alteram comportamento de simulação.

---

## 6) Features com maior risco de regressão

1. **F-006 Automação** (sem testes dedicados de integração, contrato quebrado entre receiver/docs/service).
2. **F-007 Tile** (depende de estado não persistido no fluxo principal; não há teste de ponta a ponta).
3. **F-008 Widget** (feature parcialmente “declarada” sem wiring com service).
4. **F-002 Modos** (branches múltiplos e acoplamento alto em `SimulationService`; mudanças em um modo podem impactar outros).
5. **F-003 GPX** (duplicidade de parser aumenta chance de regressão por divergência).

Cobertura de testes observada no repositório: foco em engine/rotas/perfis, sem cobertura robusta de integração Android (service/receiver/tile/widget).

---

## 7) Checklist objetivo por feature

Legenda: **Sim / Não / Parcial**

### F-001 Simulação
- Fluxo principal funciona: **Sim**
- Fluxo de erro implementado: **Parcial**
- Entrada validada: **Sim**
- Saída no formato esperado: **Sim**
- Efeitos colaterais implementados: **Sim** (injeção mock)
- Feature acessível: **Sim**
- Teste cobrindo comportamento: **Parcial** (sem E2E Android)

### F-002 Modos
- Fluxo principal funciona: **Parcial**
- Fluxo de erro implementado: **Parcial**
- Entrada validada: **Parcial**
- Saída esperada: **Parcial**
- Efeitos colaterais: **Sim**
- Acessível: **Sim**
- Testes: **Não** (sem integração de modos no app)

### F-003 GPX
- Fluxo principal funciona: **Parcial**
- Fluxo de erro: **Sim**
- Entrada validada: **Parcial**
- Saída esperada: **Parcial**
- Efeitos colaterais: **Sim**
- Acessível: **Sim**
- Testes: **Não** (parser usado pela simulação sem testes próprios aqui)

### F-006 Automação
- Fluxo principal funciona: **Não**
- Fluxo de erro: **Parcial**
- Entrada validada: **Parcial**
- Saída esperada: **Não**
- Efeitos colaterais: **Parcial**
- Acessível: **Sim** (receiver exportada)
- Testes: **Não**

### F-007 Tile
- Fluxo principal funciona: **Parcial**
- Fluxo de erro: **Não** (retorno silencioso sem config)
- Entrada validada: **Parcial**
- Saída esperada: **Parcial**
- Efeitos colaterais: **Parcial**
- Acessível: **Sim**
- Testes: **Não**

### F-008 Widget
- Fluxo principal funciona: **Parcial**
- Fluxo de erro: **Não**
- Entrada validada: **Parcial**
- Saída esperada: **Não** (status não sincroniza)
- Efeitos colaterais: **Parcial**
- Acessível: **Sim**
- Testes: **Não**

### F-011 Route Editor
- Fluxo principal funciona: **Parcial** (internamente)
- Fluxo de erro: **Parcial**
- Entrada validada: **Parcial**
- Saída esperada: **Sim**
- Efeitos colaterais: **Sim**
- Acessível: **Não** (não integrado à navegação principal)
- Testes: **Parcial** (parser/exporter com testes, UI/editor sem integração)

---

## 8) Problemas por feature (com impacto e ação)

### P-001 (F-006/F-015) — Contrato de ações quebrado entre Receiver e Service — 🔴 Crítico
- **Trecho/prova**: actions em `AutomationReceiver` usam `com.ghostpin.ACTION_*`, enquanto `SimulationService` define `com.ghostpin.action.*` e não implementa handler dedicado para `SET_ROUTE/SET_PROFILE`.
- **Impacto usuário**: automações externas falham ou executam fluxo inesperado.
- **Correção**:
  1) centralizar actions/extras em objeto único (`AutomationContract`);
  2) implementar `when(intent.action)` completo no service;
  3) validar com testes instrumentados de broadcast.

### P-002 (F-006) — `EXTRA_SPEED_RATIO` ignorado no Service — 🔴 Crítico
- **Trecho/prova**: receiver envia extra; service não consome para ajustar `speedMs`.
- **Impacto usuário**: automação não controla velocidade como documentado.
- **Correção**: aplicar multiplicador no cálculo de deslocamento por frame.

### P-003 (F-007) — Quick tile depende de config nunca alimentado — 🟠 Alto
- **Trecho/prova**: `lastUsedConfig` é pré-requisito no tile, porém `emitConfig` não é chamado no fluxo principal.
- **Impacto usuário**: tile não inicia simulação em cenários comuns.
- **Correção**: persistir `SimulationConfig` no start real e em atualizações relevantes.

### P-004 (F-008) — Widget sem atualização de estado em runtime — 🟠 Alto
- **Trecho/prova**: `GhostPinWidget.updateAll` existe com comentário de chamada pelo service, mas sem wiring.
- **Impacto usuário**: widget mostra estado desatualizado.
- **Correção**: chamar `GhostPinWidget.updateAll(...)` nos pontos de transição do `SimulationService`.

### P-005 (F-003) — GPX parser da simulação sem validação de bounds — 🟡 Médio
- **Impacto**: aceitação de coordenadas inválidas pode gerar comportamentos erráticos.
- **Correção**: validar lat/lng no parser ou unificar parser com `RouteFileParser`.

### P-006 (F-002/F-005) — Instabilidade numérica no joystick em altas latitudes — 🟡 Médio
- **Impacto**: salto abrupto de longitude, potencial teleporte.
- **Correção**: proteger denominador com epsilon em `cos(lat)`.

### P-007 (F-011) — Route Editor não acessível no app principal — 🟡 Médio
- **Impacto**: feature pronta mas indisponível para usuário final.
- **Correção**: adicionar navegação/entrypoint na UI principal.

---

## 9) Evidências de código (referências diretas)

- Ações e extras de automação: `app/src/main/kotlin/com/ghostpin/app/automation/AutomationReceiver.kt`.
- Ações efetivas tratadas no serviço e ausência de handlers dedicados para SET_ROUTE/SET_PROFILE: `app/src/main/kotlin/com/ghostpin/app/service/SimulationService.kt`.
- Permissão/receiver exportada da automação: `app/src/main/AndroidManifest.xml`.
- Tile dependente de `lastUsedConfig`: `app/src/main/kotlin/com/ghostpin/app/service/GhostPinQsTile.kt`.
- `emitConfig` não conectado no fluxo principal: `app/src/main/kotlin/com/ghostpin/app/data/SimulationRepository.kt`.
- Widget com `updateAll` sem integração no service: `app/src/main/kotlin/com/ghostpin/app/widget/GhostPinWidget.kt`.
- GPX parser de simulação sem validação de bounds: `app/src/main/kotlin/com/ghostpin/app/routing/GpxParser.kt`.
- Fluxo de modo e waypoints na ViewModel: `app/src/main/kotlin/com/ghostpin/app/ui/SimulationViewModel.kt`.
- Route editor implementado: `app/src/main/kotlin/com/ghostpin/app/ui/RouteEditorScreen.kt` e `RouteEditorViewModel.kt`.

---

## 10) Relatório final de validação

### Percentual estimado
- **✅ Completas:** 40% (6/15)
- **⚠️ Parciais:** 27% (4/15)
- **❌ Quebradas:** 7% (1/15)
- **🔲 Stub/Placeholder:** 13% (2/15)
- **💀 Código morto:** 13% (2/15)

### Top 3 features críticas para correção imediata
1. **F-006 Automação Broadcast** (contrato quebrado e implementação incompleta).
2. **F-007 Quick Settings Tile** (start rápido dependente de estado não persistido).
3. **F-008 Widget** (status sem sincronização runtime com service).

### Pré-requisitos bloqueantes para concluir features parciais
- Definir **contrato único de actions/extras** compartilhado entre docs/receiver/service.
- Adicionar **testes instrumentados** para broadcast -> service -> state transitions.
- Persistir `SimulationConfig` no start para habilitar tile/widget com previsibilidade.
- Unificar parser GPX e validação de coordenadas para consistência entre fluxos.

### Recomendação geral
- **Pronto para testes internos?** **Sim, com foco em testes de integração Android.**
- **Pronto para staging?** **Parcial** (após corrigir automação + tile/widget).
- **Pronto para produção?** **Não** no estado atual, devido a quebra de contrato funcional em feature pública de automação e inconsistências operacionais de atalhos (tile/widget).
