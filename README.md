# Precision RTA 2

Android magnitude-response analyzer for car-audio measurements. Package `app.precisionrta.v2`, version 2.0.0 (code 2), Android 6+; V1 remains separate. Only RECORD_AUDIO is requested; no network permission.

Three independently configurable graphs retain zoom/presets, temporal averaging, 1/3 through 1/48 display smoothing, cursor and maximize/reset gestures. Formal captures preserve raw and frequency-corrected fine band data, sample-based kept/rejected time, input metadata and calibration snapshots. Saved traces, compatible equal-energy spatial averages, named sessions and interruption recovery are supported. Manufacturer calibration import, reference-match Auto Cal and separately referenced SPL remain available. EQ/targets/phase optimization are deferred to V3.

**Read [release notes](docs/RELEASE-NOTES-2.0.0.md) and [validation report](docs/VALIDATION-REPORT.md) before trusting measurements.** Independent numerical tests and connected S25 Ultra smoke tests passed within the documented scope. Physical microphone accuracy and acoustic calibration remain unverified. A 1/48 base grid does not establish independent 1/48 resolution across the entire audio range. The old source package's normalization was corrected; its historical accuracy claims are superseded.

## Reproduce the build

Use JDK 17, Android SDK platform 34 and build-tools 34.0.0. Set ANDROID_HOME and JAVA_HOME. The included wrapper pins Gradle 8.9 and verifies its distribution SHA-256; AGP is 8.7.3.

```sh
bash ./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease
```

On Windows use `./gradlew.bat` instead. Release output is `build/outputs/apk/release/PrecisionRTA2-release-unsigned.apk`. This is unsigned until the external signing step. CI performs the same validation and publishes unsigned artifacts. `bash build.sh` delegates to the wrapper and never creates keys. Independent NumPy fixtures are committed; `tests/generate_oracle.py` regenerates them with NumPy.

## Release signing

On Windows with SDK build-tools 34.0.0 and JDK 17:

```powershell
./sign-release.ps1 -SigningConfig C:/private/release-secret.json -Apk build/outputs/apk/release/PrecisionRTA2-release-unsigned.apk -OutputApk C:/releases/PrecisionRTA-2.0.0.apk
```

The external JSON holds `keystore` (relative filename), `alias` and `password`. It and the private keystore must remain outside source control. Use the existing V2 key for updates; never generate a replacement. Increment versionCode for future releases. Public certificate SHA-256: `41e357d65cc7a5ceec9bddf3cb2c5497c2bfc32398bd03da391b302a2af3c6ee`.

## Evidence and documentation

- [Validation and Galaxy acceptance procedure](docs/VALIDATION-REPORT.md)
- [Pipeline, independent expectations and investigated discrepancies](docs/MEASUREMENT-VALIDATION.md)
- [Updated operating notes](docs/RELEASE-NOTES-2.0.0.md)
- [Known limits](KNOWN_LIMITATIONS.md)

The original PDF manual is retained as historical interface guidance. Updated release notes supersede its measurement and validation claims.
