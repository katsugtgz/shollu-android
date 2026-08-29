# 🎯 Quick Start Guide - Shollu Android CI/CD

## ✅ ALREADY DONE:
- ✅ Created `.github/workflows/android-build.yml` (CI workflow)
- ✅ Updated `app/build.gradle.kts` with release signing
- ✅ Created config example file

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

---

### Step 2: Set GitHub Secrets 🔐

Run these commands (replace passwords!):

```bash
# Set keystore as base64
base64 -i app/release.keystore | tr -d '\n' | gh secret set RELEASE_KEYSTORE_BASE64

# Set credentials (you'll be prompted to enter values)
gh secret set RELEASE_KEY_ALIAS
gh secret set RELEASE_STORE_PASSWORD  
gh secret set RELEASE_KEY_PASSWORD
```

**⚠️ IMPORTANT**: Store passwords securely! Don't commit them anywhere.

---

### Step 3: Build Test APK Locally First

Before pushing anything:

```bash
# Test build locally
./gradlew assembleDebug

# Check output at:
# app/build/outputs/apk/debug/app-debug.apk
```

Upload to your phone and test!

---

### Step 4: Push & Test CI

```bash
git add .
git commit -m "Setup CI/CD with automatic APK builds"
git push origin main
```

**Check:** https://github.com/katsugtgz/shollu-android/actions

You should see: **Android CI - Build & Release APK** running ✅

After ~3 minutes → Debug APK ready in Artifacts!

---

### Step 5: Create Your FIRST Release 📦

Update version in `app/build.gradle.kts`:
```kotlin
versionCode = 2
versionName = "3.11.0"  // <-- Update this!
```

Create tag:
```bash
git commit -am "Release version 3.11.0"
git tag v3.11.0
git push && git push origin v3.11.0
```

**Boom!** 🚀 After ~5-8 minutes you'll have:
- ✅ Signed APK (`app-release.apk`)
- ✅ AAB for Play Store (`app-release.aab`)
- ✅ GitHub Release page with download links

---

## 📝 What Happens Each Time

| Action | Result |
|--------|--------|
| **Push to `main`** | Debug APK + run tests |
| **Push tag `v*`** | Signed Release APK + AAB + GitHub Release |
| **Pull Request** | Run tests (no APKs) |

---

## 🆘 Troubleshooting

**Tests fail?** Fix them first before releasing!
```bash
./gradlew test --stacktrace
```

**Keystore error?** Ensure all 4 secrets are set correctly

**Version not updating?** Make sure `versionCode` increments each release

---

## 💡 Pro Tips

1. **Always increment `versionCode`** before creating new tag
2. **Test debug APK first** on real device before signed release
3. **AAB needed?** Required for Google Play Store uploads
4. **Draft releases?** Use draft mode for testing before publishing

---

## 🎯 Ready to Ship?

1. Generate keystore ✓
2. Set 4 GitHub secrets ✓
3. Build debug locally ✓
4. Push to main ✓
5. Create tag for release ✓

**Total time: ~15 minutes first time, then instant!**

Need help? Check the workflow logs at:
https://github.com/katsugtgz/shollu-android/actions
