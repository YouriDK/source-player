# Phonograph 🎵

**Version:** 1.0.0

Phonograph is a modern, native Android music player application. It is designed with a focus on beautiful aesthetics, smooth performance, and comprehensive local audio file management. 

## ✨ Main Features
- **Local Audio Library Management:** Automatically scans and organizes your local music by Songs, Albums, Artists, and Genres.
- **Folder Navigation:** Browse your device's filesystem directly to play music from specific directories.
- **Advanced Playback:** Full-featured audio engine supporting smooth playback, precise seeking, and gapless transitions (powered by Androidx Media3/ExoPlayer).
- **Queue Management:** Dynamic playback queue with seamless synchronization between player screens.
- **Playlists:** Create and manage custom playlists.
- **Modern UI/UX:** Built entirely with Jetpack Compose featuring smooth animations, dynamic themes, and a responsive layout.

## 🛠️ Tech Stack & Tools Used
This project was developed leveraging cutting-edge tools to accelerate design and development:

- **Design:** [Google Stitch](https://stitch.google.com/) for crafting a premium, pixel-perfect user interface.
- **AI Assistant:** Claude 4.6 (Anthropic) for rapid code generation, bug fixing, and architectural guidance.
- **Development Environment:** Antigravity IDE.
- **Core Technology:**
  - 100% Kotlin
  - UI: Jetpack Compose
  - Audio Engine: Media3 / ExoPlayer
  - Architecture: MVVM, Hilt (Dependency Injection), Coroutines
  - Local Database: Room

## 🚀 How to Install and Try

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (Koala Feature Drop or newer) or Antigravity IDE.
- A physical Android device (Android 8.0+ / API 26+) or an Android Emulator.

### Steps
1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd source-player
   ```
2. **Open the Project:**
   - Launch Android Studio or Antigravity IDE.
   - Select **Open** and choose the `source-player` folder.
   - Wait for Gradle to perform the initial sync and download all necessary dependencies.
3. **Build and Run:**
   - Connect your Android device via USB (with USB Debugging enabled) or start an emulator.
   - Click the green **Run 'app'** button (Play icon) in the top toolbar to build and install the APK.
4. **Permissions:**
   - Upon first launch, the app will request permission to access your audio files. Please grant it so the app can scan your local library.

---
*Created as part of the Phonograph modernization effort.*
