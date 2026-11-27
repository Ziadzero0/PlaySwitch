# Changelog

## [1.0.0-beta.2]

### Fixed
- **Android 15 Compatibility:** Prevented `SQLiteCompatibilityWalFlags` from querying `Settings.Global` inside the unattached root `app_process`, resolving `Snapshot failed` (`SecurityException`) on Android 15 devices. Fix contributed by [@midopop-ux](https://github.com/midopop-ux) ([PlaySwitch-A15](https://github.com/midopop-ux/PlaySwitch-A15)).
- **Health Diagnostics:** Show the actual snapshot or environment check failure when the root daemon is already connected instead of reporting `"Root shell daemon not connected"`.

### Tests
- Added unit test verifying `SqliteCompatFixer.apply()` is idempotent and safe in JVM unit tests.

## [1.0.0-beta.1]

- Initial public beta release of Play Switch.
