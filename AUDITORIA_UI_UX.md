# Auditoria UI/UX — GhostPin (Jetpack Compose)

## Escopo e método
- Escopo: `OnboardingScreen`, `MainActivity` (4 modos), `MapController`, `RouteEditorScreen`, `GhostPinQsTile`, tema/tokens.
- Método: leitura estática de código Compose + validação de contraste WCAG (pares críticos).

---

## 1) ONBOARDING

### TELA/COMPONENTE: Onboarding (HorizontalPager + StepIndicator)
**Estado atual**: fluxo em 3 passos com indicador de progresso por dots e navegação Skip/Next/Start.  
**Problema**: o botão **Skip** finaliza onboarding mesmo com setup incompleto e aplica fallback silencioso para coordenadas padrão de São Paulo quando inválidas/nulas.  
**Severidade**: 🟠  
**Impacto**: usuários iniciantes podem iniciar simulação sem entender permissões/mock location e sem perceber que coordenadas foram substituídas.  
**Recomendação**: bloquear Skip sem confirmação contextual (Dialog) e exibir resumo do que ficará pendente.

```kotlin
if (!state.isMockLocationConfigured || !state.hasLocationPermission) {
  AlertDialog(
    onDismissRequest = { show = false },
    confirmButton = { TextButton({ proceedSkip() }) { Text("Continuar mesmo assim") } },
    dismissButton = { TextButton({ show = false }) { Text("Voltar") } },
    text = { Text("Você ainda não concluiu permissões/mock location.") }
  )
}
```

### TELA/COMPONENTE: Simulation Setup (Onboarding)
**Estado atual**: validação de coordenadas retorna `null`, mas sem mensagem inline no formulário.  
**Problema**: erro de input não é acionável (usuário só percebe que Start não avança).  
**Severidade**: 🟠  
**Impacto**: aumenta abandono e tentativa/erro em usuários não técnicos.  
**Recomendação**: usar estado de erro por campo (`isError`) + `supportingText` com faixa válida.

---

## 2) NAVEGAÇÃO E ARQUITETURA DE INFORMAÇÃO

### TELA/COMPONENTE: NavigationBar (Classic/Joystick/Waypoints/GPX)
**Estado atual**: 4 modos com ícones adequados semanticamente.  
**Problema**: labels em 10sp + ícones sem `contentDescription` reduzem legibilidade/acessibilidade.  
**Severidade**: 🟠  
**Impacto**: usuários com baixa visão/TalkBack perdem contexto do modo ativo.  
**Recomendação**: aumentar label para 12sp mínimo, e definir descrição semântica por modo.

```kotlin
Icon(Icons.Default.Map, contentDescription = "Modo Waypoints")
label = { Text(mode.displayName, fontSize = 12.sp) }
```

### TELA/COMPONENTE: troca de modos
**Estado atual**: estado do mapa depende de flows do `ViewModel` e callbacks.  
**Problema**: não há affordance visual explícita de “modo atual” além da seleção da bottom bar.  
**Severidade**: 🟡  
**Impacto**: confusão em sessões longas (especialmente quando o painel inferior muda radicalmente).  
**Recomendação**: inserir “Mode badge” persistente no topo do mapa (chip com ícone+texto).

---

## 3) MAPA E INTERAÇÃO ESPACIAL

### TELA/COMPONENTE: Pins e rota (MapController)
**Estado atual**: start verde, end vermelho, waypoint âmbar; rota teal; dot com anel de accuracy.  
**Problema**: diferenciação depende muito de cor (daltônicos podem confundir start/end sem forma distinta).  
**Severidade**: 🟠  
**Impacto**: erros de interpretação de direção e destino.  
**Recomendação**: usar formas/símbolos distintos (ex.: triângulo para start, quadrado para end) + legenda opcional.

### TELA/COMPONENTE: fitCamera
**Estado atual**: padding fixo de 150px para bounds.  
**Problema**: em telas pequenas, 150px pode comprimir demais viewport útil.  
**Severidade**: 🟡  
**Impacto**: percepção ruim de contexto da rota; zoom “apertado”.  
**Recomendação**: padding responsivo por densidade/altura.

### TELA/COMPONENTE: long-press no mapa
**Estado atual**: adiciona waypoint sem feedback háptico/visual explícito imediato.  
**Problema**: pouca confirmação de ação em interação crítica.  
**Severidade**: 🟡  
**Impacto**: usuário repete gesto e cria pontos duplicados.  
**Recomendação**: `HapticFeedbackType.LongPress` + Snackbar curto “Waypoint adicionado”.

---

## 4) ROUTE EDITOR (BottomSheet)

### TELA/COMPONENTE: ModalBottomSheet de overrides
**Estado atual**: dismiss por toque fora/gesture padrão; campos speed/pause e toggle loop.  
**Problema**: risco de dismiss acidental sem confirmação quando usuário já digitou mudanças.  
**Severidade**: 🟠  
**Impacto**: perda de edição e frustração.  
**Recomendação**: “dirty state guard” com confirmação ao fechar quando houve alterações.

### TELA/COMPONENTE: semântica de unidades
**Estado atual**: velocidade em m/s e pausa em segundos estão corretas tecnicamente.  
**Problema**: m/s não é unidade amigável para usuários comuns.  
**Severidade**: 🟡  
**Impacto**: configurações erradas de velocidade.  
**Recomendação**: mostrar conversão inline (`m/s` + `km/h`) no `supportingText`.

### TELA/COMPONENTE: botões Clear Override / Apply
**Estado atual**: hierarquia visual razoável (outlined vs filled).  
**Problema**: ausência de feedback após Apply (toast/snackbar).  
**Severidade**: 🟡  
**Impacto**: incerteza se alteração persistiu.  
**Recomendação**: snackbar “Override aplicado ao segmento X”.

---

## 5) CORES E CONTRASTE (WCAG 2.1 AA)

### Resultado de contraste (amostras críticas)
- `TextSecondary #888888` sobre `Surface #1E1E2E` = **4.63:1** ✅ (AA para texto normal)
- `TextDisabled #555555` sobre `CardBackground #252535` = **2.02:1** ❌
- `Primary #80CBC4` sobre `Background #121212` = **10.04:1** ✅
- `Accent #4CAF50` sobre `Background #121212` = **6.74:1** ✅

### TELA/COMPONENTE: estados desabilitados
**Problema**: cor de texto desabilitado está abaixo do mínimo de legibilidade para conteúdo textual.  
**Severidade**: 🔴  
**Impacto**: usuários com baixa visão não conseguem ler placeholders/hints desabilitados.  
**Recomendação**: subir `TextDisabled` para tonalidade com contraste >= 3.0:1 (ideal >=4.5:1 para texto pequeno).

---

## 6) TIPOGRAFIA E HIERARQUIA

### TELA/COMPONENTE: escala tipográfica geral
**Estado atual**: títulos 24sp, body 14–16sp, captions 11–13sp.  
**Problema**: labels da NavigationBar em 10sp (muito pequeno).  
**Severidade**: 🟠  
**Impacto**: baixa legibilidade em telas compactas e com escalonamento do sistema.  
**Recomendação**: mínimo 12sp nos labels da barra de navegação.

---

## 7) FEEDBACK E ERRO

### TELA/COMPONENTE: validação de coordenadas
**Estado atual**: valida no ViewModel, sem mensagem visual no formulário.  
**Problema**: erro silencioso/implícito.  
**Severidade**: 🟠  
**Impacto**: difícil descobrir por que não inicia.  
**Recomendação**: `isError` + texto de apoio contextual por campo.

### TELA/COMPONENTE: snackbars de permissão
**Estado atual**: snackbar com `actionLabel = "Dismiss"` apenas.  
**Problema**: falta ação direta de retry/abrir Settings.  
**Severidade**: 🟡  
**Impacto**: mais passos para recuperar erro de permissão.  
**Recomendação**: ação “Abrir Configurações” e deep-link para tela apropriada.

### TELA/COMPONENTE: estado de simulação
**Estado atual**: card dedicado (Idle/Fetching/Running/Paused/Error) com ícone e texto.  
**Problema**: bom estado geral; melhoria seria reforço de contraste em subtexto de progresso.  
**Severidade**: 🟡  
**Impacto**: leitura parcial em ambientes externos.  
**Recomendação**: elevar contraste do subtítulo de `#888888` para tom ligeiramente mais claro.

---

## 8) QUICK SETTINGS TILE

### TELA/COMPONENTE: GhostPinQsTile
**Estado atual**: exibe estado ativo/inativo e subtítulo com profile/Paused em Q+.  
**Problema**: se `lastUsedConfig` for nulo, click não dá feedback ao usuário.  
**Severidade**: 🟠  
**Impacto**: percepção de “tile quebrado”.  
**Recomendação**: abrir app na tela principal com snackbar explicativo quando não houver config prévia.

---

## 9) ACESSIBILIDADE

### TELA/COMPONENTE: ícones e botões
**Estado atual**: várias `Icon(..., contentDescription = null)` em navegação/ações críticas.  
**Problema**: baixa navegabilidade com TalkBack.  
**Severidade**: 🔴  
**Impacto**: usuários leitores de tela perdem ações essenciais.  
**Recomendação**: preencher `contentDescription` para todos ícones acionáveis; manter `null` apenas em ícones puramente decorativos.

### TELA/COMPONENTE: áreas de toque
**Estado atual**: muitos controles usam `IconButton` (ok 48dp).  
**Problema**: alguns elementos customizados (bubble canvas) dependem de gesto manual sem semântica Compose.  
**Severidade**: 🟠  
**Impacto**: acessibilidade reduzida fora de toque direto.  
**Recomendação**: expor ações equivalentes em controles Compose acessíveis dentro do app.

---

## 10) CONSISTÊNCIA E DESIGN TOKENS

### TELA/COMPONENTE: tema e tokens
**Estado atual**: `GhostPinColors` existe, porém `MainActivity` ainda usa muitos `Color(0x...)` inline inclusive no `darkColorScheme`.  
**Problema**: drift visual e manutenção difícil; risco de inconsistência entre superfícies/modos.  
**Severidade**: 🟠  
**Impacto**: UI inconsistente e custo alto para evolução do design system.  
**Recomendação**: migrar gradualmente para tokens (`GhostPinColors.*`) + wrapper de tema único.

---

# Top 5 problemas prioritários (próxima sprint)
1. 🔴 **Acessibilidade TalkBack**: ícones sem `contentDescription` em navegação e ações primárias.
2. 🔴 **Contraste de texto desabilitado** (`#555555` em `#252535` = 2.02:1).
3. 🟠 **Skip do onboarding sem guard-rail** (pode gerar setup incompleto sem aviso forte).
4. 🟠 **Validação de coordenadas sem erro inline** (erro pouco acionável).
5. 🟠 **Falta de feedback no QS Tile sem lastUsedConfig**.

# Quick wins (< 1h cada)
- [ ] Aumentar label da NavigationBar de 10sp para 12sp.
- [ ] Adicionar `contentDescription` nos ícones de NavigationBar e FAB.
- [ ] Exibir erro inline para latitude/longitude inválidas no onboarding.
- [ ] Inserir snackbar de confirmação em “Apply override”.
- [ ] Adicionar ação “Abrir Configurações” nas snackbars de permissão.
- [ ] Trocar `TextDisabled` por tom com contraste mínimo adequado.

# Métricas sugeridas para validar evolução
- **Task success rate onboarding** (% usuários que chegam ao Start com permissões + mock location corretos).
- **Time-to-first-simulation** (tempo do primeiro launch até estado Running).
- **Form error rate** (tentativas inválidas de coordenadas por sessão).
- **A11y lint coverage** (contagem de componentes acionáveis sem `contentDescription`).
- **UI consistency debt** (% de uso de tokens vs `Color(0x...)` hardcoded).
- **Waypoints interaction reliability** (média de long-press válidos vs removidos imediatamente).
