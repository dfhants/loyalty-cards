# Loyalty Cards

Android app (Kotlin, Jetpack Compose, Material 3 Expressive) for storing loyalty cards.

- Add cards by scanning (CameraX + ML Kit, on-device), from a photo (system photo picker), or by typing the number.
- Quick-pick presets (Tesco Clubcard, Nectar, Boots, Morrisons, Waitrose, Co-op, Lidl Plus, Costa…) set the name and colour. Tesco and Nectar numbers are recognised automatically when scanned.
- Draws Code 128, EAN-13/8, UPC-A/E, Code 39/93, Codabar, ITF, QR, Data Matrix, Aztec and PDF417 (ZXing).
- Choose any card colour. The card screen turns brightness up and keeps the screen on while the barcode is showing.
- Three home-screen widgets (Glance): **Loyalty card** (one card you choose, barcode showing), **Last used card**, and **All cards** (a scrolling list).

Cards are stored only on the phone, in a JSON file in app-private storage.

## Build

```
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleRelease
```

Release builds are signed with the key described in `~/.config/loyalty-cards/signing.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without that file they fall back to the
local debug key, so anyone can build and install the app.

## Licence

MIT. See [LICENSE](LICENSE).
