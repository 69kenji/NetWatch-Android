# NetWatch Android thin client

This app is a LAN remote for a paired NetWatch PC. It has no torrent engine, provider credentials, VPN stack, arbitrary URL opener, download mode, or offline library.

NetWatch for Windows 1.0.9 or newer is required. The PC remains the security boundary and owns discovery, torrent resolution, streaming, and optional subtitle-provider access. The desktop repository owns the authoritative [Remote Protocol v1](https://github.com/69kenji/netwatch/blob/main/remote-gateway/protocol/remote-v1.md) contract.

## Build

Use JDK 17 and an Android SDK containing API 37 and Build Tools 36.0.0. The checked-in Gradle wrapper uses Gradle 9.3.1. The app is pinned to AGP 9.1.1, Compose BOM 2026.08.00, Media3 1.11.0, and CameraX 1.6.2.

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\android-sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Pair and use

1. Start NetWatch on the PC and wait for its protected runtime to become ready.
2. Open Settings, choose the active private IPv4 interface, and enable Remote access.
3. Select **Pair new device**. The QR is valid for five minutes and one successful claim.
4. On Android, select **Scan pairing QR** and grant the camera permission after reading its disclosure.
5. Browse Home, Discover, or Search, select a title and release, and play it. TV episodes are listed for the selected season and remain visible while their streams load.
6. Use **Unpair this device** on Android or revoke the individual device from PC Settings when access is no longer needed.

The app uses the same compact Discover selectors and Iconoir navigation geometry as the PC application. The player uses the NetWatch control layout and the same Iconoir vector geometry as the PC player. Preparation and rebuffering are exclusive artwork-backdrop states; regular playback controls remain hidden until video renders. The resize control cycles **Fill**, **Fit**, **Original**, and **16:9**. Fill covers the viewport, while Fit constrains the video by height.

The compact **Tracks** menu manages embedded and externally retrieved subtitles. Subtitle size, background, and contrast are configurable and stored only on the Android device.

Both devices must remain on the selected private LAN. Guest Wi-Fi/client isolation may prevent connection. A changed PC address requires a new QR. A regenerated PC identity or revoked credential always requires re-pairing.

## Permissions and privacy

- `INTERNET`: communicates only with the pinned gateway origin selected by the QR.
- `CAMERA`: scans the pairing QR locally; frames are not retained or transmitted.

Backups are disabled. The paired profile is encrypted with Android Keystore, and credentials are never placed in a URL or application log.

## Releases

Release APKs belong in GitHub Releases with a SHA-256 checksum. APKs, signing keys, local SDK paths, and generated build directories must not be committed to this repository.
