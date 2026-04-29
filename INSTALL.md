# Dream Archive — Android install guide

Install instructions for the prebuilt Android app from the [latest release](../../releases/latest).

## 1. Get the APK on your phone

Transfer `DreamArchive-x.y.z.apk` to your phone — Google Drive, USB cable, file-share app, whichever is easiest.

## 2. Allow installs from your file manager (one-time)

Tap the APK. Android will warn that it can't install from this source — tap **Settings** in that dialog and enable **Allow from this source** for whichever app you used to open the APK (Files, My Files, Drive, etc.). Back out, tap the APK again, **Install**.

## 3. First launch

Open **Dream Archive** from your app drawer.

- Tap **Connect Google Drive** and sign in with the Google account you want recordings synced to. The app creates a folder called *Dream Archive — Audio Recordings* in your Drive.
- Grant **microphone** and **notifications** when prompted. Notifications keep the recording chip visible while running and let the foreground service stay alive with the screen off.

> **If Drive sign-in fails** with *"access denied"* or similar: the OAuth client used by this prebuilt APK is in *Testing* mode and your Gmail isn't on the test-user list. Send your Gmail to whoever shared the APK with you — they add it in the Google Cloud Console (~30 seconds on their end). Alternatively, build the APK yourself with your own OAuth client (see the README's *Building it yourself* section).

## 4. Pair with the desktop app

Recordings auto-upload from your phone to the *Dream Archive — Audio Recordings* folder on Drive. To pull them into the desktop transcription pipeline:

- Install **Drive for Desktop** on the same machine that runs the Dream Archive desktop app.
- Set the *Dream Archive — Audio Recordings* folder to **Mirror** (or *Available offline*).
- In the Dream Archive desktop app, set the **Input folder** to the synced path:
  - macOS: `~/Library/CloudStorage/GoogleDrive-…/My Drive/Dream Archive — Audio Recordings`
  - Windows: usually `%USERPROFILE%\My Drive\Dream Archive — Audio Recordings` (depends on your Drive for Desktop config)

Recordings appear there within ~1 min of the phone finishing the upload, and transcription kicks off automatically.

## 5. Map a hardware key for one-touch recording

The killer feature: wake the phone with one button press, no unlock needed.

**Samsung Galaxy** (cleanest path):
- **Settings → Advanced features → Side key**
- Set **Double press** to **Open app** → pick **Dream Archive**.
- Long-press behaviour: leave as-is.

Now: phone face-down on the bedside table, eyes closed, double-tap the side key — recording starts. Double-tap again to stop. Audio uploads when you're back on Wi-Fi.

**Other Android phones:** look for an equivalent power/volume-button shortcut in your phone's settings, or use a triple-press accessibility shortcut (varies by manufacturer and Android version).

## 6. Add the widget (optional)

Long-press your home screen → **Widgets** → scroll to **Dream Archive** → drag the 3×1 onto a panel. One tap to start/stop.

## 7. Battery optimisation — only if recordings get cut short

By default the foreground service protects in-progress recordings, and short ones almost never get killed. Try it as-is first.

If recordings longer than 15 min get cut, or the side-key shortcut sometimes fails to wake the app after a day idle:

- **Settings → Apps → Dream Archive → Battery → Unrestricted**

That stops aggressive background management. Cost is negligible — the app does nothing while not recording.

## Useful to know

- Tapping the launcher icon **opens the app** (not records). For tap-to-record, toggle **Settings (gear) → Record on launch**.
- *"Authentication needed"* notification = Drive token expired. Tap it and sign in again; anything queued uploads automatically afterwards.
- If a recording ends weirdly (phone restart, app killed mid-record), the file gets quarantined under *Damaged* so it doesn't poison the queue. Cleanup button in the app.
