# Eazpire Wear Player (Android)

Phone app for the Eazpire Wear player hub — mirrors [wear-web](../wear-web/) tabs.

## Modules

- `:app` — Compose Material3 UI (`com.eazpire.wear`)
- `:wear-core` — shared library (via `../wear-core`)

## Build

### Android Studio (empfohlen)

1. **Ordner öffnen:** `wear-android/` im Monorepo (Pfad mit `wear-core` als Nachbarordner)
2. **Einmalig SDK-Pfade setzen** (wegen Modul `../wear-core`):

```powershell
.\scripts\wear-android\setup-wear-android-studio.ps1
```

3. Android Studio → **File → Sync Project with Gradle Files**

> **Nicht** nur das GitHub-Mirror-Repo `eazpire-wear-android` klonen ohne Setup — `local.properties` ist gitignored.  
> Fehler *„SDK location not found“* → Setup-Script ausführen oder in **wear-android/** und **wear-core/** je `local.properties` mit `sdk.dir=...` anlegen.

### CLI

```powershell
.\scripts\wear-android\build-wear-android.ps1
.\scripts\wear-android\build-wear-android.ps1 -Install
```

```bash
cd wear-android
./gradlew assembleDebug
```

## Auth

Shopify Customer Account OAuth PKCE with callback `shop.73952035098.eazpire://callback` (same URI as Creator app; both apps may compete on the same device).

After login, JWT is pushed to the Wear OS companion via Data Layer path `/eaz/wear/auth`.

## Tabs

Hub · Feed · Verify · Squad · Vault · Move (Health Connect step sync)

See [WEAR_PRODUCT_MAP.md](../docs/project/WEAR_PRODUCT_MAP.md).
