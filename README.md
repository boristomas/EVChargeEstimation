# EV Charge

Android app that estimates how long until your EV reaches the charge you want.

Works offline for estimates, scan, history, and timers. Optional internet is only used to **check GitHub for app updates**.

**Repo:** https://github.com/boristomas/EVChargeEstimation  

## Install (users)

1. Open [Releases](https://github.com/boristomas/EVChargeEstimation/releases/latest)  
2. Download the `.apk`  
3. Open it and install (allow “Install unknown apps” if asked)  

After install, the app can **check for updates** itself (banner + “Check for updates” on the home screen). You still confirm the install when a new APK is ready — Android does not allow silent sideload updates.

## Publish a new version (you)

### One-time: GitHub Actions secrets

In the GitHub repo → **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
|--------|--------|
| `KEYSTORE_BASE64` | Base64 of `keystore/evcharge-release.jks` |
| `KEYSTORE_PASSWORD` | From your local `keystore.properties` |
| `KEY_ALIAS` | `evcharge` |
| `KEY_PASSWORD` | From your local `keystore.properties` |

Encode the keystore (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\evcharge-release.jks")) | Set-Clipboard
```

(Paste into the `KEYSTORE_BASE64` secret.)

**Back up** `keystore/evcharge-release.jks` and `keystore.properties` offline. Losing them means you cannot ship updates that install over the same app id.

### Ship a release

1. Bump in `app/build.gradle.kts`:
   - `versionCode` → **+1** every release (required for Android)
   - `versionName` → e.g. `1.0.1`
2. Commit and push to `main`
3. Tag and push:

```bash
git tag v1.0.1
git push origin main --tags
```

4. GitHub Actions builds a **signed APK** and attaches it to the Release  

Or run **Actions → Release APK → Run workflow** and enter version name/code.

### Local release build

```bat
gradlew.bat packageDist
```

Output: `dist/EVChargeEstimation-<version>.apk`

## Privacy

- No account, no ads  
- Charge math, history, scan, and reminders work **without** internet  
- Internet is used only if you (or auto-check) look for updates on GitHub  

## Requirements

- Android 9+  
- Camera optional  
