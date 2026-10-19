package macrolab;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class DataService {
    public record Source(String id,
                         String name,
                         String shortName,
                         String unit,
                         boolean rate,
                         String key,
                         String description,
                         Color color) {
        public String toString() {
            return name;
        }
        public String url() {
            if (key.startsWith("Y:"))
                return "https://query1.finance.yahoo.com/v8/finance/chart/"
                        + URLEncoder.encode(key.substring(2), StandardCharsets.UTF_8)
                        + "?period1=883612800&period2="
                        + Instant.now().getEpochSecond()
                        + "&interval=1mo";
            return "https://data-api.ecb.europa.eu/service/data/"
                    + key
                    + "?startPeriod=1998-01-01&format=csvdata";
        }
    }
    public static final Source[] SOURCES = {
        new Source("btp", "BTP (ETF Italia)", "BTP ETF", "EUR", false, "Y:IITB.MI", "iShares Italy Govt Bond (IITB.MI). Proxy di prezzo di un paniere di titoli di Stato italiani; non è il prezzo di un BTP specifico. Chiusure non rettificate: cedole escluse.", new Color(47, 103, 137)),
        new Source("rate", "Tasso depositi BCE", "BCE", "% annuo", true, "FM/D.U2.EUR.4F.KR.DFR.LEV", "Deposit facility BCE, livello in vigore a fine mese. È un tasso ufficiale, diverso dal rendimento di mercato dei BTP.", new Color(163, 112, 42)),
        new Source("inflation", "Inflazione area euro", "HICP", "% a/a", true, "HICP/M.U2.N.000000.4D0.ANR", "HICP totale, variazione annua, area euro a composizione variabile. Nuovo dataset HICP; dati anche preliminari e rivedibili. Mese di riferimento, non data di pubblicazione.", new Color(165, 75, 67)),
        new Source("sox", "Semiconduttori (SOX)", "SOX", "punti USD", false, "Y:^SOX", "PHLX Semiconductor Sector Index: progettazione, distribuzione e produzione di semiconduttori. Indice price return in USD.", new Color(114, 92, 145)),
        new Source("fx", "Cambio EUR / USD", "EUR/USD", "USD per EUR", false, "EXR/M.USD.EUR.SP00.E", "Cambio di riferimento BCE a fine mese. Un valore maggiore indica un euro più forte sul dollaro.", new Color(50, 125, 134)),
        new Source("market", "EURO STOXX 50", "EURO 50", "punti EUR", false, "Y:^STOXX50E", "Indice azionario generale scelto: EURO STOXX 50, grandi società dell'area euro, price return. Non rappresenta l'intero mercato mondiale.", new Color(91, 121, 62)),
        new Source("banks", "Banche europee (ETF)", "Banche ETF", "EUR", false, "Y:EXV1.DE", "iShares STOXX Europe 600 Banks (EXV1.DE), proxy dell'indice bancario europeo, inclusi paesi extra euro. Prezzi non rettificati; dividendi esclusi. Misura le azioni bancarie, non la stabilità del sistema.", new Color(156, 91, 45)),
        new Source("yield", "Rendimento Italia 10 anni", "Italia 10a", "% annuo", true, "IRS/M.IT.L.L40.CI.0000.EUR.N.Z", "Tasso a lungo termine italiano ai fini della convergenza, scadenza circa 10 anni, media mensile BCE. Non è una serie di prezzi né il rendimento di un singolo ISIN.", new Color(88, 103, 113)),
        new Source("auto", "Auto europee (STOXX 600 · ETF)", "Auto ETF", "EUR", false, "Y:EXV5.DE", "Settore automobilistico europeo: iShares STOXX Europe 600 Automobiles & Parts UCITS ETF (EXV5.DE), proxy investibile del benchmark settoriale capped. Include costruttori e componentistica, anche fuori dall'area euro; non misura vendite di automobili né il mercato globale. Chiusure non rettificate, dividendi esclusi. Fondo lanciato nel 2002; lo storico del fornitore può iniziare dopo.", new Color(80, 107, 164))
    };
    public record Series(Source source,
                         TreeMap<YearMonth, Double> values,
                         String status,
                         String retrieved,
                         String url) {
    }
    public record Dataset(List<Series> series) {

        public YearMonth first() {
            YearMonth first = YearMonth.now();
            for (final Series s : series) if (!s
                    .values
                    .isEmpty()
                    && s
                    .values
                    .firstKey()
                    .isBefore(first)) first = s.values.firstKey();
            return first;
        }

        public YearMonth last() {
            YearMonth last = YearMonth.of(2000, 1);
            for (final Series s : series) if (!s
                    .values
                    .isEmpty()
                    && s
                    .values
                    .lastKey()
                    .isAfter(last)) last = s.values.lastKey();
            return last;
        }

        public double[][] align(final YearMonth from,
                                final YearMonth to,
                                final boolean euroSox) {
            if (to
                    .isBefore(from)) throw new IllegalArgumentException("Intervallo mensile invertito");
            final int n = Math.toIntExact(to.getLong(ChronoField.PROLEPTIC_MONTH) - from.getLong(ChronoField.PROLEPTIC_MONTH) + 1);
            final double[][] out = new double[SOURCES.length][n];
            /* Un mese viene cercato in tutte le serie. Lo creo una volta sola;
               ogni cella è assegnata, compresi i NaN: il fill iniziale non serve. */
            for (int t = 0; t < n; t++) {
                final YearMonth ym = from.plusMonths(t);
                for (int i = 0; i < out.length; i++)
                    out[i][t] = series
                            .get(i)
                            .values
                            .getOrDefault(ym, Double.NaN);
            }
            if (euroSox)
                for (int t = 0; t < n; t++)
                    out[3][t] = out[4][t] > 0
                            ? out[3][t] / out[4][t]
                            : Double.NaN;
            return out;
        }
    }

    static HttpClient client() throws Exception {
        final HttpClient.Builder builder = HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (System
                .getProperty("os.name")
                .startsWith("Windows")) {
            /* Usa la fiducia del sistema Windows, mantenendo verifica TLS e hostname.
               Serve anche sulle reti con CA aziendali; NON disabilitare SSL. */
            final KeyStore roots = KeyStore.getInstance("Windows-ROOT");
            roots.load(null, null);
            final TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(roots);
            final SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trust.getTrustManagers(), null);
            builder.sslContext(ssl);
        }
        return builder.build();
    }

    public static Dataset load(final Path directory,
                               final boolean online) throws Exception {
        Files.createDirectories(directory);
        final List<Series> result = new ArrayList<>(SOURCES.length);
        try (final HttpClient http = client(); final ExecutorService workers = Executors.newFixedThreadPool(4)) {
            /* Quattro richieste alla volta. Ogni job possiede la sua
               mappa; Future.get() la consegna prima della pubblicazione su Swing. */
            final List<Callable<Series>> jobs = new ArrayList<>(SOURCES.length);
            for (final Source source : SOURCES) jobs.add(() -> loadOne(http, source, directory, online));
            for (final Future<Series> future : workers.invokeAll(jobs)) result.add(future.get());
        }
        return new Dataset(List.copyOf(result));
    }

    static Series loadOne(final HttpClient http,
                          final Source source,
                          final Path directory,
                          final boolean online) {
        final Path cache = directory.resolve(source.id + ".csv");
        String problem = "offline";
        if (online) {
            try {
                final String url = source.url();
                final HttpResponse<String> response;
                if ((response = http
                        .send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(45))
                        .header("User-Agent", "Mozilla/5.0 MacroLab/1.0")
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)))
                        .statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                final TreeMap<YearMonth, Double> values = source.key.startsWith("Y:")
                        ? yahoo(response.body()) : ecb(response.body(), source.id.equals("rate"));
                values.tailMap(YearMonth.now(ZoneId.of("Europe/Rome")), true).clear();
                if (values
                        .size() < 3) throw new IOException("meno di tre mesi disponibili");
                final String time = Instant.now().toString();
                final StringBuilder csv = new StringBuilder("month,value\n");
                for (final Map.Entry<YearMonth, Double> row : values.entrySet())
                    csv.append(row.getKey())
                            .append(',')
                            .append(row.getValue())
                            .append('\n');
                final Path temp = directory.resolve(source.id + ".tmp");
                Files.writeString(temp, csv);
                /* TODO: cache CSV e metadati sono due file separati. Un arresto tra
                   TODO: le due scritture può lasciare una data di download precedente. */
                Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(directory.resolve(source.id + ".meta"), time + "\n" + url);
                return new Series(source, values, "Online", time, url);
            } catch (final InterruptedException _) {
                Thread.currentThread().interrupt();
                problem = "interrotto"; }
            catch (final Exception ex) {
                problem = ex
                        .getClass()
                        .getSimpleName()
                        + ": "
                        + ex.getMessage();
            }
        }
        try {
            final TreeMap<YearMonth, Double> values = new TreeMap<>();
            for (final List<String> row : Formats.csv(Files.readString(cache))) {
                if (!row
                        .getFirst()
                        .equals("month"))
                    values.put(YearMonth.parse(row.getFirst()), Double.valueOf(row.get(1)));
            }
            final List<String> metadata = Files.readAllLines(directory.resolve(source.id + ".meta"));
            values.tailMap(YearMonth.now(ZoneId.of("Europe/Rome")), true).clear();
            return new Series(source,
                    values,
                    "Cache · " + problem,
                    metadata.getFirst(),
                    metadata.size() > 1
                            ? metadata.get(1)
                            : source.url());
        } catch (final Exception e) {
            return new Series(source,
                    new TreeMap<>(),
                    "Non disponibile · " + problem,
                    "—",
                    source.url());
        }
    }

    static TreeMap<YearMonth, Double> ecb(final String text,
                                          final boolean policyRate) throws IOException {
        final List<List<String>> rows;
        if ((rows = Formats.csv(text))
                .isEmpty()) throw new IOException("CSV BCE vuoto");
        final int date = rows.getFirst().indexOf("TIME_PERIOD");
        final int value;
        if ((value = rows
                .getFirst()
                .indexOf("OBS_VALUE")) < 0
                || date < 0) throw new IOException("CSV BCE: colonne mancanti");
        final TreeMap<LocalDate, Double> daily = new TreeMap<>();
        final TreeMap<YearMonth, Double> monthly = new TreeMap<>();
        for (int i = 1; i < rows.size(); i++) {
            final List<String> row;
            if ((row = rows
                    .get(i))
                    .size() <= Math.max(date, value)
                    || row
                    .get(value)
                    .isBlank()) continue;
            final double number;
            if (!Double.isFinite(number = Double.parseDouble(row
                    .get(value)))) continue;
            final String period = row.get(date);
            if (policyRate) daily.put(LocalDate.parse(period), number);
            else monthly.put(YearMonth.parse(period), number);
        }
        if (policyRate
                && !daily
                .isEmpty()) {
            /* I tassi ufficiali restano in vigore fino alla modifica successiva.
               Questa propagazione NON viene applicata ai prezzi o all'inflazione. */
            for (YearMonth month = YearMonth.from(daily.firstKey());
                 month.isBefore(YearMonth.now(ZoneId.of("Europe/Rome")));
                 month = month.plusMonths(1)) {
                final Map.Entry<LocalDate, Double> observation;
                if ((observation = daily
                        .floorEntry(month
                        .atEndOfMonth())) != null)
                    monthly.put(month, observation.getValue());
            }
        }
        return monthly;
    }

    static TreeMap<YearMonth, Double> yahoo(final String text) throws IOException {
        final Map<?, ?> chart = Formats.object(Formats.object(Formats.json(text)).get("chart"));
        final Object error;
        if ((error = chart
                .get("error")) != null)
            throw new IOException("Yahoo: " + error);
        final List<?> results;
        if ((results = Formats.array(chart
                .get("result")))
                .isEmpty())
            throw new IOException("Yahoo: nessun risultato");
        final Map<?, ?> result = Formats.object(results.getFirst());
        final List<?> timestamps = Formats.array(result.get("timestamp"));
        final List<?> closes = Formats.array(Formats
                .object(Formats
                        .array(Formats
                                .object(result.get("indicators"))
                                .get("quote"))
                        .getFirst())
                .get("close"));
        /* Il fuso vale per tutta la risposta: risoluzione fuori dal ciclo. */
        final ZoneId zone = ZoneId.of(String.valueOf(Formats
                .object(result.get("meta"))
                .get("exchangeTimezoneName")));
        if (timestamps
                .size() != closes
                .size())
            throw new IOException("Yahoo: lunghezze diverse");
        final TreeMap<YearMonth, Double> values = new TreeMap<>();
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps
                    .get(i) instanceof final Number timestamp
                    && closes
                    .get(i) instanceof final Number close
                    && Double.isFinite(close
                    .doubleValue())
                    && close
                    .doubleValue() > 0) {
                values.put(YearMonth.from(Instant
                        .ofEpochSecond(timestamp.longValue())
                        .atZone(zone)),
                        close.doubleValue());
            }
        }
        return values;
    }
}
