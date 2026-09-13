package app.precisionrta.v2;

final class RealFft {
    private RealFft() {}

    static void fft(double[] real, double[] imag) {
        final int n = real.length;
        if(n<2||(n&(n-1))!=0||imag.length!=n)throw new IllegalArgumentException("FFT arrays must have equal power-of-two length");
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) {
                double tr = real[i]; real[i] = real[j]; real[j] = tr;
                double ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            double wLenR = Math.cos(angle);
            double wLenI = Math.sin(angle);
            int half = len >> 1;
            for (int i = 0; i < n; i += len) {
                double wr = 1.0;
                double wi = 0.0;
                for (int k = 0; k < half; k++) {
                    int a = i + k;
                    int b = a + half;
                    double br = real[b] * wr - imag[b] * wi;
                    double bi = real[b] * wi + imag[b] * wr;
                    double ar = real[a];
                    double ai = imag[a];
                    real[a] = ar + br;
                    imag[a] = ai + bi;
                    real[b] = ar - br;
                    imag[b] = ai - bi;
                    double nwr = wr * wLenR - wi * wLenI;
                    wi = wr * wLenI + wi * wLenR;
                    wr = nwr;
                }
            }
        }
    }
}
