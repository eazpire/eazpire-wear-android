# Eazpire Wear Player (Android)

Phone app for the Eazpire Wear player hub — mirrors [wear-web](../wear-web/) tabs.

## Modules

- `:app` — Compose Material3 UI (`com.eazpire.wear`)
- `:wear-core` — shared library (via `../wear-core`)

## Build

```powershell
.\scripts\wear-android\build-wear-android.ps1
.\scripts\wear-android\build-wear-android.ps1 -Install
```

```bash
cd wear-android
./gradlew assembleDebug
```

## Auth

Shopify Customer Account OAuth PKCE with callback `shop.73952035098.eazpire://wear-callback`.

After login, JWT is pushed to the Wear OS companion via Data Layer path `/eaz/wear/auth`.

## Tabs

Hub · Feed · Verify · Squad · Vault · Move (Health Connect step sync)

See [WEAR_PRODUCT_MAP.md](../docs/project/WEAR_PRODUCT_MAP.md).
