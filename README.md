# Dream Archive

A desktop app for transcribing voice recordings into a searchable Markdown journal — built for dreams, but works for any audio note.

It watches a folder of audio files, transcribes them with OpenAI Whisper, generates a title and summary with `gpt-4o-mini`, and writes a `.md` file into a year/month vault structure. An optional Android companion records on your phone and syncs to Google Drive so the desktop app picks it up automatically.

## Install (prebuilt)

Grab the installer for your platform from the [latest release](../../releases/latest):

- **Windows** — `Dream Archive Setup x.y.z.exe` (NSIS installer; unsigned, so SmartScreen will warn on first launch — click *More info → Run anyway*)
- **macOS** — `Dream Archive-x.y.z.dmg` (signed and notarized)
- **Android** — `DreamArchive-x.y.z.apk` (sideload — see the [Android section](#android-companion-optional))

### Prerequisites

- **An OpenAI API key.** On first launch the app will tell you exactly where to drop a `.env` file (e.g. `%APPDATA%\Dream Archive\.env` on Windows). Contents:
  ```
  OPENAI_API_KEY=sk-...
  ```
- **ffmpeg** on your `PATH`. The app uses it to normalise audio before transcription.
  - Windows: `winget install ffmpeg` (or [download manually](https://ffmpeg.org/download.html#build-windows) and add to PATH)
  - macOS: bundled automatically by the installer (or `brew install ffmpeg`)
  - Linux: `apt install ffmpeg` / equivalent

## Build from source (desktop)

1. Clone the repo and install dependencies:
   ```bash
   npm install
   ```
2. Create `app/.env` with your OpenAI key (see `app/.env.example`).
3. Run it:
   - Mac/Linux: `./run.sh`
   - Windows: `run.bat`

The app opens at `http://localhost:3000`. Pick an **input folder** (where your audio files land) and an **output folder** (your dream journal). Hit *Transcribe*.

To produce installers:
```bash
npm run dist:win   # Windows NSIS .exe
npm run dist:mac   # macOS .dmg (requires macOS + Apple Developer ID for signing/notarization)
```

## Output structure — open it as an Obsidian vault

The output folder is a plain folder of Markdown files, organised by year and month:

```
Output Folder/
├── 2025/
│   └── 2025_08_August/
│       └── 2025-08-12 - A Dream About Owls.md
└── 2026/
    └── 2026_01_January/
        └── 2026-01-01 - Mushroom Battle of the Mind.md
```

Each `.md` has frontmatter (date, source audio) and the transcript + summary.

**To browse it as a vault:** open the output folder directly in [Obsidian](https://obsidian.md) — `File → Open Vault → Open folder as vault`. You get search, backlinks, graph view, tags, etc., over your whole dream archive.

It also opens fine in any other Markdown viewer ([Logseq](https://logseq.com), [VS Code](https://code.visualstudio.com), [iA Writer](https://ia.net/writer), Typora, etc.) — there's nothing Obsidian-specific in the files.

## Android companion (optional)

The `android/` folder contains a Kotlin/Compose app for recording on a Samsung phone (or any Android 8+ device) — double-press the side key from a locked screen, the recording starts, and uploads to Google Drive when you're back on Wi-Fi.

To use it with the desktop app, mirror the Drive folder via **Drive for Desktop** and point Dream Archive's input folder at the synced location.

### Building it yourself

The Android app uses Google's standard OAuth flow for Drive — no client IDs or secrets are checked into the repo. The OAuth client is bound to your package name + your signing certificate's SHA-1, so you need to register your own client in a Google Cloud project before the Drive sign-in will work.

1. **Create a Google Cloud project** at [console.cloud.google.com](https://console.cloud.google.com).
2. **Enable the Drive API** — APIs & Services → Library → search *Google Drive API* → Enable.
3. **Configure the OAuth consent screen** — APIs & Services → OAuth consent screen → *External*. Add the `.../auth/drive.file` scope. Leave it in **Testing** mode (going to Production requires Google verification for sensitive scopes — only needed for >100 users).
4. **Add yourself as a test user** on the consent screen. Add anyone else who'll use your build the same way.
5. **Get your signing key's SHA-1.** For a debug build:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   For release, use your own keystore.
6. **Create the OAuth 2.0 client** — APIs & Services → Credentials → *Create Credentials* → *OAuth 2.0 Client ID* → **Android**. Package name: `com.conndreams.recorder`. SHA-1: from step 5.
7. **Build the APK** — open `android/` in Android Studio, or:
   ```bash
   cd android && ./gradlew assembleRelease
   ```
   Release builds need an `android/keystore.properties` file (gitignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. See `android/app/build.gradle.kts`.
8. **Install** the APK on your phone (Files → tap → enable *Install from this source*).

The 100-test-user cap is per consent screen — fine for personal use and small groups.

## Stack

- Desktop: Electron + plain Node HTTP server, vanilla HTML/CSS/JS
- Transcription: OpenAI `gpt-4o-transcribe` (override via `TRANSCRIPTION_MODEL` in `.env`)
- Summary: OpenAI `gpt-4o-mini` (override via `SUMMARY_MODEL`)
- Mobile: Kotlin, Jetpack Compose, Google Drive REST
- Audio: ffmpeg (bundled on macOS via `install-mac.sh`, expected on PATH on Windows)

## License

No license has been added yet — code is shared for personal/reference use only.
