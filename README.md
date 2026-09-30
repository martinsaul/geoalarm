# GeoAlarm

Open-source location alarms for Android. Tap the map, set a radius, and get an alarm or a reminder when you arrive or leave.

- **Works offline.** Alarms fire from GPS alone, which needs no data connection. The map uses OpenStreetMap, and you can download only the regions you need for offline use.
- **No Google required.** The `fdroid` build has no proprietary dependencies and uses an on-device geofence engine. The `gms` build uses Google Play Services geofencing when it's available, which is lighter on battery, and falls back to the same on-device engine when it isn't.
- **No API keys.** Maps come from [MapLibre Native](https://github.com/maplibre/maplibre-native) and [OpenFreeMap](https://openfreemap.org) (OpenStreetMap data). You can switch to your own tile server in settings.

## Status

Early development. Work is tracked in Trakr project `GEO`:

| Ticket | Scope |
|---|---|
| GEO-1 | Scaffold, flavors, CI |
| GEO-2 | Map screen: tap to drop a fence, drag to set the radius |
| GEO-3 | Offline regions: download only the areas you need |
| GEO-4 | Geofence engine: Play Services + on-device GPS fallback |
| GEO-5 | Alarms and reminders: enter/exit, full-screen alarm or notification |
| GEO-6 | F-Droid readiness |

## How it works offline

| Part | Needs internet? |
|---|---|
| Detecting that you entered or left a fence | No. GPS is receive-only. Without data, the first fix after a cold start is slower. |
| Sounding the alarm | No |
| Browsing the map | Only for areas you haven't downloaded |
| Downloading an offline region | Yes, once |

## Building

JDK 17+ and the Android SDK (platform 36) are required.

```
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew testFdroidDebugUnitTest assembleFdroidDebug   # FOSS build
./gradlew testGmsDebugUnitTest assembleGmsDebug         # Play Services build
```

## Contributing

Branch `feature/<name>` off `dev` and open a PR against `dev`. `main` holds releases.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Map data © OpenStreetMap contributors, ODbL.
