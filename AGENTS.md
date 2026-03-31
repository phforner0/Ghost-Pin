# AGENTS.md

## Purpose
This file gives repository-specific guidance to coding agents working in GhostPin.
Prefer the smallest correct change.
Preserve existing architecture and naming patterns.
Do not introduce broad refactors unless the task requires them.

## Rule Files
No existing `AGENTS.md` was found when this file was drafted.
No Cursor rules were found in `.cursor/rules/`.
No `.cursorrules` file was found.
No Copilot instructions were found in `.github/copilot-instructions.md`.
If any of those files are added later, merge their repository-specific rules into this file.

## Repo Map
- `app/`: Android app using Compose, Hilt, Room, MapLibre, services, and app widgets.
- `core/`: Pure Kotlin/JVM domain models, math, and security helpers.
- `engine/`: Pure Kotlin/JVM interpolation, noise, and validation logic.
- `realism-lab/`: Pure Kotlin/JVM realism metrics and reporting utilities.
- `.github/workflows/ci.yml`: Best source of truth for commands actually used in CI.

## Toolchain And Variants
- Use the Gradle wrapper, not a system Gradle install.
- Gradle wrapper version: `8.14.3`.
- Kotlin version: `2.1.10`.
- Android Gradle Plugin version: `8.8.2`.
- JDK and Gradle toolchain version: `21`.
- App SDKs: `compileSdk 35`, `targetSdk 35`, `minSdk 26`.
- Distribution flavors: `nonplay` and `playstore`.
- Default to `nonplayDebug` unless the task is explicitly flavor-specific.
- KSP is used; do not reintroduce `kapt`.

## Build Commands
- macOS/Linux: `./gradlew :app:compileNonplayDebugKotlin`
- Windows: `./gradlew.bat :app:compileNonplayDebugKotlin`
- `./gradlew :app:assembleNonplayDebug`
- `./gradlew :app:compilePlaystoreDebugKotlin`
- `./gradlew :core:build`
- `./gradlew :engine:build`
- `./gradlew :realism-lab:build`

## Test Commands
- `./gradlew :core:test`
- `./gradlew :engine:test`
- `./gradlew :realism-lab:test`
- `./gradlew :app:testNonplayDebugUnitTest`
- `./gradlew :app:testPlaystoreDebugUnitTest`
- `app` uses JUnit 4; `core`, `engine`, and `realism-lab` use JUnit 5.
- No checked-in instrumentation tests were found under `app/src/androidTest/kotlin`.

## Single Test Commands
- `./gradlew :core:test --tests "com.ghostpin.core.math.GeoMathTest"`
- `./gradlew :engine:test --tests "com.ghostpin.engine.interpolation.RouteInterpolatorTest"`
- `./gradlew :realism-lab:test --tests "com.ghostpin.realism.metrics.RealismMetricsTest"`
- `./gradlew :app:testNonplayDebugUnitTest --tests "com.ghostpin.app.routing.OsrmRouteProviderTest"`
- `./gradlew :core:test --tests "com.ghostpin.core.model.RouteExtensionsTest.*"`
- `./gradlew :engine:test --tests "*repeat controller*"`
- `./gradlew :app:testNonplayDebugUnitTest --tests "*parses valid OSRM response*"`
- For Kotlin backticked test names, wildcard method filters are often more reliable than exact method signatures.

## Lint And CI
- CI compile step: `./gradlew :app:compileNonplayDebugKotlin --no-daemon`
- CI test steps: `:core:test`, `:engine:test`, and `:app:testNonplayDebugUnitTest`.
- App unit tests are marked `continue-on-error` in CI because some tests may require Android stubs.
- CI downloads standalone `ktlint` `1.5.0` and runs `./ktlint --reporter=plain "core/src/**/*.kt" "engine/src/**/*.kt" "app/src/**/*.kt"`.
- `realism-lab` is omitted from CI lint, and lint is advisory because CI uses `|| true`.

## Reports
- Test HTML reports appear under `core/build/reports/tests/`, `engine/build/reports/tests/`, and `app/build/reports/tests/`.
- JUnit XML results appear under each module's `build/test-results/` directory.

## Style Baseline
- `gradle.properties` sets `kotlin.code.style=official`.
- Follow Kotlin official style first, then match the nearby file.
- Do not reformat unrelated files while making a focused fix.

## Imports And Formatting
- Wildcard imports are explicitly allowed by `.editorconfig` and are common in Compose, Room, and some tests.
- Use 4-space indentation and keep lines at or under `120` characters.
- Trailing commas are disabled for declarations and call sites.
- Multiline parameter lists are common, with closing `)` and `}` on their own lines.
- Section divider comments are common and acceptable.

## KDoc And Comments
- Public models, repositories, services, and non-trivial functions often have KDoc.
- Good comments explain intent, invariants, migrations, bug fixes, or rationale.
- Avoid comments that only narrate obvious code.

## Naming
- Packages use lowercase dot-separated names.
- Types use `PascalCase`; functions and properties use `camelCase`.
- Constants use `SCREAMING_SNAKE_CASE`; enum entries use `UPPER_SNAKE_CASE`.
- Composable names are usually `PascalCase` feature nouns such as `GhostPinScreen`.
- Backing state follows the private `_foo` and public `foo` pattern.
- Preserve unit-bearing names such as `distanceMeters`, `durationMs`, `elapsedTimeSec`, `frequencyHz`, `maxAccelMs2`, and `speedOverrideMs`.
- Do not replace unit-bearing names with ambiguous names like `distance` or `time`.

## Types And State
- Prefer explicit public types on APIs and state holders.
- Use `data class` for value-like domain models and UI state.
- Use `sealed class` and `data object` for state machines and result states.
- Use `Flow<T>` and `StateFlow<T>` for observable state.
- The dominant pattern is private `MutableStateFlow` plus public `asStateFlow()`.
- One-off UI events use `MutableSharedFlow`.
- Derived state often uses `combine(...)` and `stateIn(...)`.
- Keep nullability explicit rather than using sentinel values.

## Compose Guidance
- Screen entry points are responsible for obtaining view models.
- Prefer passing plain data and callbacks to child composables instead of threading full view models deep into the tree.
- The repo mostly uses `collectAsState()` and `LaunchedEffect`.
- For local UI state, use `remember { mutableStateOf(...) }`, `mutableIntStateOf`, or `mutableFloatStateOf`.
- Reuse theme tokens from `app/ui/theme` instead of scattering raw color literals.
- Preserve previewable and testable child composables when editing UI.

## Dependency Injection
- Hilt is the DI standard in `app`.
- Prefer constructor injection.
- Use `@Module` and `@Provides` only when constructor injection is insufficient.
- Use `@HiltViewModel` for view models, `@AndroidEntryPoint` for Android components, and `@HiltAndroidApp` for the application.
- Do not add parallel DI patterns.

## Room And Persistence
- DAOs use `Flow` for observed reads and `suspend` functions for one-shot access.
- Keep SQL inline in `@Query` annotations.
- Domain mapping is explicit through helpers like `toDomain()` and `fromDomain()`.
- If you change schema, update the database version and add a migration.
- Preserve existing migration history in `GhostPinDatabase`.
- Schema export is enabled; keep it working.
- Be careful with stable built-in profile IDs, profile names, Room field names, and JSON keys.
- Do not rename persisted identifiers casually without a migration need.

## Error Handling And Logging
- Use `require(...)`, `check(...)`, or `error(...)` for programmer or data invariants.
- Use `runCatching { ... }`, `fold`, `onSuccess`, `onFailure`, or `getOrElse` around IO and parsing edges.
- Prefer explicit fallback behavior over silent failure.
- Surface recoverable failures through state or user-visible messaging.
- Logging uses `android.util.Log`, not Timber.
- Typical pattern: `private const val TAG = "ClassName"`.
- Use `Log.d` for lifecycle/debug info, `Log.w` for degraded behavior, and `Log.e` for actual failures.
- Sanitize coordinate-bearing or sensitive strings with `LogSanitizer` before logging them.

## Testing Conventions
- Match the framework and assertion style already used by the module under test.
- Backticked sentence-style test names are common.
- Assertions are direct and local; avoid heavy helper layers unless repetition is real.
- Numeric tolerances are common in geo and math tests.
- App tests often isolate parsing and logic from Android runtime dependencies.

## Change Strategy
- Prefer small focused edits.
- Preserve module boundaries and avoid unrelated cleanup.
- Update or add tests near the change when practical.
- Run the narrowest useful verification command first.
- For app logic or service changes, at minimum run `:app:compileNonplayDebugKotlin` if full tests are not practical.

## Final Reminder
- Use repository evidence over generic Android advice.
- Match the style of the module and file you are editing.
- When in doubt, follow nearby code before introducing a new pattern.
