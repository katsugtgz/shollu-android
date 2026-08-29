# 🎯 Quick Start Guide - Shollu Android CI/CD

## ✅ ALREADY DONE:
- ✅ Created `.github/workflows/android-build.yml` (the single CI/CD workflow — tests, debug APK, signed releases)
- ✅ Updated `app/build.gradle.kts` with release signing
- ✅ Created config example file
- ✅ Added keystore + `app/signing.properties` to `.gitignore`

---

## 🚀 5-MINUTE SETUP TO GO LIVE:

### Step 1: Generate Release Keystore ⚠️ IMPORTANT

**Windows (Git Bash):**
```bash
keytool -genkeypair \
  -v \
  -keystore app/release.keystore \
  -alias shollu-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass your_password_here \
  -keypass your_password_here
```

**Or use PowerShell (no keytool):**
```powershell
# Install OpenJDK first: winget install EclipseAdoptium.Temurin.17.JDK
$JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.x.x"
$keytool = Join-Path $JAVA_HOME "bin\keytool.exe"
& $keytool -genkeypair -v -keystore app/release.keystore -alias shollu-release -keyalg RSA -keysize 2048 -validity 10000 -storepass YOUR_PASSWORD -keypass YOUR_PASSWORD
```

**⚠️ CRITICAL:** The `app/release.keystore` file contains your signing credentials. **DO NOT COMMIT IT TO GIT**.

---

### Step 2: Add Keystore to .gitignore 🔒

Before committing anything, add the keystore to gitignore:

```bash
echo "app/release.keystore" >> .gitignore
echo "app/signing.properties" >> .gitignore
```

This prevents accidentally leaking your signing credentials.

---

### Step 3: Set GitHub Secrets 🔐

Run these commands (replace passwords!):

```bash
# Set keystore as base64
base64 -i app/release.keystore | tr -d '\n' | gh secret set KEYSTORE_BASE64

# Set credentials (you'll be prompted to enter values)
gh secret set SIGNING_KEY_ALIAS
gh secret set SIGNING_STORE_PASSWORD  
gh secret set SIGNING_KEY_PASSWORD
```

**⚠️ IMPORTANT**: These are the exact secret names the CI workflow expects:
- `KEYSTORE_BASE64` - Base64-encoded keystore file
- `SIGNING_KEY_ALIAS` - Keystore alias (e.g., `shollu-release`)
- `SIGNING_STORE_PASSWORD` - Keystore store password
- `SIGNING_KEY_PASSWORD` - Key password

Don't mix up with older variable names!

---

### Step 4: Build Test APK Locally First

Before pushing anything:

```bash
# Test build locally (debug only)
./gradlew assembleDebug

# Check output at:
# app/build/outputs/apk/debug/app-debug.apk
```

Upload to your phone and test!

---

### Step 5: Push & Test CI

```bash
git add .
git commit -m "Setup CI/CD with automatic APK builds"
git push origin main
```

**Check:** https://github.com/katsugtgz/shollu-android/actions

You should see: **Android CI - Build & Release APK** running ✅

After ~3 minutes → Debug APK ready in Artifacts!

---

### Step 6: Create Your FIRST Release 📦

No manual version edit needed — CI derives the version from the tag:
- `versionName` = tag without the `v`
- `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH` (e.g. v3.11.0 → 31100)

Just tag and push:
```bash
git tag v3.11.0
git push origin v3.11.0
```

Tags with a suffix (e.g. `v3.11.0-rc1`) publish as pre-releases.

**Boom!** 🚀 After ~5-8 minutes you'll have:
- ✅ Signed APK (`app-release.apk`)
- ✅ AAB for Play Store (`app-release.aab`)
- ✅ GitHub Release page with download links

---

## 📝 What Happens Each Time

| Action | Result |
|--------|--------|
| **Push to `main`/`develop`** | Debug APK + run tests |
| **Push tag `v*.*.*`** | Signed Release APK + AAB + GitHub Release |
| **Pull Request** | Run tests + debug APK artifact |

---

## 🆘 Troubleshooting

**Tests fail?** Fix them first before releasing!
```bash
./gradlew test --stacktrace
```

**Keystore error?** Ensure all 4 secrets are set correctly:
- `KEYSTORE_BASE64`
- `SIGNING_KEY_ALIAS`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_PASSWORD`

**Version not updating?** Release version comes from the tag, not `build.gradle.kts`: `versionName` = tag minus `v`, `versionCode` = `MAJOR*10000+MINOR*100+PATCH`. Push a new tag (e.g. `v3.11.0`)

**Signing fails?** Gradle resolves signing credentials in this order:
1. `RELEASE_*` environment variables (CI exports these from GitHub Secrets via GITHUB_ENV)
2. `app/signing.properties` (local dev — see "Local Development Signing" below)
3. Built-in defaults (`app/release.keystore`, alias `shollu-release`)

Note: Gradle never loads `gradle.properties.local`.

---

## 💡 Pro Tips

1. **Release versions come from tags** — `v3.11.0` ships versionName 3.11.0 / versionCode 31100 automatically
2. **Test debug APK first** on real device before signed release
3. **AAB needed?** Required for Google Play Store uploads
4. **Draft releases?** Use draft mode for testing before publishing
5. **Never commit `app/release.keystore`** - add it to `.gitignore` immediately after generation

---

## 🔧 Local Development Signing (Optional)

For local testing with a custom signing config, create `app/signing.properties`:

```bash
# Copy the example file
cp app/signing.properties.example app/signing.properties

# Edit app/signing.properties with your credentials (local only!)
# DO NOT COMMIT THIS FILE
```

Note: `app/signing.properties` IS read by Gradle for local builds (environment variables take precedence). The CI system ignores it and uses environment variables from GitHub Secrets only.

---

## 🎯 Ready to Ship?

1. Generate keystore ✓
2. Add keystore to .gitignore ✓
3. Set 4 GitHub secrets ✓
4. Build debug locally ✓
5. Push to main ✓
6. Create tag for release ✓

**Total time: ~15 minutes first time, then instant!**

Need help? Check the workflow logs at:
https://github.com/katsugtgz/shollu-android/actions

