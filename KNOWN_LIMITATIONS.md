# Measurement limits

- Software tests validate calculations on supplied PCM. They do not establish microphone response, gain linearity, hardware ADC rate or absence of Android processing. The tested S25 Ultra reports VOICE_RECOGNITION at 48 kHz; UNPROCESSED was not advertised. No reference microphone or SPL meter was available.
- Digital band RMS values are not absolute SPL. Frequency correction does not establish level calibration. SPL is a user-referenced, unweighted band estimate tied to input/calibration/orientation/case, and is not certified.
- Formal 1/48 base capture uses 262144 samples (5.46 seconds at 48 kHz). Its Hann main lobe cannot independently resolve 1/48 detail below about 50.7 Hz. This is an FFT band estimate, not an IEC fractional-octave filter bank. Smaller live windows have higher limits.
- Saved fine bands preserve raw/corrected energy, not PCM or individual FFT bins. Display smoothing uses a within-band logarithmic energy distribution approximation. It cannot recover finer information than the saved bands.
- PCM16 quantization limits very low signals. Broadband warnings do not guarantee individual-band accuracy. Transient/clipping/dropout detection is conservative, may reject useful windows, and cannot detect every disturbance or hardware loss.
- Route, silencing and app queue checks stop known invalid capture, but manufacturer DSP, resampling and undetectable missing samples require physical investigation. Long-duration stress, competing microphone apps/calls and external USB routing were not tested.
- Auto Cal uses sequential reference/phone recordings: source or placement changes become calibration error. Curves hold endpoint corrections outside their frequency range; use appropriate files. Synthetic tests do not validate a physical calibration workflow.
- Interrupted incomplete formal windows are omitted. Recovery preserves completed accepted windows. Legacy traces with unknown units are rejected rather than silently compared with corrected V2 measurements.
- V2 measures magnitude only, with no impulse/phase/timing reference. Targets, Auto EQ, crossover/phase optimization and Poweramp export remain deferred to V3.
