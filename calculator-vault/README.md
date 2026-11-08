# Calculator Vault

An Android app that looks and works like an ordinary calculator, but hides a
private, encrypted vault for your own files and photos behind a secret PIN.
It can also connect to network storage (SMB / WebDAV) and pull files down into
the vault — similar to mapping a network drive on Windows, but on your phone.

> This is a personal privacy tool for protecting **your own** files on **your
> own** device. "Calculator vault" apps are a common, legitimate privacy
> category. Use it responsibly and within the law.

## How it works

### The disguise
The launcher shows a fully functional calculator (`+ − × ÷ %`, decimals, sign
toggle). Nothing hints at the vault.

### The secret unlock
- Type your PIN (4–12 digits, no operators) and press **=**.
- **First launch:** the first code you enter and confirm becomes your PIN.
- **Wrong code:** it just behaves like a normal calculation — no hint that a
  vault exists.
- Change the PIN later from the vault's overflow menu.

### The vault
Three tabs:
- **Files** — import any file via the system picker; export or delete.
- **Photos** — hidden photos/videos in a grid; tap an image for the built-in
  viewer.
- **Network** — save SMB or WebDAV connections, browse them, and download
  files straight into the encrypted vault.

## Security design

| Concern | Approach |
|---|---|
| File contents | AES-256-GCM, streamed, per-file random IV |
| Encryption key | Android Keystore (hardware-backed where available), never leaves the device |
| PIN | PBKDF2-HMAC-SHA256 hash + random salt, stored in `EncryptedSharedPreferences` |
| Metadata index | Encrypted JSON inside app-private storage |
| Connection credentials | Stored in `EncryptedSharedPreferences` |
| Screenshots / Recents | `FLAG_SECURE` on vault screens (toggle in menu) |
| Cloud/device backup | Vault data excluded from backup & transfer |

All vault data lives in **app-private internal storage**, invisible to other
apps and the gallery. Images are decrypted **in memory** for viewing — no clear
copy is written to disk.

## Build & run

Requires Android Studio (Giraffe+) or the Android SDK with JDK 17.

```bash
cd calculator-vault
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

- `minSdk` 26, `targetSdk` 34, Kotlin, ViewBinding.
- Open the folder directly in Android Studio to build/run on a device.

## Network storage notes

- **SMB** (jcifs-ng, SMB2/SMB3): enter server host/IP, share name, and
  optional username / password / domain. Works with Windows shares and most NAS
  devices.
- **WebDAV** (OkHttp): enter the full base URL (e.g.
  `https://host/remote.php/dav/files/you`) plus optional Basic-Auth
  credentials. Works with Nextcloud/ownCloud, Apache `mod_dav`, etc.
- The device and the share must be reachable on the same network (or via VPN).

## Project layout

```
app/src/main/java/com/vault/calc/
  crypto/     SettingsManager (PIN), VaultCrypto (AES-GCM, Keystore)
  data/       VaultItem, VaultRepository (encrypted store + index)
  network/    RemoteStorage + SMB and WebDAV clients, ConnectionStore
  ui/         CalculatorActivity, VaultActivity, fragments, adapters, viewer
  util/       CalculatorEngine, BitmapUtils
```

## Limitations / ideas for later

- Video playback opens via export rather than an in-app player.
- Optional biometric unlock, decoy/duress PIN, and upload-to-share could be
  added.

<!-- CI: debug APK собирается через GitHub Actions (.github/workflows/android-build.yml) -->

<!-- rebuild 084235 -->
