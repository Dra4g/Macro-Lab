package macrolab;

import java.util.ArrayList;
import java.util.List;

public final class PatternStudy {
    private PatternStudy() {}

    public static final int[] MARKETS = {4, 0, 5};
    public enum Kind {
        DISINFLATION_REBOUND("Calo HICP → discesa e recupero", -1, true),
        INFLATION_SELLOFF("Salita HICP → discesa mercati", 1, false);

        final String label;
        final int direction;
        final boolean recoveryRequired;
        Kind(final String label, final int direction, final boolean recoveryRequired) {
            this.label = label;
            this.direction = direction;
            this.recoveryRequired = recoveryRequired;
        }
        public String toString() { return label; }
    }
    public enum Outcome {
        CONFIRMED("Confermato"),
        REJECTED("Non confermato"),
        PENDING("In attesa"),
        MISSING("Dati insufficienti");
        final String label;
        Outcome(final String label) {
            this.label = label;
        }
        public String toString() {
            return label;
        }
    }

    public record Rules(int inflationMonths,
                        double inflationDrop,
                        int declineMonths,
                        double marketDrop,
                        int recoveryMonths,
                        double rebound,
                        Kind kind) {
        public Rules(final int inflationMonths, final double inflationDrop, final int declineMonths,
                     final double marketDrop, final int recoveryMonths, final double rebound) {
            this(inflationMonths, inflationDrop, declineMonths, marketDrop, recoveryMonths, rebound, Kind.DISINFLATION_REBOUND);
        }
        public Rules {
            if (kind == null
                    || inflationMonths < 1
                    || declineMonths < 0
                    || recoveryMonths < 1
                    || !Double.isFinite(inflationDrop)
                    || inflationDrop <= 0
                    || !Double.isFinite(marketDrop)
                    || marketDrop <= 0
                    || marketDrop >= 100
                    || !Double.isFinite(rebound)
                    || rebound <= 0)
                throw new IllegalArgumentException("Soglie del pattern non valide");
        }

        public int horizonMonths() { return declineMonths + (kind.recoveryRequired ? recoveryMonths : 0); }
    }

    public record Leg(int market, int trough, int recovery, double fall, double bounce, Outcome outcome) {}
    public record Event(int start, int signal, double inflationFall, List<Leg> legs, Outcome outcome, Kind kind) {
        public double inflationChange() { return kind.direction * inflationFall; }
    }

    public static List<Event> detect(final double[][] levels,
                                     final Rules rules) {
        if (levels.length <= 5) throw new IllegalArgumentException("Serie richieste mancanti");
        final double[] inflation = levels[2];
        for (final double[] series : levels) {
            if (series.length != inflation.length)
                throw new IllegalArgumentException("Calendari diversi");
        }
        final List<Event> events = new ArrayList<>();
        boolean episode = false;
        for (int t = rules.inflationMonths(); t < inflation.length; t++) {
            final int start = t - rules.inflationMonths();
            boolean valid = true;
            for (int m = start; m <= t; m++) {
                if (!Double.isFinite(inflation[m])) { valid = false; break; }
            }
            if (!valid) { episode = false; continue; }
            /* 4.0% -> 2.5% = -1.5 PUNTI; 2.5% -> 4.0% = +1.5 PUNTI.
               Una sola soglia attraversata per episodio contiguo; il segnale è
               il primo mese che la raggiunge. NON scegliere a posteriori il più bello. */
            final double fall = rules.kind().direction * (inflation[t] - inflation[start]);
            if (fall < rules.inflationDrop()) { episode = false; continue; }
            if (episode) continue;
            episode = true;
            final List<Leg> legs = new ArrayList<>(MARKETS.length);
            boolean missing = false, pending = false, rejected = false;
            for (final int market : MARKETS) {
                final Leg leg = evaluate(levels[market], market, start, t, rules);
                legs.add(leg);
                missing |= leg.outcome() == Outcome.MISSING;
                pending |= leg.outcome() == Outcome.PENDING;
                rejected |= leg.outcome() == Outcome.REJECTED;
            }
            events.add(new Event(start, t, fall, List.copyOf(legs),
                    rejected ? Outcome.REJECTED : missing ? Outcome.MISSING : pending ? Outcome.PENDING : Outcome.CONFIRMED, rules.kind()));
        }
        return List.copyOf(events);
    }

    static Leg evaluate(final double[] prices,
                        final int market,
                        final int start,
                        final int signal,
                        final Rules rules) {
        final int declineEnd = signal + rules.declineMonths();
        final int horizon = signal + rules.horizonMonths();
        /* La stessa finestra completa è richiesta anche per gli insuccessi.
           Nessuno zero al posto dei buchi. Se manca la coda, il caso resta aperto;
           un buco interno non diventa un falso crollo o una falsa ripresa. */
        for (int t = start; t <= Math.min(horizon, prices.length - 1); t++) {
            if (!Double.isFinite(prices[t])
                    || prices[t] <= 0)
                return new Leg(market, -1, -1, Double.NaN, Double.NaN, Outcome.MISSING);
        }
        int trough = start;
        for (int t = start + 1; t <= Math.min(declineEnd, prices.length - 1); t++) {
            if (prices[t] < prices[trough]) trough = t;
        }
        int recovery = -1;
        double bounce = Double.NaN;
        if (rules.kind().recoveryRequired) {
            bounce = 0;
            for (int t = trough + 1; t <= Math.min(trough + rules.recoveryMonths(), prices.length - 1); t++) {
                bounce = Math.max(bounce, 100 * (prices[t] / prices[trough] - 1));
                if (recovery < 0
                        && 100 * (prices[t] / prices[trough] - 1)
                        >= rules.rebound()) recovery = t;
            }
        }
        final double fall = 100 * (prices[trough] / prices[start] - 1);
        return new Leg(market, trough, recovery, fall, bounce,
                horizon >= prices.length
                        ? Outcome.PENDING
                        : fall <= -rules.marketDrop()
                        && (!rules.kind().recoveryRequired || recovery >= 0)
                        ? Outcome.CONFIRMED
                        : Outcome.REJECTED);
    }
}
