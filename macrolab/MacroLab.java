package macrolab;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public final class MacroLab extends JPanel {
    static final Color BG = new Color(245, 245, 242), PANEL = Color.WHITE, INK = new Color(40, 43, 46), MUTED = new Color(99, 105, 109), GRID = new Color(225, 228, 229), ACCENT = new Color(48, 103, 137);
    static final Color POSITIVE = new Color(178, 213, 194), NEGATIVE = new Color(234, 192, 178);
    static final String[] MATRIX_NAMES = {"BTP", "BCE", "HICP", "SOX", "FX", "EURO50", "Banche", "IT10a"};
    final Path directory;
    final JLabel status = new JLabel("Scarica i dati online per iniziare.");
    final JLabel insight = new JLabel(" ");
    final javax.swing.table.DefaultTableModel dataRows = new DefaultTableModel(new String[]{"Indicatore", "Ultimo valore", "Mese", "Dati"}, 0) {
         public boolean isCellEditable(final int row,
                                       final int column) {
             return false;
         }
    };
    final JTextField start = new JTextField("2016-01", 7), end = new JTextField(YearMonth.now().minusMonths(1).toString(), 7);
    final JComboBox<String> transform = new JComboBox<>(new String[]{"Variazioni mensili", "Valori originali"});
    final JComboBox<String> method = new JComboBox<>(new String[]{"Pearson", "Spearman"});
    final JComboBox<DataService.Source> xChoice = new JComboBox<>(DataService.SOURCES), yChoice = new JComboBox<>(DataService.SOURCES);
    final JSpinner lag = new JSpinner(new SpinnerNumberModel(0, -24, 24, 1));
    final JSpinner window = new JSpinner(new SpinnerNumberModel(24, 6, 120, 1));
    final JCheckBox euroSox = new JCheckBox("SOX convertito in EUR");
    final JCheckBox[] visible = new JCheckBox[DataService.SOURCES.length];
    final JTabbedPane tabs = new JTabbedPane();
    final Plot overview = new Plot(0), matrix = new Plot(1), scatter = new Plot(2), rolling = new Plot(3);
    final JTextArea notes = new JTextArea();
    final JButton refresh = button("Aggiorna dati");
    DataService.Dataset dataset;
    double[][] levels = new double[0][], analysis = new double[0][], z = new double[0][];
    /* Questi risultati appartengono al thread Swing. Si aggiornano con i filtri;
       paintComponent deve solo disegnare, anche durante un ridimensionamento. */
    Stats.Correlation[][] correlations = new Stats.Correlation[0][];
    Stats.Correlation[] rollingPairs = new Stats.Correlation[0];
    double[][] rollingPlot = new double[0][];
    YearMonth first;
    int size;

    public MacroLab(final Path directory) {
        this.directory = directory;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 18, 12, 18));
        setBackground(BG);
        final JPanel top = new JPanel(); top.setOpaque(false); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        final JPanel heading = row();
        final JLabel title = new JLabel("Analisi dei mercati"); title.setFont(new Font("Segoe UI", Font.BOLD, 21)); title.setForeground(INK);
        heading.add(title); heading.add(Box.createHorizontalGlue()); heading.add(refresh); heading.add(Box.createHorizontalStrut(6));
        final JButton offline = button("Usa dati salvati"); heading.add(offline); heading.add(Box.createHorizontalStrut(6));
        final JButton export = button("Esporta CSV"); heading.add(export); heading.add(Box.createHorizontalStrut(6));
        final JButton png = button("Salva grafico"); heading.add(png);
        top.add(heading); top.add(Box.createVerticalStrut(14));
        final JPanel controls = row();
        start.setMaximumSize(new Dimension(95, 28)); end.setMaximumSize(new Dimension(95, 28));
        start.setToolTipText("Anno e mese, ad esempio 2020-01"); end.setToolTipText("Anno e mese, ad esempio 2025-12");
        controls.add(label("Da", INK)); controls.add(start); controls.add(label("a", INK)); controls.add(end);
        final JButton apply = button("Applica"); controls.add(Box.createHorizontalStrut(6)); controls.add(apply);
        controls.add(Box.createHorizontalStrut(16)); controls.add(label("Valori", INK)); controls.add(transform);
        controls.add(label("Metodo", INK)); controls.add(method); controls.add(Box.createHorizontalStrut(10)); controls.add(euroSox); controls.add(Box.createHorizontalGlue());
        transform.setMaximumSize(new Dimension(180, 28)); method.setMaximumSize(new Dimension(110, 28));
        top.add(controls); top.add(Box.createVerticalStrut(7));
        final JPanel pair = row(); pair.add(label("Confronta", INK)); pair.add(xChoice); pair.add(label("con", INK)); pair.add(yChoice);
        xChoice.setMaximumSize(new Dimension(230, 28)); yChoice.setMaximumSize(new Dimension(230, 28));
        yChoice.setSelectedIndex(1); pair.add(label("Ritardo Y (mesi)", INK)); pair.add(lag);
        lag.setMaximumSize(new Dimension(62, 28)); window.setMaximumSize(new Dimension(62, 28));
        final JPanel rollingControls = row(); rollingControls.add(label("Finestra (mesi)", INK)); rollingControls.add(window); pair.add(rollingControls); pair.add(Box.createHorizontalGlue());
        pair.setVisible(false); top.add(pair);
        add(top, BorderLayout.NORTH);
        final JPanel linePage = new JPanel(new BorderLayout()); linePage.setOpaque(false);
        final JPanel legend = new JPanel(new GridLayout(2, 4, 6, 3)); legend.setBorder(new EmptyBorder(8, 12, 6, 12));
        for (int i = 0; i < visible.length; i++) {
            visible[i] = new JCheckBox(DataService.SOURCES[i].name(), i < 3);
            visible[i].setForeground(DataService.SOURCES[i].color()); visible[i].setOpaque(false);
            visible[i].addActionListener((final ActionEvent e) -> overview.repaint()); legend.add(visible[i]);
        }
        legend.setOpaque(true); legend.setBackground(PANEL); linePage.setOpaque(true); linePage.setBackground(PANEL);
        linePage.add(legend, BorderLayout.NORTH); linePage.add(overview);
        tabs.addTab("Grafico", linePage); tabs.addTab("Correlazioni", matrix); tabs.addTab("Confronto", scatter); tabs.addTab("Nel tempo", rolling);
        final JTable dataTable = new JTable(dataRows); dataTable.setRowHeight(34); dataTable.setFillsViewportHeight(true); dataTable.setShowVerticalLines(false);
        dataTable.getTableHeader().setReorderingAllowed(false); dataTable.getColumnModel().getColumn(0).setPreferredWidth(310);
        final JPanel dataPage = new JPanel(new BorderLayout(0, 10)); dataPage.setBackground(PANEL); dataPage.setBorder(new EmptyBorder(14, 14, 14, 14));
        dataPage.add(label("Ultimi valori disponibili. Il periodo scelto si applica ai grafici e alle correlazioni.", MUTED), BorderLayout.NORTH);
        dataPage.add(new JScrollPane(dataTable), BorderLayout.CENTER); tabs.addTab("Dati", dataPage);
        notes.setEditable(false); notes.setLineWrap(true); notes.setWrapStyleWord(true); notes.setMargin(new Insets(20, 22, 20, 22)); notes.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabs.addTab("Fonti e metodo", new JScrollPane(notes)); add(tabs, BorderLayout.CENTER);
        tabs.addChangeListener((final javax.swing.event.ChangeEvent e) -> {
            pair.setVisible(tabs.getSelectedIndex() == 2 || tabs.getSelectedIndex() == 3);
            rollingControls.setVisible(tabs.getSelectedIndex() == 3);
            insight.setVisible(tabs.getSelectedIndex() == 2 || tabs.getSelectedIndex() == 3);
            png.setEnabled(tabs.getSelectedIndex() < 4); top.revalidate();
        });
        final JPanel footer = new JPanel(new GridLayout(2, 1, 0, 7)); footer.setOpaque(false);
        insight.setForeground(INK); insight.setVisible(false); status.setForeground(MUTED); footer.add(insight); footer.add(status); add(footer, BorderLayout.SOUTH);
        refresh.addActionListener((final ActionEvent e) -> load(true)); offline.addActionListener((final ActionEvent e) -> load(false));
        apply.addActionListener((final ActionEvent e) -> calculate()); start.addActionListener((final ActionEvent e) -> calculate()); end.addActionListener((final ActionEvent e) -> calculate());
        transform.addActionListener((final ActionEvent e) -> calculate()); method.addActionListener((final ActionEvent e) -> calculate()); euroSox.addActionListener((final ActionEvent e) -> calculate());
        xChoice.addActionListener((final ActionEvent e) -> calculate()); yChoice.addActionListener((final ActionEvent e) -> calculate());
        lag.addChangeListener((final javax.swing.event.ChangeEvent e) -> calculate()); window.addChangeListener((final javax.swing.event.ChangeEvent e) -> calculate());
        export.addActionListener((final ActionEvent e) -> save(false)); png.addActionListener((final ActionEvent e) -> save(true));
        updateData();
    }

    static JPanel row() { final JPanel row = new JPanel(); row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS)); row.setOpaque(false); return row; }
    static JLabel label(final String text, final Color color) { final JLabel label = new JLabel("  " + text + "  "); label.setForeground(color); return label; }
    static JButton button(final String text) { final JButton b = new JButton(text); b.setMargin(new Insets(5, 10, 5, 10)); return b; }
    static String number(final double d) { return Double.isFinite(d) ? String.format(Locale.ITALY, "%.2f", d) : "n/d"; }

    void load(final boolean online) {
        if (!refresh
                .isEnabled()) return;
        refresh.setEnabled(false); status.setText(online ? "Sto aggiornando i dati da BCE e Yahoo Finance…" : "Apertura dei dati salvati…");
        new SwingWorker<DataService.Dataset, Void>() {
            @Override protected DataService.Dataset doInBackground() throws Exception { return DataService.load(directory, online); }
            @Override protected void done() {
                /* get() consegna il risultato del worker prima di toccare la GUI.
                   NON pubblicare dataset dal thread che sta scaricando. */
                try { dataset = get(); updateData(); calculate(); }
                catch (final Exception e) { status.setText("Caricamento fallito: " + e.getMessage()); }
                finally { refresh.setEnabled(true); }
            }
        }.execute();
    }

    void updateData() {
        dataRows.setRowCount(0);
        for (int i = 0; i < DataService.SOURCES.length; i++) {
            final DataService.Source source = DataService.SOURCES[i];
            String value = "—", month = "—", origin = "Non disponibili";
            if (dataset != null
                    && !dataset
                    .series()
                    .get(i)
                    .values()
                    .isEmpty()) {
                final DataService.Series series = dataset.series().get(i);
                value = number(series.values().lastEntry().getValue()) + "  " + source.unit();
                month = series.values().lastKey().toString();
                origin = series.status().startsWith("Online") ? "Aggiornati online" : "Salvati sul computer";
            }
            dataRows.addRow(new Object[]{source.name(), value, month, origin});
        }
    }

    void calculate() {
        if (dataset == null) return;
        try {
            final YearMonth from = YearMonth.parse(start.getText().trim()), to = YearMonth.parse(end.getText().trim());
            final long count;
            if ((count = java.time.temporal.ChronoUnit.MONTHS
                    .between(from, to) + 1) < 3
                    || count > 600) throw new IllegalArgumentException("Scegli un intervallo tra 3 e 600 mesi.");
            if (!to
                    .isBefore(YearMonth.now())) throw new IllegalArgumentException("Il mese corrente è incompleto: scegli al massimo il mese scorso.");
            /* Calcolo la prima variazione usando anche il mese precedente al filtro.
               Il calendario è continuo: i valori assenti restano NaN. */
            final double[][] extended = dataset.align(from.minusMonths(1), to, euroSox.isSelected());
            first = from; size = (int) count;
            levels = new double[extended.length][]; analysis = new double[extended.length][]; z = new double[extended.length][];
            for (int i = 0; i < extended.length; i++) {
                levels[i] = Arrays.copyOfRange(extended[i], 1, extended[i].length);
                analysis[i] = transform.getSelectedIndex() == 0 ? Arrays.copyOfRange(Stats.changes(extended[i], DataService.SOURCES[i].rate()), 1, extended[i].length) : levels[i];
                z[i] = Stats.standardize(analysis[i]);
            }
            /* Leggo metodo e ritardo fuori dai cicli; nessun accesso ripetuto ai
               controlli Swing durante la costruzione delle serie derivate. */
            final boolean spearman = method.getSelectedIndex() == 1;
            correlations = new Stats.Correlation[analysis.length][analysis.length];
            for (int i = 0; i < analysis.length; i++) {
                for (int j = i; j < analysis.length; j++) {
                    /* Ritardo zero: r(X,Y) = r(Y,X), anche eliminando i mancanti
                       per coppia. Otto serie: 8*9/2 = 36 calcoli invece di 64. */
                    correlations[i][j] = Stats.correlate(analysis[i], analysis[j], 0, size, 0, spearman);
                    correlations[j][i] = correlations[i][j];
                }
            }
            final int months = (int) window.getValue();
            final int delay = (int) lag.getValue();
            rollingPairs = new Stats.Correlation[size];
            rollingPlot = new double[1][size];
            Arrays.fill(rollingPlot[0], Double.NaN);
            for (int t = months - 1; t < size; t++) {
                rollingPairs[t] = pair(t + 1 - months, t + 1, delay);
                rollingPlot[0][t] = rollingPairs[t].value();
            }
            final Stats.Correlation c = pair(0, size, (int) lag.getValue());
            insight.setText(name(xChoice.getSelectedIndex()) + " ↔ " + name(yChoice.getSelectedIndex()) + "    r = " + number(c.value()) + "    ·    n = " + c.count() + " mesi    ·    " + (c.count() < 24 ? "Campione ridotto. " : "") + "Il legame osservato non dimostra una causa.");
            int available = 0;
            for (final DataService.Series s : dataset.series()) if (!s
                    .values()
                    .isEmpty()) available++;
            status.setText(available + "/8 serie disponibili  ·  " + from + " → " + to + "  ·  " + (transform.getSelectedIndex() == 0 ? "Prezzi: variazioni %. Tassi e inflazione: differenze in punti percentuali." : "Livelli: trend comuni possono generare correlazioni spurie."));
            notes.setText(report()); notes.setCaretPosition(0); repaint();
        } catch (final Exception e) { size = 0; insight.setText("Parametri non validi: correggi il periodo e premi Applica."); status.setText("Controlla i parametri: " + e.getMessage()); repaint(); }
    }

    String name(final int index) { return DataService.SOURCES[index].shortName() + (index == 3 ? (euroSox.isSelected() ? " EUR" : " USD") : ""); }
    Stats.Correlation pair(final int from, final int to, final int delay) { return Stats.correlate(analysis[xChoice.getSelectedIndex()], analysis[yChoice.getSelectedIndex()], from, to, delay, method.getSelectedIndex() == 1); }

    String report() {
        final StringBuilder text = new StringBuilder("Analisi dei mercati\n\n");
        text.append("Periodo: ").append(first).append(" → ").append(first.plusMonths(size - 1)).append("\nMetodo: ").append(method.getSelectedItem()).append("; ").append(transform.getSelectedItem()).append("\nSOX: ").append(euroSox.isSelected() ? "convertito in EUR" : "USD").append("\n").append(insight.getText()).append("\n\n");
        text.append("Come leggere i risultati\nr va da −1 (associazione inversa) a +1 (diretta); vicino a 0 indica poca associazione secondo il metodo scelto. Pearson misura il legame lineare, Spearman quello monotono con ranghi medi per i pareggi. n indica le coppie valide. Una serie costante o meno di 3 coppie restituiscono n/d. Nessun test di significatività o inferenza causale è effettuato.\n\n");
        text.append("Calcoli\nFrequenza mensile; mese corrente escluso. Prezzi e cambi: 100 × (P[t]/P[t−1] − 1). Tassi, rendimento decennale e inflazione a/a: x[t] − x[t−1] in punti percentuali. Non si tratta dell'inflazione mensile. Sono usati mesi consecutivi, senza interpolare dati mancanti. La prima variazione del filtro usa il mese precedente. Matrice: correlazioni contemporanee, esclusione dei mancanti per coppia, campioni eventualmente diversi.\n\n");
        text.append("Grafici\nAndamento: z-score delle serie selezionate nel periodo, media 0 e deviazione standard campionaria 1; non è un rendimento cumulato. Confronto: valori nelle unità della trasformazione. Ritardo positivo k confronta X[t] con Y[t+k]: X precede Y. Ritardo negativo: Y precede X. La matrice ignora il ritardo. Correlazione mobile: finestra di ").append(window.getValue()).append(" mesi di calendario; entrambe le osservazioni devono ricadere nella finestra; ritardo ").append(lag.getValue()).append(".\n\n");
        text.append("Interpretazione\nA parità di flussi e scadenza, un aumento del rendimento richiesto riduce il prezzo di un'obbligazione. Il tasso BCE non coincide con il rendimento dei BTP: contano anche aspettative e rischio sovrano. Inflazione, banche, cambio e semiconduttori possono rispondere a più fattori comuni; segni e intensità cambiano nel tempo. I dati di inflazione sono riferiti al mese economico e possono essere rivisti: questa analisi non è un backtest con informazioni disponibili in tempo reale.\n\n");
        text.append("Indicatori utilizzati\nETF BTP e banche sono proxy espliciti, con costi, composizione e stacchi cedolari propri. Prezzi di chiusura NON rettificati: non includono reinvestimento di dividendi o cedole. Un singolo BTP richiede ISIN e prezzi storici coerenti. Il rendimento italiano 10 anni è una media mensile; gli altri mercati e il tasso BCE sono di fine mese. La valuta base influenza le correlazioni: SOX in EUR = SOX in USD / USD per EUR, senza copertura valutaria.\n\n");
        text.append("Fonti e disponibilità\nDownload BCE ufficiale via SDMX; quotazioni Yahoo Finance tramite endpoint pubblico non contrattualizzato, soggetto a cambiamenti o limiti. Nessun dato simulato. In caso di errore si usa solo la cache disponibile, identificata qui sotto. Gli ultimi valori nella tabella Dati sono quelli disponibili e non dipendono dal filtro.\n\n");
        for (final DataService.Series series : dataset.series()) {
            text.append(series.source().name()).append("\n").append(series.source().description()).append("\nStato: ").append(series.status()).append("\nDownload UTC: ").append(series.retrieved()).append("\nCopertura: ").append(series.values().isEmpty() ? "nessuna" : series.values().firstKey() + " → " + series.values().lastKey() + "; " + series.values().size() + " mesi").append("\n").append(series.url()).append("\n\n");
        }
        text.append("Riferimenti\nhttps://data.ecb.europa.eu/help/api/data\nhttps://data.ecb.europa.eu/data/datasets/hicp/data-information\nhttps://www.ecb.europa.eu/mopo/intro/transmission/html/index.en.html\nhttps://indexes.nasdaq.com/Index/Overview/SOX\nhttps://www.ishares.com/uk/individual/en/products/251934/ishares-stoxx-europe-600-banks-ucits-etf_7\nhttps://www.borsaitaliana.it/borsa/etf/scheda/IE00B7LW6Y90-ETFP.html\n");
        return text.toString();
    }

    void save(final boolean image) {
        if (size == 0) { status.setText("Carica i dati prima di esportare."); return; }
        final JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser
                .showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            final Path folder = chooser.getSelectedFile().toPath();
            if (image) {
                final Component component = tabs.getSelectedComponent();
                final BufferedImage picture = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
                final Graphics2D graphics = picture.createGraphics(); component.paint(graphics); graphics.dispose();
                ImageIO.write(picture, "png", folder.resolve("macrolab-grafico-" + System.currentTimeMillis() + ".png").toFile());
            } else export(folder);
            status.setText("Esportazione completata in " + folder);
        } catch (final Exception e) { status.setText("Esportazione fallita: " + e.getMessage()); }
    }

    void export(final Path folder) throws Exception {
        final String suffix = "-" + System.currentTimeMillis();
        for (int kind = 0; kind < 2; kind++) {
            final StringBuilder csv = new StringBuilder("month");
            for (int i = 0; i < DataService.SOURCES.length; i++) csv.append(',').append(DataService.SOURCES[i].id()).append(i == 3 && euroSox.isSelected() ? "_eur" : "");
            csv.append('\n');
            for (int t = 0; t < size; t++) {
                csv.append(first.plusMonths(t));
                for (final double[] series : kind == 0 ? levels : analysis) { csv.append(','); if (Double.isFinite(series[t])) csv.append(series[t]); }
                csv.append('\n');
            }
            Files.writeString(folder.resolve((kind == 0 ? "livelli" : "analisi") + suffix + ".csv"), csv);
        }
        final StringBuilder correlationCsv = new StringBuilder("x,y,method,r,n\n");
        for (int i = 0; i < analysis.length; i++) for (int j = 0; j < analysis.length; j++) {
            final Stats.Correlation c = correlations[i][j];
            correlationCsv.append(DataService.SOURCES[i].id()).append(',').append(DataService.SOURCES[j].id()).append(',').append(method.getSelectedItem()).append(',').append(Double.isFinite(c.value()) ? c.value() : "").append(',').append(c.count()).append('\n');
        }
        Files.writeString(folder.resolve("correlazioni" + suffix + ".csv"), correlationCsv);
        Files.writeString(folder.resolve("metodo-e-fonti" + suffix + ".txt"), report());
    }

    final class Plot extends JPanel {
        final int kind;
        Plot(final int kind) {
            this.kind = kind; setBackground(PANEL); setPreferredSize(new Dimension(1120, 430));
            ToolTipManager.sharedInstance().registerComponent(this);
            if (kind == 1) addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(final MouseEvent e) {
                    final int cell;
                    if ((cell = Math.min((getWidth() - 180) / 8, (getHeight() - 96) / 8)) <= 0) return;
                    /* Coordinate acquisite prima dei controlli: preservo l'ordine
                       delle letture dell'evento, anche sui bordi esterni. */
                    final int x = (e.getX() - 142) / cell, y = (e.getY() - 64) / cell;
                    if (e
                            .getX() >= 142
                            && e
                            .getY() >= 64
                            && x < 8
                            && y < 8) { xChoice.setSelectedIndex(x); yChoice.setSelectedIndex(y); tabs.setSelectedIndex(2); }
                }
            });
        }
        @Override public String getToolTipText(final MouseEvent e) {
            if (size == 0) return null;
            if (kind == 1) {
                final int cell;
                if ((cell = Math.min((getWidth() - 180) / 8, (getHeight() - 96) / 8)) <= 0
                        || e
                        .getX() < 142
                        || e
                        .getY() < 64) return null;
                final int x = (e.getX() - 142) / cell;
                final int y;
                if ((y = (e
                        .getY() - 64) / cell) >= 8
                        || x >= 8) return null;
                final Stats.Correlation c = correlations[x][y];
                return name(x) + " / " + name(y) + " : r=" + number(c.value()) + ", n=" + c.count() + ". Clicca per confrontare.";
            }
            final int t = Math.max(0, Math.min(size - 1, (int) Math.round((e.getX() - 72.0) * (size - 1) / (getWidth() - 104))));
            if (kind == 3) {
                if (t + 1 < (int) window
                        .getValue()) return "Finestra incompleta";
                final Stats.Correlation c = rollingPairs[t];
                return first.plusMonths(t) + ": r=" + number(c.value()) + ", n=" + c.count();
            }
            if (kind != 0) return "X=" + name(xChoice.getSelectedIndex()) + "; Y=" + name(yChoice.getSelectedIndex());
            final StringBuilder tip = new StringBuilder("<html>").append(first.plusMonths(t));
            for (int i = 0; i < levels.length; i++) if (visible[i]
                    .isSelected()) tip.append("<br>").append(name(i)).append(": ").append(number(levels[i][t])).append(" · analisi ").append(number(analysis[i][t]));
            return tip.append("</html>").toString();
        }
        @Override protected void paintComponent(final Graphics graphics) {
            super.paintComponent(graphics);
            final Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g.setColor(MUTED);
                if (size == 0) { g.drawString("Scarica i dati online per esplorare i mercati.", 40, 65); return; }
                if (kind == 1) { heatmap(g); return; }
                final int left = 72, top = 56, width = getWidth() - 104;
                final int height;
                if ((height = getHeight() - 110) < 40
                        || width < 40) return;
                if (kind == 2) { scatter(g, left, top, width, height); return; }
                double min = kind == 3 ? -1 : Double.POSITIVE_INFINITY, max = kind == 3 ? 1 : Double.NEGATIVE_INFINITY;
                final double[][] values;
                if (kind == 3) {
                    values = rollingPlot;
                } else {
                    values = z;
                    for (int i = 0; i < values.length; i++) if (visible[i]
                            .isSelected()) for (final double v : values[i]) if (Double.isFinite(v)) { min = Math.min(min, v); max = Math.max(max, v); }
                    if (!Double.isFinite(min)) { g.drawString("Nessun dato variabile nel periodo selezionato.", left, top); return; }
                    if (max == min) { max++; min--; }
                    final double pad = (max - min) * 0.08; min -= pad; max += pad;
                }
                axes(g, left, top, width, height, min, max);
                g.setColor(INK); g.drawString(kind == 3 ? "Correlazione nel tempo  —  " + method.getSelectedItem() + "  ·  finestra " + window.getValue() + " mesi  ·  ritardo Y " + lag.getValue() : "Serie su scala comune (z-score)  —  " + transform.getSelectedItem(), left, 28);
                for (int i = 0; i < values.length; i++) {
                    if (kind == 0
                            && !visible[i]
                            .isSelected()) continue;
                    g.setColor(kind == 3 ? ACCENT : DataService.SOURCES[i].color()); g.setStroke(new BasicStroke(kind == 3 ? 2.6f : 1.8f));
                    final Path2D line = new Path2D.Double(); boolean connected = false;
                    for (int t = 0; t < size; t++) {
                        if (!Double.isFinite(values[i][t])) { connected = false; continue; }
                        final double x = left + (double) t * width / (size - 1), y = top + height - (values[i][t] - min) / (max - min) * height;
                        if (connected) line.lineTo(x, y); else { line.moveTo(x, y); connected = true; }
                    }
                    g.draw(line);
                }
                g.setColor(MUTED);
                for (int i = 0; i <= 4; i++) g.drawString(first.plusMonths((size - 1L) * i / 4).toString(), left + width * i / 4 - 20, top + height + 24);
                g.drawString(kind == 3 ? "Passa sul grafico per r e numerosità. Le finestre incomplete non vengono tracciate." : "Media = 0 · deviazione standard = 1. Passa sul grafico per valori originali e trasformati.", left, getHeight() - 10);
            } finally { g.dispose(); }
        }
        void axes(final Graphics2D g, final int left, final int top, final int width, final int height, final double min, final double max) {
            g.setStroke(new BasicStroke(1));
            for (int i = 0; i <= 4; i++) {
                final int y = top + i * height / 4;
                g.setColor(GRID); g.drawLine(left, y, left + width, y); g.setColor(MUTED); g.drawString(number(max - (max - min) * i / 4), 12, y + 4);
            }
        }
        void heatmap(final Graphics2D g) {
            final int cell;
            if ((cell = Math.min((getWidth() - 180) / 8, (getHeight() - 96) / 8)) < 10) return;
            g.setColor(INK); g.drawString("Correlazioni  —  " + method.getSelectedItem() + "  ·  ritardo zero  ·  clicca una cella per confrontare", 26, 26);
            for (int i = 0; i < 8; i++) {
                g.setColor(MUTED); g.drawString(name(i), 16, 64 + i * cell + cell / 2); g.drawString(MATRIX_NAMES[i], 142 + i * cell, 52);
                for (int j = 0; j < 8; j++) {
                    final Stats.Correlation c = correlations[j][i];
                    final double strength = Double.isFinite(c.value()) ? Math.abs(c.value()) : 0;
                    final Color target = c.value() >= 0 ? POSITIVE : NEGATIVE;
                    g.setColor(new Color((int) (PANEL.getRed() * (1 - strength) + target.getRed() * strength), (int) (PANEL.getGreen() * (1 - strength) + target.getGreen() * strength), (int) (PANEL.getBlue() * (1 - strength) + target.getBlue() * strength)));
                    g.fillRect(142 + j * cell, 64 + i * cell, cell - 2, cell - 2); g.setColor(INK);
                    g.drawString(number(c.value()), 148 + j * cell, 64 + i * cell + cell / 2);
                    if (cell >= 40) { g.setFont(new Font("Segoe UI", Font.PLAIN, 10)); g.drawString("n " + c.count(), 148 + j * cell, 78 + i * cell + cell / 2); g.setFont(new Font("Segoe UI", Font.PLAIN, 12)); }
                }
            }
            final int lx;
            if (getWidth() - (lx = 162 + 8 * cell) > 180) {
                g.setColor(ACCENT); g.drawString("+1   Associazione diretta", lx, 100);
                g.setColor(MUTED); g.drawString("  0   Associazione debole", lx, 130);
                g.setColor(new Color(153, 75, 54)); g.drawString("−1   Associazione inversa", lx, 160);
                g.setColor(MUTED); g.drawString("n = coppie di mesi valide", lx, 210); g.drawString("Campioni diversi per coppia.", lx, 235); g.drawString("Nessuna inferenza causale.", lx, 260);
            }
        }
        void scatter(final Graphics2D g, final int left, final int top, final int width, final int height) {
            final double[] x = analysis[xChoice.getSelectedIndex()], y = analysis[yChoice.getSelectedIndex()];
            final int delay = (int) lag.getValue();
            double xmin = Double.POSITIVE_INFINITY, xmax = Double.NEGATIVE_INFINITY, ymin = Double.POSITIVE_INFINITY, ymax = Double.NEGATIVE_INFINITY;
            for (int t = 0; t < size; t++) {
                final int j;
                if ((j = t + delay) >= 0
                        && j < size
                        && Double.isFinite(x[t])
                        && Double.isFinite(y[j])) { xmin = Math.min(xmin, x[t]); xmax = Math.max(xmax, x[t]); ymin = Math.min(ymin, y[j]); ymax = Math.max(ymax, y[j]); }
            }
            g.setColor(INK); g.drawString(name(xChoice.getSelectedIndex()) + " [X]  /  " + name(yChoice.getSelectedIndex()) + " [Y]  ·  X[t] con Y[t" + (delay >= 0 ? "+" : "") + delay + "]", left, 28);
            if (!Double.isFinite(xmin)) { g.drawString("Nessuna coppia disponibile.", left, top); return; }
            if (xmin == xmax) { xmin--; xmax++; } if (ymin == ymax) { ymin--; ymax++; }
            final double padx = (xmax - xmin) * 0.08, pady = (ymax - ymin) * 0.08; xmin -= padx; xmax += padx; ymin -= pady; ymax += pady;
            axes(g, left, top, width, height, ymin, ymax);
            g.setColor(new Color(48, 103, 137, 170));
            for (int t = 0; t < size; t++) {
                final int j;
                if ((j = t + delay) >= 0
                        && j < size
                        && Double.isFinite(x[t])
                        && Double.isFinite(y[j])) g.fillOval(left + (int) ((x[t] - xmin) / (xmax - xmin) * width) - 4, top + height - (int) ((y[j] - ymin) / (ymax - ymin) * height) - 4, 8, 8);
            }
            g.setColor(MUTED);
            for (int i = 0; i <= 4; i++) g.drawString(number(xmin + (xmax - xmin) * i / 4), left + i * width / 4 - 12, top + height + 24);
            g.drawString("X: " + unit(xChoice.getSelectedIndex()) + "   ·   Y: " + unit(yChoice.getSelectedIndex()) + "   ·   Ogni punto è una coppia mensile; ritardo positivo = X precede Y.", left, getHeight() - 10);
        }
    }
    String unit(final int index) { return transform.getSelectedIndex() == 0 ? (DataService.SOURCES[index].rate() ? "punti percentuali" : "variazione %") : (index == 3 && euroSox.isSelected() ? "punti convertiti EUR" : DataService.SOURCES[index].unit()); }

    static void theme() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            System.err.println("Tema di sistema non disponibile: " + e.getMessage());
        }
        for (final String key : new String[]{"Panel.background", "TabbedPane.background", "CheckBox.background"}) UIManager.put(key, BG);
        for (final String key : new String[]{"Viewport.background", "TextArea.background", "TextField.background", "Table.background"}) UIManager.put(key, PANEL);
        for (final String key : new String[]{"Label.foreground", "Button.foreground", "TextField.foreground", "TextArea.foreground", "ComboBox.foreground", "CheckBox.foreground", "TabbedPane.foreground", "Table.foreground"}) UIManager.put(key, INK);
        UIManager.put("TextField.caretForeground", INK); UIManager.put("Table.gridColor", GRID);
        for (final String key : new String[]{"Label.font", "Button.font", "TextField.font", "ComboBox.font", "TabbedPane.font", "CheckBox.font", "Table.font", "TableHeader.font", "Spinner.font"}) UIManager.put(key, new Font("Segoe UI", Font.PLAIN, 13));
    }

    public static void main(final String[] args) throws Exception {
        final Path data = Path.of(System.getProperty("macrolab.data", "data"));
        if (args.length > 0
                && args[0]
                .equals("--download")) {
            for (final DataService.Series series : DataService.load(data, true).series()) System.out.println(series.source().id() + " | " + series.status() + " | " + series.values().size() + " mesi | " + (series.values().isEmpty() ? "—" : series.values().lastKey()));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            theme(); final JFrame frame = new JFrame("Analisi dei mercati"); final MacroLab app = new MacroLab(data);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); frame.setContentPane(app); final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); frame.setSize(Math.min(1240, screen.width), Math.min(800, screen.height)); frame.setMinimumSize(new Dimension(Math.min(1000, screen.width), Math.min(650, screen.height))); frame.setLocationRelativeTo(null); frame.setVisible(true); app.load(true);
        });
    }
}
