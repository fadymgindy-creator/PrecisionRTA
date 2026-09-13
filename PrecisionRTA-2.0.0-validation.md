# Precision RTA 2.0.0 validation — 13 September 2026

**Release built, signed and installed on the connected Galaxy S25 Ultra. Numerical and bounded phone checks passed. Physical microphone/acoustic accuracy remains unverified.** No reference microphone, adapter or SPL reference was available. This is not a certified measurement instrument.

## Release identity

| Item | Verified result |
| --- | --- |
| APK | PrecisionRTA-2.0.0.apk |
| SHA-256 | `4bf6e0baafed8505b1ba7419efba0f3481a9cd4c308ead207e377d2c80aa95a6` |
| Package / version | app.precisionrta.v2 / 2.0.0 / code 2 |
| Android | min 23, target/compile 34 |
| Permissions | RECORD_AUDIO only; no network permission |
| Signing | Persistent RSA3072 key; v1, v2, v3 verified; zipalign 4-byte check passed |
| Certificate SHA-256 | `41e357d65cc7a5ceec9bddf3cb2c5497c2bfc32398bd03da391b302a2af3c6ee` |
| Contents | AndroidManifest.xml, resources.arsc, launcher/backup resources and classes.dex present; release is not debuggable |
| Installed copy | Phone base.apk SHA-256 equals the file above |

The persistent private key and password are supplied separately in a private backup, excluded from source control. Preserve both for future V2 updates and increment versionCode. V1 was not replaced.

## Reproduced software results

- Real JDK17/SDK34/build-tools34/Gradle8.9/AGP8.7.3 builds passed. The latest recovery fix passed assembleRelease and lintRelease. The wrapper includes the Gradle distribution checksum.
- 16 production-path MeasurementTest tests passed, including direct DFT and independent time-domain Parseval comparisons; 44.1/48 kHz, supported FFT sizes, known tones/gain/band edges, clipping/transients, sample-based averaging, calibration signs/interpolation, raw data preservation, incompatible spatial data, JSON round-trips and atomic recovery. See the numerical contract for exact tolerances.
- Two additional production-path noise ensemble tests passed: worst white-noise band error 0.1120 dB (limit 0.35 dB); pink-noise octave deviation 0.2815 dB (limit 0.6 dB), each over 32 frames. Independent NumPy fixtures compare all 478 bands at two rate/window configurations.
- An original source error was reproduced: a 0.5-peak sine should integrate to -9.0309 dBFS RMS, but original readings changed with fractional resolution (-10.2809 / -4.2603 / +1.7603 dB). Correct Hann power scaling and energy integration replaced it.
- An ideal low-level sine tolerance failed for PCM16 quantization. Independent time-domain calculations established the 0.6033 dB discrepancy. Tests now verify actual quantized sample energy more strictly and preserve the ideal-wave counterexample; the failure was investigated, not hidden by relaxing tolerances.
- Calibration, live analysis and formal capture share the processing path. Fine raw/corrected arrays survive saving; display normalization/smoothing operate on copies. Acquisition now checks actual routing, current stream rate and known invalid-capture conditions. Formal duration counts fresh, non-overlapping samples.

## Connected S25 Ultra results

Tested SM-S938U, Android 16/API36, using the delivered release signature:

- Installation/update, cold launch, microphone access and live capture passed. Actual selected route: built-in microphone id22, mono PCM16, app and reported device stream 48 kHz, VOICE_RECOGNITION. Hardware processing remains unverified; UNPROCESSED was not advertised.
- Formal ambient capture retained 11 complete windows and rejected 29; saved data was labeled Interrupted measurement. These room observations are software smoke data, not an acoustic reference or calibration. Rejection can discard a full 5.46-second window around a disturbance.
- Forced process-stop recovery passed. A newly discovered bug discarded recovery data if the app paused while its recovery prompt was open. Fixed with a pending-recovery guard. The regression then passed through Home/background, a second force-stop and reopening the still-pending prompt.
- Signed instrumentation on the release app passed a known-tone calculation, Android JSON raw/corrected/calibration round-trips and actual recovered fine-data/sample-duration checks (one recovered ambient trace). A 262144-point production calculation took 97.9 ms on that run; this is a bounded timing sample, not a long-term performance guarantee.
- Rotation into landscape, capture resumption, collapsed/expanded controls, labeled scrolling graph settings, capture-details dialog, explicit built-in input selection, named session save/load and restored trace visibility passed. Original free rotation was restored.
- No Precision RTA fatal exception was found in the inspected Android crash buffer. Temporary instrumentation package and UI dump were removed. V2 remains installed with its local smoke-test session; capture was stopped by leaving the app.

## Warnings and untested boundaries

Release lint reported no errors and 25 warnings: one newer-AGP notice, one obsolete SDK guard, 22 English string/localization warnings and one custom touch-view accessibility warning. Single-tap handling calls performClick through the gesture listener; comprehensive TalkBack/gesture accessibility was not validated. The signing tool notes two META-INF build-metadata entries are not covered by the JAR v1 signature; v2/v3 protect the APK on the target phone. No native libraries are packaged. These warnings are not evidence of an audio calculation failure.

No physical frequency-response calibration, SPL accuracy, input gain linearity, hardware resampling/processing, USB microphone switching/unplugging, calls/competing recorder interruptions, long-duration stress or exhaustive multi-touch tests were performed. Emulator tests were not run because the actual target phone became available. No synthetic test substitutes for acoustic validation.

The 1/48 base grid is an FFT estimate. At 48 kHz/262144 its 0.1831 Hz bins and roughly 0.7324 Hz Hann main lobe cannot resolve independent 1/48 detail below about 50.7 Hz. Fine storage is non-destructive at the stored-band level; it is not raw PCM. Absolute SPL remains unavailable without an independent reference. See KNOWN_LIMITATIONS.md and RELEASE-NOTES-2.0.0.md before interpreting results.

## Short Galaxy acceptance procedure

1. Open **Precision RTA 2**. Allow microphone access. Tap the bottom status line and check that it names the microphone you intend to use. Leave calibration off until you have a valid profile and reference.
2. With the phone fixed in place, play steady pink noise quietly enough to avoid clipping. Tap **MEASURE**, wait at least 20 seconds, then **STOP + KEEP**. A full window takes about 5.5 seconds. If most time is rejected, remove disturbances and repeat; do not assume the graph is accurate just because it looks smooth.
3. Save the session, close/reopen the app and load it. Confirm the trace remains. Change graph smoothing and normalization; the saved measurement should remain available without recapturing it. Keep this smoke trace separate from measurements used for tuning.
4. Repeat twice without moving anything. Curves should be reasonably repeatable; unexplained drift or changing response with playback level needs investigation before EQ decisions. This observation alone does not prove accuracy.
5. When the reference microphone, its calibration file and SPL reference are available, compare the same fixed source/position at several safe levels, verify the actual USB route, run the guided reference/phone calibration, then use a fresh verification capture. Keep orientation/case unchanged. Establish SPL only against the independent reading. Report inconsistent results rather than compensating with guessed corrections.
