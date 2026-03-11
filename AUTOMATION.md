# GhostPin — Automation Guide

Control GhostPin via ADB, Tasker, or MacroDroid without opening the app.

## Permission

All automation commands require the `com.ghostpin.permission.AUTOMATION` permission.
This is automatically granted to apps signed with the same certificate or to ADB commands.

## Actions

| Action | Description |
|--------|-------------|
| `com.ghostpin.ACTION_START` | Start simulation |
| `com.ghostpin.ACTION_STOP` | Stop simulation |
| `com.ghostpin.ACTION_PAUSE` | Pause/resume simulation |
| `com.ghostpin.ACTION_SET_ROUTE` | Load a GPX/KML/TCX route file |
| `com.ghostpin.ACTION_SET_PROFILE` | Change movement profile |

## Extras

| Extra | Type | Range | Default | Used by |
|-------|------|-------|---------|---------|
| `EXTRA_LAT` | double | -90.0 to 90.0 | — | ACTION_START |
| `EXTRA_LNG` | double | -180.0 to 180.0 | — | ACTION_START |
| `EXTRA_SPEED_RATIO` | double | 0.0 to 1.0 | 0.65 | ACTION_START |
| `EXTRA_FREQUENCY_HZ` | int | 1 to 60 | 5 | ACTION_START |
| `EXTRA_PROFILE_ID` | string | Pedestrian, Bicycle, Car, Urban Vehicle, Drone | Car | ACTION_START, ACTION_SET_PROFILE |
| `EXTRA_ROUTE_FILE` | string (URI) | file:// or content:// | — | ACTION_SET_ROUTE |

## ADB Examples

### Start simulation at specific coordinates
```bash
adb shell am broadcast \
  -a com.ghostpin.ACTION_START \
  --ed EXTRA_LAT -23.5505 \
  --ed EXTRA_LNG -46.6333 \
  --es EXTRA_PROFILE_ID Car \
  com.ghostpin.app
```

### Start with custom speed ratio (80% of max)
```bash
adb shell am broadcast \
  -a com.ghostpin.ACTION_START \
  --ed EXTRA_SPEED_RATIO 0.8 \
  com.ghostpin.app
```

### Stop simulation
```bash
adb shell am broadcast \
  -a com.ghostpin.ACTION_STOP \
  com.ghostpin.app
```

### Pause simulation
```bash
adb shell am broadcast \
  -a com.ghostpin.ACTION_PAUSE \
  com.ghostpin.app
```

### Change profile to Pedestrian
```bash
adb shell am broadcast \
  -a com.ghostpin.ACTION_SET_PROFILE \
  --es EXTRA_PROFILE_ID Pedestrian \
  com.ghostpin.app
```

### Load a GPX route
```bash
adb shell am broadcast \
  -a com.ghostpin.ACTION_SET_ROUTE \
  --es EXTRA_ROUTE_FILE "file:///sdcard/Download/route.gpx" \
  com.ghostpin.app
```

## Tasker Integration

1. **Action:** Send Intent
2. **Action:** `com.ghostpin.ACTION_START`
3. **Package:** `com.ghostpin.app`
4. **Target:** Broadcast Receiver
5. **Extras:**
   - `EXTRA_LAT` → `-23.5505` (Double)
   - `EXTRA_LNG` → `-46.6333` (Double)
   - `EXTRA_PROFILE_ID` → `Car` (String)

## MacroDroid Integration

1. **Add Action → Connectivity → Send Broadcast**
2. **Action:** `com.ghostpin.ACTION_START`
3. **Package:** `com.ghostpin.app`
4. **Add extras as key-value pairs**

## Validation

All numeric extras are clamped to their valid ranges:
- Latitude is clamped to [-90, 90]
- Longitude is clamped to [-180, 180]
- Speed ratio is clamped to [0.0, 1.0]
- Frequency is clamped to [1, 60] Hz

Invalid profile names default to "Car". Invalid route URIs are logged and rejected.
All log messages containing user data are sanitized via `LogSanitizer`.
