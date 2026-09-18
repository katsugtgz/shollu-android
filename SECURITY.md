# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 3.10.x  | :white_check_mark: |
| < 3.10  | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability or sensitive issue in Shollu Android, please report it via private GitHub Security Advisory or by creating a private issue.

We take security and privacy seriously:
- Prayer times, location, and schedules stay on-device. No analytics.
- The optional updater checks GitHub Releases (`GET /repos/katsugtgz/shollu-android/releases/latest`) on app launch, throttled to 24h, then downloads the selected APK asset. Downloaded APKs are sha256-checked against the GitHub asset digest and must match the currently installed signing certs before PackageInstaller runs. Failure is fail-closed (no install).
