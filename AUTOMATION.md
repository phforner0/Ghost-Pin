# Automation

GhostPin exposes a narrow automation surface through `AutomationReceiver`.

Source of truth:
1. `app/src/main/AndroidManifest.xml`
2. `app/src/main/kotlin/com/ghostpin/app/automation/AutomationReceiver.kt`

## Security Model

- Receiver: `com.ghostpin.app.automation.AutomationReceiver`
- Exported: `true`
- Guarded by: `com.ghostpin.permission.AUTOMATION`
- Protection level: `signature`

This means only same-signature callers can send automation broadcasts directly.
It is not an open Tasker-style public API.

## Supported Actions

| Action | Purpose |
|---|---|
| `com.ghostpin.ACTION_START` | Start a simulation |
| `com.ghostpin.ACTION_STOP` | Stop the current simulation |
| `com.ghostpin.ACTION_PAUSE` | Pause the current simulation |
| `com.ghostpin.ACTION_SET_ROUTE` | Load a route file into the app |
| `com.ghostpin.ACTION_SET_PROFILE` | Change the active profile |

## Extras

| Extra | Type | Meaning |
|---|---|---|
| `EXTRA_LAT` | `double` | Start latitude |
| `EXTRA_LNG` | `double` | Start longitude |
| `EXTRA_SPEED_RATIO` | `double` | Runtime speed ratio `0..1` |
| `EXTRA_FREQUENCY_HZ` | `int` | Update frequency `1..60` |
| `EXTRA_PROFILE_ID` | `string` | Built-in or custom profile lookup key |
| `EXTRA_ROUTE_FILE` | `string` | `content://` or `file://` route URI |

## Route File Rules

For `ACTION_SET_ROUTE`:

1. Only `content://` and `file://` URIs are accepted.
2. The file content must parse as GPX, KML, or TCX.
3. Callers using `content://` URIs must provide a readable URI grant.

## Notes

1. `ACTION_SET_PROFILE` now forwards the provided lookup key to `SimulationService`, so custom profiles can be targeted by stable ID.
2. Invalid route URIs and malformed route files fail explicitly inside the app and are surfaced through simulation state.
