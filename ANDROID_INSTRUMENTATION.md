# Android Instrumentation

This document describes how GhostPin Android instrumentation tests are executed locally and in CI.

## Local execution

Recommended commands:

```powershell
./gradlew.bat :app:compileNonplayDebugAndroidTestKotlin
./gradlew.bat :app:connectedNonplayDebugAndroidTest
```

If you want to run a single class:

```powershell
./gradlew.bat :app:connectedNonplayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ghostpin.app.service.SimulationServiceInstrumentedTest
```

## Current instrumentation coverage

The repository now includes Android tests for:

1. `SimulationServiceInstrumentedTest`
2. `ScheduleReceiverInstrumentedTest`
3. `BootCompletedReceiverInstrumentedTest`
4. `ProfileManagerScreenTest`
5. `GhostPinDatabaseMigrationTest`

These tests focus on runtime Android behavior that unit tests and Robolectric cannot fully replace.

## Hilt test infrastructure

- instrumentation runner: `com.ghostpin.app.GhostPinTestRunner`
- test application: `HiltTestApplication`
- fake location injection is provided from `androidTest` through `FakeLocationModule`

This allows `SimulationService` instrumentation tests to run without requiring the device to register a real mock provider.

## CI execution

Instrumentation tests are exposed through the manual workflow:

- `.github/workflows/android-instrumentation.yml`

The workflow:

1. boots an emulator on GitHub Actions
2. runs the selected Gradle connected test task
3. uploads Android test reports as artifacts

Default task:

```text
:app:connectedNonplayDebugAndroidTest
```

## Notes

1. `Build & Test` CI still compiles `androidTest` sources on every PR.
2. Emulator execution is manual by design to avoid slowing every PR and to reduce flakiness/cost.
3. If Android system surfaces change, update both this document and the manual workflow.
