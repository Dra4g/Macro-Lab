package macrolab;

import java.util.Arrays;
import java.util.Objects;

public final class Stats {
    private Stats() {}
    public record Correlation(double value,
                              int count) {}

    public static double[] changes(final double[] values,
                                   final boolean rate) {
        final double[] result = new double[values.length];
        Arrays.fill(result, Double.NaN);
        for (int i = 1; i < values.length; i++) {
            if (Double
                    .isFinite(values[i])
                    && Double
                    .isFinite(values[i - 1])) {
                /* NON saltare i buchi: gennaio -> marzo non è un rendimento mensile.
                   Tasso 3.00 -> 3.25 = +0.25 punti; prezzo 100 -> 102 = +2%. */
                if (rate)
                    result[i] = values[i] - values[i - 1];
                else if (values[i - 1] > 0)
                    result[i] = 100 * (values[i] / values[i - 1] - 1);
            }
        }
        return result;
    }

    public static Correlation correlate(final double[] x,
                                        final double[] y,
                                        final int from,
                                        final int to,
                                        final int lag,
                                        final boolean spearman) {
        Objects.checkFromToIndex(from, to, x.length);
        Objects.checkFromToIndex(from, to, y.length);
        if (lag <= from - to
                || lag >= to - from) return new Correlation(Double.NaN, 0);

        if (spearman) {
            /* I ranghi vanno rifatti sulle COPPIE valide, non sulla serie intera.
               [1, 2, NaN, 4] contro [5, 7, 8, 9] -> tre coppie, tre ranghi. */
            final double[] a = new double[to - from];
            final double[] b = new double[a.length];
            int n = 0;
            for (int t = from; t < to; t++) {
                final int j;
                if ((j = t + lag) >= from
                        && j < to
                        && Double.isFinite(x[t])
                        && Double.isFinite(y[j])) {
                    a[n] = x[t];
                    b[n++] = y[j];
                }
            }
            return new Correlation(n < 3
                    ? Double.NaN
                    : pearson(ranks(a, n),
                    ranks(b, n), n), n);
        }

        /* Pearson legge gli array originali: niente due copie a ogni finestra.
           Aggiornamento centrato, stesso ordine delle coppie del percorso a ranghi.
           Con n=2 e x=[1,3]: media=2, somma degli scarti al quadrato=2. */
        int n = 0;
        double mx = 0, my = 0, xx = 0, yy = 0, xy = 0;
        for (int t = from; t < to; t++) {
            final int j;
            if ((j = t + lag) >= from
                    && j < to
                    && Double.isFinite(x[t])
                    && Double.isFinite(y[j])) {
                final double dx = x[t] - mx;
                final double dy = y[j] - my;
                mx += dx / ++n;
                my += dy / n;
                xx += dx * (x[t] - mx);
                yy += dy * (y[j] - my);
                xy += dx * (y[j] - my);
            }
        }
        return new Correlation(
                n < 3
                        || xx <= 0
                        || yy <= 0
                        ? Double.NaN
                        : Math.clamp(
                                xy / Math.sqrt(xx) / Math.sqrt(yy),
                        -1,
                        1),
                n);
    }

    static double pearson(final double[] a,
                          final double[] b,
                          final int n) {
        double mx = 0, my = 0, xx = 0, yy = 0, xy = 0;
        for (int i = 0; i < n; i++) {
            final double dx = a[i] - mx;
            final double dy = b[i] - my;
            mx += dx / (i + 1);
            my += dy / (i + 1);
            xx += dx * (a[i] - mx);
            yy += dy * (b[i] - my);
            xy += dx * (b[i] - my);
        }
        if (xx <= 0
                || yy <= 0)
            return Double.NaN;
        return Math.clamp(
                xy / Math.sqrt(xx) / Math.sqrt(yy),
                -1,
                1);
    }

    static double[] ranks(final double[] values,
                          final int n) {
        final double[] sorted = Arrays.copyOf(values, n);
        final double[] result = new double[n];
        Arrays.sort(sorted);
        for (int i = 0; i < n; i++) {
            int low = 0, high = n;
            while (low < high) {
                final int mid;
                if (sorted[mid = (low + high) >>> 1] < values[i])
                    low = mid + 1;
                else
                    high = mid;
            }
            final int first = low;
            high = n;
            while (low < high) {
                final int mid;
                if (sorted[mid = (low + high) >>> 1] <= values[i])
                    low = mid + 1;
                else high = mid;
            }
            /* low è il limite escluso. Due valori uguali ai posti 0 e 1:
               rango medio = (0 + 2 + 1) / 2 = 1.5.
               first conserva il limite prima della seconda ricerca. */
            result[i] = (first + (double) low + 1) * 0.5;
        }
        return result;
    }

    public static double[] standardize(final double[] values) {
        double mean = 0, sum = 0;
        int n = 0;
        for (final double value : values) {
            if (Double.isFinite(value)) {
                final double delta = value - mean;
                mean += delta / ++n;
                sum += delta * (value - mean);
            }
        }
        final double[] out = new double[values.length];
        final double sd = n > 1
                ? Math.sqrt(sum / (n - 1))
                : 0;
        for (int i = 0; i < out.length; i++)
            out[i] = sd > 0
                    ? (values[i] - mean) / sd
                    : Double.NaN;
        return out;
    }
}
