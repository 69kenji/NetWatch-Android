# NetWatch Android

NetWatch Android is a companion app for a paired NetWatch PC on the same local network.

The phone handles browsing and playback. The PC still handles discovery, torrent streaming, VPN routing, and subtitle providers.

The Android app does **not** include:

* a torrent engine
* provider API keys
* a VPN
* downloads or offline storage
* arbitrary URL access

NetWatch for Windows **1.0.9 or newer** is required.

The desktop app defines the protocol used between both devices: [Remote Protocol v1](https://github.com/69kenji/netwatch/blob/main/remote-gateway/protocol/remote-v1.md).

## Build

Requirements:

* JDK 17
* Android SDK with API 37
* Android Build Tools 36.0.0

The project uses:

* Gradle 9.3.1
* Android Gradle Plugin 9.1.1
* Compose BOM 2026.08.00
* Media3 1.11.0
* CameraX 1.6.2

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\android-sdk"

.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Pairing

1. Start NetWatch on the PC and wait for it to become ready.
2. Open **Settings → Remote access**.
3. Choose the local network interface and enable remote access.
4. Select **Pair new device**.
5. On Android, select **Scan pairing QR** and allow camera access.
6. Scan the QR shown on the PC.

The pairing QR expires after five minutes and can only be used once.

Once paired, you can browse Home, Discover, and Search, choose a release, and start playback from the phone.

To remove access, use **Unpair this device** on Android or revoke the device from NetWatch Settings on the PC.

## Playback

The Android player follows the same general layout as the Windows player.

It includes:

* playback controls
* subtitle and audio track selection
* online subtitle support through the PC
* subtitle size, background, and contrast settings
* Fill, Fit, Original, and 16:9 resize modes

Subtitle appearance settings are stored only on the Android device.

During startup or rebuffering, the player shows the title artwork instead of normal playback controls. Controls appear once video is ready.

For TV shows, episodes remain visible while their streams are being prepared.

## Network requirements

The phone and PC must be on the same private local network.

Connections may fail when:

* the devices are on different networks
* guest Wi-Fi isolates devices from each other
* router client isolation is enabled
* the PC's local IP address changes

If the PC address changes, generate a new pairing QR.

Re-pairing is also required if the PC identity is regenerated or the Android device's credential is revoked.

## Privacy

The app requests only:

* **Internet** — connects to the paired NetWatch PC
* **Camera** — scans the pairing QR locally

QR camera frames are not saved or uploaded.

Pairing information is encrypted using Android Keystore. Credentials are never placed in URLs or application logs.

Android backups are disabled for the app.
