# Source 🎵

**Version:** 2.6.0

Source is a modern, native Android music player. It focuses on a clean interface, smooth performance, and complete management of your local audio library — no ads, no analytics, no tracking.

## ✨ Features

### Library
- **Local library management:** Automatically scans and organizes your music by Songs, Albums, Artists and Genres.
- **Folder navigation:** Browse your device's filesystem directly to play music from specific directories.
- **Search:** Instantly find songs, albums and artists across the whole library.
- **Playlists:** Create and manage your own playlists.
- **Tag editor:** Edit song metadata (title, artist, album, artwork…) straight from the app.
- **Rescan:** Refresh the library manually when new files or folders appear.

### Playback
- **Media3 / ExoPlayer engine:** Smooth playback, precise seeking and gapless transitions.
- **Queue management:** Dynamic queue kept in sync between the player, the mini-player and the queue screen.
- **Mini-player:** Persistent playback bar with swipe-to-dismiss.
- **Background playback:** Foreground media service with full notification and lock-screen controls.

### Multi-room & casting
- **Sonos:** Discover and play to Sonos devices on the local network.
- **Google Cast:** Send playback to Chromecast-enabled devices.
- **Local HTTP server:** Streams your local files to those devices without any cloud upload.

### Personalization
- **Last.fm:** Connect your account and scrobble your listening history.
- **Themes:** Dark mode, accent color picker and font selection.
- **Material 3 UI:** Built entirely with Jetpack Compose — smooth animations, dynamic theming, responsive layout.

## 🛠️ Tech stack

- 100% Kotlin
- UI: Jetpack Compose (Material 3, no XML layouts)
- Architecture: MVVM, Hilt (dependency injection), Coroutines, StateFlow
- Audio engine: Media3 / ExoPlayer
- Local database: Room
- Preferences: DataStore

## 🚀 Build and run

### Prerequisites
- Android Studio (latest stable) with the Android SDK 36 platform installed.
- JDK 17.
- A physical Android device (Android 8.0+ / API 26+) or an emulator.

### Steps
1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd source-player
   ```
2. **Open the project:**
   - In Android Studio, choose **Open** and select the `source-player` folder.
   - Let Gradle sync and download the dependencies.
3. **(Optional) Last.fm credentials:**
   Add your keys to `local.properties` (git-ignored) to enable scrobbling:
   ```properties
   lastfm.api_key=YOUR_KEY
   lastfm.api_secret=YOUR_SECRET
   ```
4. **Build and run:**
   - Connect your device via USB (USB debugging enabled) or start an emulator.
   - Run the `app` configuration, or from the command line:
     ```bash
     ./gradlew installDebug
     ```
5. **Permissions:**
   On first launch, grant access to your audio files so the library can be scanned.

## 📦 Versioning

The version lives in a single place — `source.version` in `gradle.properties`, as `MAJOR.MINOR.PATCH`.
`versionCode` is derived from it at build time, so it always moves forward with the version name.
