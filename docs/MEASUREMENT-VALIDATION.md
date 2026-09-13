# Measurement contract and test plan

Written before changing the measurement implementation. The imported audit is not evidence of accuracy. Synthetic tests verify software, never the Galaxy S25 Ultra microphone, adapter, acoustic field, or factory calibration.

## Pipeline and starting defects

AudioRecord mono PCM16 -> ring snapshot -> symmetric Hann -> complex FFT -> calibration power gain -> fractional bands -> callback -> temporal energy average -> trace JSON -> display normalization/rebanding/average -> graph.

The original Auto Cal used a separate unnormalized FFT path. Both paths ignored preferred routing failure and assumed requested sample rate. Negative reads were ignored. Overlapping windows were equally counted using capped UI callback wall time. Input/calibration could change during capture. Spatial averaging interpolated different inputs and attached the current microphone's metadata. JSON writes could truncate crash recovery. Display smoothing averaged powers and failed to clear temporal history when a frequency grid changed without changing length. SPL was based on one arbitrary fine band before the calibration dialog, with no input binding. These are testable integrity hazards.

## Numerical contract

- PCM16 is signed, divided by 32768; -32768 represents -1, +32767 is below +1. Sample magnitude >=32760 is flagged near clipping. Silence must not become NaN or infinity.
- Spectrum units: integrated mean-square power in each band relative to digital full-scale amplitude squared (1). A full-scale sine integrated over its leakage is -3.0103 dBFS RMS. A 0.5-peak sine is -9.0309 dBFS RMS. Frequency correction makes these corrected digital levels, not raw dBFS or acoustic SPL.
- With w[i]=0.5-0.5*cos(2*pi*i/(N-1)), one-sided power P[k]=c[k]*|FFT(x*w)[k]|^2/(N*sum(w^2)), c=2 except DC/Nyquist where c=1. Parseval total equals sum(x^2*w^2)/sum(w^2). Remove sample mean to reject DC; compare the same centered input in the oracle.
- Fractional-octave bands use exact base-2 centers 1000*2^(j/fraction). Integrate power over bin cells using fractional Hz overlap, including partially crossed band edges; no fraction-dependent arbitrary gain. This is an FFT band estimator, not an IEC filter bank.
- Store the 1/48 base spectrum and raw uncorrected base spectrum separately when calibration is applied. Display rebanding allocates source-band energy according to logarithmic overlap (an explicit within-band approximation). Sum, never average, integrated band energies. Normalization only transforms copies.
- Formal capture uses only newly acquired, non-overlapping complete 262144-sample blocks. Elapsed accepted/rejected seconds are sample counts/sample rate. An interrupted partial block is not represented as a complete measurement. This necessary 5.46 s window at 48 kHz is an acquisition limit, not a quality gate. Live display can overlap; formal capture cannot reuse samples.
- Clipped blocks are rejected. Transient detection examines short-time powers inside a block and compares recent levels conservatively; sustained new levels must not be rejected forever. No detector guarantees removal of all coughs/knocks.
- Spatial averaging is equal energy weight per selected measurement. Incompatible units, sample rate, calibration identity, input path, orientation or frequency grids are rejected with an explanation. Source traces remain intact.
- Phone/reference matching shares the production FFT path. Align frequency grids explicitly; reference minus phone corrections normalized over 500–2000 Hz. Reject malformed/nonfinite/duplicate calibration coordinates. Manufacturer error values are subtracted, explicit correction values added.

## Independent expected results and tolerances

1. FFT against direct O(N^2) DFT for N=8,32,128: absolute complex-bin error <1e-9. Invalid non-power-of-two arrays must fail promptly.
2. PCM and Hann power against independent time-domain weighted energy: relative error <1e-8 (absolute <1e-12 near zero). Test silence, DC, impulses, deterministic random PCM, and Nyquist alternating samples.
3. Analytic tones at 44.1/48 kHz, all supported FFT sizes, several phases and off-bin frequencies: integrated tone power within 0.05 dB at amplitudes >=0.01; 0.5 dB at 0.0001 due to PCM quantization. Level increments of x2 peak produce 6.0206 +/-0.02 dB. Edge tests conserve integrated energy across adjacent bands; narrow individual-band leakage is documented, not forced to match an ideal filter.
4. Independent Python/NumPy rFFT + continuous bin-cell integration compared with production output on deterministic PCM: per-band error <1e-6 dB above -140 dBFS. Verify white-noise/pink-noise shapes using ensemble averaging, not one random periodogram.
5. +6 dB calibration adds 6 +/-1e-8 dB to every non-floor band and preserves raw data. Log interpolation at geometric midpoint equals midpoint correction to 1e-10 dB. Calibration JSON round-trips to 1e-12 relative precision.
6. Synthetic uniform energy per log interval rebands with target/source bandwidth ratio, within 1e-8 dB. Resmoothing and normalization leave original arrays byte-for-byte unchanged.
7. Known equal-duration powers 1 and .01 average to .505; unequal durations 1 and 3 average to .2575. Relative tolerance 1e-10. Clipping/transient rejection and sustained level transitions have exact accepted/rejected counts; times reflect samples, not test execution speed.
8. Sessions/profiles round-trip with metadata and exact doubles; truncated/corrupt/invalid input is rejected, interrupted writes preserve the last valid file. No mismatched silent spatial average.

## Resolution limits

At 48 kHz/N=262144: bin spacing 0.183105 Hz, Hann ENBW approximately 0.27466 Hz, null-to-null main lobe approximately 0.73242 Hz. A 1/48 band at 20 Hz is only 0.28881 Hz wide. Closely spaced bass features below about 51 Hz cannot be resolved as independent 1/48-octave detail by this window; there is no claim of true 1/48 filter-bank performance across 20 Hz–20 kHz. Other FFT sizes and rates must report their own limits.

## Physical validation boundary

## Investigated discrepancy: PCM quantization

The initially proposed 0.5 dB ideal-tone tolerance at peak amplitude 0.0001 was disproved: 15,000.1 Hz at 48 kHz, N=16384 and phase .71 produces -83.6136103687644 dBFS after PCM16 quantization, versus -83.01029995663981 dBFS for the ideal continuous sine. Independent NumPy time-domain integration agrees with production to better than 1e-8 dB. This is a 3.28-count peak signal, not an FFT normalization defect. The low-level test now checks the actual quantized time-domain energy with a stricter relative tolerance (1e-8), and retains the failing ideal-wave counterexample as a permanent limitation test. The ideal-tone claim was withdrawn rather than widening it until the test passed. The app warns when broadband digital power is below -70 dBFS. Band-specific noise/quantization uncertainty can occur even with a higher broadband level.

## Physical validation boundary (continued)

Android reports the app's configured stream rate; that does not establish hardware ADC rate, absence of resampling, microphone response, gain linearity or AGC. Request UNPROCESSED when advertised, otherwise VOICE_RECOGNITION; display the actual source/route and an unverified processing warning. Monitor route changes, explicit read failures, app queue overruns and silencing where the OS exposes it. Undetectable hardware drops and DSP remain a physical-test limitation. SPL is a user-referenced estimate bound to its input/calibration path; no certified accuracy is implied.

Official sources: [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord), [Android recording guidance](https://developer.android.com/media/platform/mediarecorder), [AGP 8.7 toolchain compatibility](https://developer.android.com/build/releases/agp-8-7-0-release-notes).
