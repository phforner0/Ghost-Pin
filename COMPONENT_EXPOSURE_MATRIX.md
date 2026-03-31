# Component Exposure Matrix

This document records the current Android component exposure surface of GhostPin,
with emphasis on export status, permission gates, and flavor differences.

Source of truth:
1. `app/src/main/AndroidManifest.xml`
2. `app/src/playstore/AndroidManifest.xml`

## Main Manifest

| Component | Type | Exported | Permission | Notes |
|---|---|---:|---|---|
| `.ui.MainActivity` | Activity | yes | none | Launcher entry point |
| `.service.SimulationService` | Service | no | none | Foreground location simulation |
| `.service.FloatingBubbleService` | Service | no | none | Overlay bubble / joystick |
| `.service.GhostPinQsTile` | Service | yes | `android.permission.BIND_QUICK_SETTINGS_TILE` | QS tile entry point |
| `.widget.GhostPinWidget` | Receiver | no | none | Home screen widget updates |
| `.scheduling.ScheduleReceiver` | Receiver | no | none | Internal `AlarmManager` events only |
| `.scheduling.BootCompletedReceiver` | Receiver | yes | none | Receives `BOOT_COMPLETED` to rearm schedules |
| `.automation.AutomationReceiver` | Receiver | yes | `com.ghostpin.permission.AUTOMATION` | External automation surface |

## Custom Permission

| Permission | Protection level | Purpose |
|---|---|---|
| `com.ghostpin.permission.AUTOMATION` | `signature` | Restricts external automation broadcasts |

## Sensitive Permissions In Main

| Permission | Why it exists |
|---|---|
| `ACCESS_FINE_LOCATION` | Map/device bootstrap and simulation UX |
| `ACCESS_COARSE_LOCATION` | Same as above, coarse fallback |
| `ACCESS_MOCK_LOCATION` | Mock provider support in `nonplay` runtime |
| `FOREGROUND_SERVICE` | Long-running service |
| `FOREGROUND_SERVICE_LOCATION` | Foreground location simulation |
| `POST_NOTIFICATIONS` | Foreground notification |
| `INTERNET` | OSRM, Nominatim, remote map style |
| `RECEIVE_BOOT_COMPLETED` | Rearm schedules after reboot |
| `SYSTEM_ALERT_WINDOW` | Floating bubble / joystick overlay |

## Playstore Flavor Overrides

The `playstore` flavor removes the following from the merged manifest via
`app/src/playstore/AndroidManifest.xml`:

### Removed permissions
1. `ACCESS_MOCK_LOCATION`
2. `RECEIVE_BOOT_COMPLETED`
3. `SYSTEM_ALERT_WINDOW`
4. `com.ghostpin.permission.AUTOMATION`

### Removed components
1. `.service.FloatingBubbleService`
2. `.service.GhostPinQsTile`
3. `.widget.GhostPinWidget`
4. `.scheduling.ScheduleReceiver`
5. `.scheduling.BootCompletedReceiver`
6. `.automation.AutomationReceiver`

## Hardening Notes

1. `ScheduleReceiver` is intentionally `exported=false` in the main manifest.
2. `AutomationReceiver` remains exported, but only behind a `signature` permission.
3. `BootCompletedReceiver` must stay exported because `BOOT_COMPLETED` is a system broadcast.
4. The `playstore` flavor removes overlay, boot, widget, QS tile, automation, and mock-location surfaces from the merged artifact.

## Validation Commands

Use these commands when reviewing manifest regressions:

```powershell
./gradlew.bat :app:compileNonplayDebugKotlin
./gradlew.bat :app:compilePlaystoreDebugKotlin
./gradlew.bat :app:compileNonplayDebugAndroidTestKotlin
```

When checking CI parity:

```powershell
./gradlew.bat --no-daemon :app:testNonplayDebugUnitTest
./gradlew.bat --no-daemon :realism-lab:test
```
