# ATR Launcher

A custom home screen for the apartment Fire TV, in the ATR colors (black, blue, white).

## How it works
- **Private side**: ATR Launcher opens from the app list behind a 4 digit PIN. It locks after `lockMinutes` without a button press and whenever the TV turns off. Roommates keep Amazon's normal home screen.
- **Home button**: while unlocked, the `ATR Home button` accessibility service brings you back here when Home is pressed. While locked it does nothing.
- **Private keys** (Notion key, calendar link) never go in this repo. They live in `my_keys.json` on the PC and are sent to the TV with `6 - Send my keys to TV.bat`.
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
| `Lock.kt` | PIN lock and on screen PIN pad |
| `Secrets.kt` | Private keys stored on the TV only |
| `Personal.kt` | Google Calendar (Next up) and Notion (ATR Global rings) |
