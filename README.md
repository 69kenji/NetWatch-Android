# NetWatch Android

NetWatch Android is a LAN companion for NetWatch on Windows. Browse the PC catalog, select releases, and stream through the paired PC without running a torrent engine or VPN on the phone.

NetWatch for Windows v1.1.0 or newer is required. The Windows repository is authoritative for [Remote Protocol v1](https://github.com/69kenji/netwatch/blob/main/remote-gateway/protocol/remote-v1.md).

## Requirements

- Android Studio with JDK 17
- Android SDK API 37
- Android Build Tools 36.0.0
- A Windows PC running NetWatch on the same private network

## Build with Android Studio

1. Open this repository in Android Studio.
2. Let Gradle synchronization finish and install any requested Android SDK components.
3. Select the `app` run configuration and an Android device or emulator.
4. Select **Run** to install a debug build, or **Build → Build APK(s)** to create an APK.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Command-line build

With JDK 17 and the Android SDK configured:

```powershell
.\gradlew.bat assembleDebug
```

## Pairing

1. Start NetWatch on the PC and wait for it to become ready.
2. Open **Settings → Remote access**.
3. Select the private network interface and enable remote access.
4. Select **Pair new device**.
5. On Android, select **Scan pairing QR**, allow camera access, and scan the code.

Pairing codes expire after five minutes and can be used once. Re-pair after a PC address or identity change. Access can be removed from either Android Settings or the paired-device list on the PC.

## Playback

The player supports audio and subtitle tracks, online subtitles supplied by the PC, subtitle appearance controls, seeking, and Fill, Fit, Original, and 16:9 resize modes. Keep Watching history remains on the PC and is shared with the Windows app.

## Network and privacy

- Connections are limited to the paired private IPv4 address and pinned HTTPS identity.
- The PC remains responsible for metadata, release discovery, Prowlarr, torrent streaming, VPN routing, and subtitle providers.
- Android stores no provider credentials, torrent engine, offline media, or independent viewing-history database.
- The pairing profile is protected by Android Keystore and application backups are disabled.
- Camera frames are processed on-device only while scanning a pairing QR.
- The client does not accept arbitrary gateway or media URLs.

Guest Wi-Fi or router client isolation can prevent the phone from reaching the PC.

## Releases

Android and Windows use independent version numbers. Each Android release states the minimum compatible Windows version.
