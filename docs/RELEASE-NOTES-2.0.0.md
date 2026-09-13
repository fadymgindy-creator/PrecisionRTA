# Precision RTA 2.0.0: validated measurement path

This document supersedes measurement/build claims in the originally supplied PDF manual and historical validation report. The application remains a measurement-first Android RTA; EQ optimization remains deferred to V3.

## Changes that affect readings

- Correct Hann-window RMS normalization replaces the original fraction-dependent scale. Digital units are integrated band power relative to full-scale amplitude squared; a full-scale sine is -3.01 dBFS RMS when its energy is integrated. Frequency-corrected digital values are explicitly labeled. They are not absolute SPL.
- Display smoothing sums band energy using fractional logarithmic overlap. A wider band generally contains more noise energy. REL normalizes after display smoothing; changing normalization or graph smoothing never edits saved fine data.
- Formal measurements acquire fresh, complete, non-overlapping 262144-sample windows. At 48 kHz the first window takes 5.46 seconds. Stop preserves complete accepted windows; an unfinished final window is omitted. Counters use captured samples rather than UI timing. Live mode can use smaller/overlapping windows.
- Original and frequency-corrected 1/48-octave base data are saved, along with stream/input/calibration/orientation/case metadata and a calibration snapshot. This supports later re-analysis but does not recover detail finer than the stored base bands.
- Near clipping, long runs of digital zero, and conspicuous within-window transients reject the affected formal window. This can discard substantial audio around a brief disturbance. The detector does not identify sounds and cannot guarantee removal of every disturbance. Capture under steady conditions and inspect kept/rejected time.
- 1/48 octave is an FFT-based band estimate, not a standards-certified fractional-octave filter bank. At 48 kHz/262144, bins are 0.1831 Hz apart, Hann equivalent noise bandwidth is about 0.2747 Hz and its main lobe is about 0.7324 Hz wide. Bass detail below approximately 50.7 Hz cannot be independently resolved at 1/48 octave. Smaller live FFTs have higher limits; the graph shows them.
- Very low digital levels have PCM16 quantization limits. One tested three-count-peak sine differed by 0.6033 dB from an ideal continuous sine even though the FFT accurately measured the quantized samples. A warning appears below -70 dBFS broadband; this is not a per-band accuracy guarantee.

## Capture and calibration

Tap the bottom status text for actual routed device, app/device stream rates, source, calibration and resolution limits. A requested device is not accepted as proof of routing: the running recorder's actual route is checked. Route changes, silencing, read failures and detected backlog stop capture. Restart after correcting the condition. Undetectable device DSP, resampling and lost samples still require physical validation.

On the tested S25 Ultra, Android selected the built-in microphone at 48 kHz using VOICE_RECOGNITION. UNPROCESSED was not advertised. Hardware signal processing remains unverified; a frequency-response calibration cannot correct unknown changing gain or nonlinear processing.

Use INPUT → Case / microphone setup notes to record case on/off and positioning. Keep input, source, orientation, case and playback unchanged during a measurement. Rotation or leaving the app stops formal capture and preserves completed accepted data. Calibration runs cancel if the app leaves the foreground.

Import manufacturer error curves with “subtract values”; import correction curves with “add values.” Duplicate/nonfinite coordinates are rejected. Corrections interpolate in logarithmic frequency and hold their endpoint values outside the file's frequency range, so use a file covering the intended range. The app warns about setup mismatches; review the active profile.

Reference-match Auto Cal now averages six full windows (about 33 seconds per microphone at 48 kHz), using the same production DSP as the analyzer. Corrections are derived at 1/12 octave to reduce noise-fitting and normalized across 500–2000 Hz. Unreliable corrections above ±24 dB are rejected instead of silently clipped. A fresh verification pair reports normalized 1/12-octave RMS disagreement; it is not an uncertainty certificate.

SPL requires a current, unclipped, dominant 1 kHz tone and an independent reference meter reading. The resulting estimate is bound to the input, frequency-calibration identity, orientation and case notes. Setup changes invalidate the live SPL reference. Saved traces retain their own reference; traces without one are hidden in SPL mode. This is an unweighted band SPL estimate, not a certified sound-level meter.

Spatial averages require compatible input/calibration/sample rate/FFT/orientation/case/units. Selected source measurements receive equal energy weight and remain unchanged. Incompatible traces are refused rather than interpolated or relabeled silently.

## Saving and recovery

STOP + KEEP retains a trace in the temporary session. SESSION → Save current session writes a named session. Recovery checkpoints include completed in-progress data. Atomic writes retain a previous valid copy. The recovery prompt preserves existing data until you explicitly recover or discard it, including if the app is interrupted while the prompt is open.

The updated session format rejects unknown legacy level units instead of treating old scaled data as comparable. V1 remains separately installed under its original package.

## Signing and future updates

The V2 release key is persistent and private, separate from source. Keep the supplied signing backup private and backed up. Future V2 updates must use that same identity and a larger versionCode. Losing the key prevents an in-place update. The repository's CI builds test/debug and unsigned release outputs; it never invents a new release key.
