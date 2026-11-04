# Minimal Launcher (Android TV)

A minimal home-screen replacement for Android TV and Android boxes. Android 9 (API 28) and above.

## Features

- **Home**: large clock and date, Favourites row, All apps button
- **Favourites**: long-press OK on an app (or press Menu / Info) to add, remove or reorder
- **Installed apps**: grid of every launchable app (Leanback and regular launcher apps)
- **Hide apps**: hide from the long-press menu, or tick several at once under menu ▸ Hidden apps
- **Custom wallpaper**: pick an image, choose a colour preset, or reset (menu ▸ Wallpaper)
- **Top settings panel**: Search, Wi-Fi, Bluetooth, Date & time, Launcher menu

Search opens the system search (Google TV / Android TV) when the device has one, otherwise it
searches installed apps by name.

## Project layout

```
TVLauncher/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml        HOME + LEANBACK_LAUNCHER intent filters
        ├── java/com/minimal/tvlauncher/
        │   ├── MainActivity.kt        screens, menus, search, wallpaper flow
        │   ├── AppRepository.kt       queries launchable apps
        │   ├── AppInfo.kt             app model
        │   ├── AppAdapter.kt          tile adapter (favourites row + grid)
        │   ├── PrefsStore.kt          favourites, hidden apps, wallpaper choice
        │   ├── Wallpapers.kt          image import, presets
        │   └── SystemShortcuts.kt     Wi-Fi / Bluetooth / Date & time / search intents
        └── res/                       layouts, drawables, strings, theme
```

## Build

Open the `TVLauncher` folder in Android Studio (Hedgehog or newer, JDK 17) and run, or:

```
gradle assembleDebug
adb connect <tv-ip>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Make it the default launcher

Press Home on the TV and pick **Minimal Launcher** if a chooser appears. Newer Android TV / Google TV
builds often skip the chooser; the stock launcher can then be disabled over adb (package names vary
by device, so check `adb shell pm list packages | grep -i launcher`):

```
adb shell pm disable-user --user 0 <stock-launcher-package>
```

Re-enable it with `pm enable <stock-launcher-package>` to go back.

## Notes

- Some TVs have no file picker, so choosing a wallpaper image may show a message; colour presets always work.
- Wi-Fi, Bluetooth and Date & time shortcuts fall back to the main Settings screen where a device
  does not expose a direct screen.
