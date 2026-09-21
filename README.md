# Notifling — Android → Windows notification forwarder

Get your phone's notifications as native Windows toasts over your home WiFi.
No cloud, no account, no internet needed — just UDP on your LAN.

```
Phone (Kotlin) --UDP broadcast DISCOVER--> PC (Go)
Phone (Kotlin) --UDP unicast NOTIFY-----> PC (Go) -> Windows toast
```

## How it works

1. The Windows exe listens on UDP `51234` and holds a random 32-byte pairing key in
   `%APPDATA%\Notifling\config.json`.
2. The Android app (`NotificationListenerService`) discovers the PC with a UDP
   broadcast, then forwards each notification as a `NOTIFY` datagram.
3. The PC checks the key hash, drops mismatches/replays, and shows a toast.

Pairing is via QR code (`NOTIFLING:1:<key>`) shown by the PC app, with manual
key entry as fallback. Wire spec: [`docs/protocol.md`](docs/protocol.md).

## Quickstart

**PC**
```powershell
cd windows
go build -o notifling.exe .
.\notifling.exe --show-qr        # generates key + qr.png, opens it for scanning
```

**Phone**
```powershell
cd android
.\gradlew.bat assembleDebug      # APK: app\build\outputs\apk\debug\app-debug.apk
```
Install the APK, **Scan QR** (or paste the key), tap *Enable notification
access*, then *Send test* — a toast should pop on the PC.

**Autostart (login, no admin)**
```powershell
powershell -ExecutionPolicy Bypass -File tools\install-startup.ps1
```
This copies the exe to `%APPDATA%\Notifling`, creates the Startup-folder `.lnk`
(`--minimized`) and a Start Menu shortcut (required for toasts), and adds a UDP
firewall rule (needs one elevated run — rerun as Admin if it warns).

## Project layout

| Path | What |
| ---- | ---- |
| `android/` | Kotlin app (UI, listener service, UDP sender, QR scan). Key in EncryptedSharedPreferences |
| `windows/` | Go exe (UDP listener, toast, QR generation). Key in `%APPDATA%\Notifling\config.json` |
| `tools/install-startup.ps1` | Autostart installer |
| `assets/` | Hand-drawn logo (`icon.png`, `notifling.ico` for shortcuts) |
| `docs/protocol.md` | v1 wire protocol |
| `dist/` | Local builds only (gitignored) |

## Requirements

- Phone + PC on the same WiFi/LAN. Android 8.0+, Windows 10/11.
- Build Android with JDK 17 (Gradle 8.7 cannot run on newer JDKs):
  `$env:JAVA_HOME="<path-to-jdk17>"` (also set `ANDROID_HOME`/`ANDROID_SDK_ROOT`).

## Security

v1 is designed for **trusted home networks**: packets carry only a SHA-256 key
hash (never the key itself), but notification text is plaintext UDP and
replayable by anyone on the same WiFi. Do not use on public networks.
Planned: TCP + HMAC transport without changing the UI.

## Uninstall

Delete `%APPDATA%\Notifling`, the two `Notifling.lnk` shortcuts
(`shell:startup` + Start Menu), and the `Notifling-UDP` firewall rule.

## License

MIT — see [LICENSE](LICENSE).
