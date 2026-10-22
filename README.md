# ATR Launcher

A custom home screen for the apartment Fire TV, in the ATR colors (black, blue, white).

## How it works
- **Home button**: Fire OS won't let another app be the default home, so the `ATR Home button` accessibility service watches for Amazon's home screen and brings ATR Launcher forward instead.
- **Look and layout**: everything comes from `config/config.json`: colors, which cards show, rows, favorites, hidden apps. Edit it, bump `"version"`, push, and the TV picks it up within 30 minutes (or ATR Settings > Pull latest look).
- **App updates**: every push to `main` builds a signed APK and publishes a GitHub release with `version.json`. The TV checks every 6 hours and shows an "Update ready" pill. One click installs it.

## Remote controls on the TV
- **OK**: open the app
- **Hold OK** or **Menu**: favorite, move, hide, app info
- **Home** while home: jump back to the top

## Code map
| File | What it does |
| --- | --- |
| `LauncherActivity.kt` | The home screen: header, info cards, rows |
| `Ui.kt` | Tiles, rows, cards, background, sizing on a 1920x1080 grid |
| `Config.kt` | Loads, saves and merges `config.json` |
| `Feeds.kt` | F1 schedule, A&M football, weather |
| `AppRepo.kt` / `Inputs.kt` | Installed apps and HDMI inputs |
| `HomeWatcherService.kt` | Sends the Home button here |
| `Updater.kt` | Update check, download, install; remote config pull |
| `SettingsActivity.kt` | ATR Settings screen |
