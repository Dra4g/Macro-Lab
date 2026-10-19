package macrolab;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Properties;
import java.util.EnumMap;
import java.io.Reader;
import java.io.Writer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public final class MacroLab extends JPanel {
    static final Color BG = new Color(245, 244, 240), PANEL = Color.WHITE, INK = new Color(40, 43, 46), MUTED = new Color(99, 105, 109), GRID = new Color(225, 228, 229), ACCENT = new Color(49, 76, 69);
    static final Color POSITIVE = new Color(178, 213, 194), NEGATIVE = new Color(234, 192, 178);
    static final String[] MATRIX_NAMES = {"BTP", "BCE", "HICP", "SOX", "FX", "EU50", "Banc.", "IT10", "Auto"};
    final Path directory;
    final Properties appearance = new Properties();
    final Color[] seriesColors = new Color[DataService.SOURCES.length];
    final JButton[] colorButtons = new JButton[DataService.SOURCES.length];
    Color inflationColor = new Color(183, 137, 58), declineColor = new Color(164, 76, 60), recoveryColor = new Color(58, 119, 94), inflationRiseColor = new Color(115, 88, 151);
    final JCheckBox showPatterns = new JCheckBox("Mostra le bande del pattern", true);
    final JPanel patternControls = column();
    final JComboBox<PatternStudy.Kind> patternKind = new JComboBox<>(PatternStudy.Kind.values());
    final EnumMap<PatternStudy.Kind, PatternStudy.Rules> patternRules = new EnumMap<>(PatternStudy.Kind.class);
    final JTextArea patternHelp = paragraph("", 4);
    PatternStudy.Kind displayedPattern = PatternStudy.Kind.DISINFLATION_REBOUND;
    boolean switchingPattern;
    final JSpinner inflationMonths = new JSpinner(new SpinnerNumberModel(3, 1, 24, 1));
    final JSpinner inflationDrop = new JSpinner(new SpinnerNumberModel(1.5, 0.1, 15.0, 0.1));
    final JSpinner declineMonths = new JSpinner(new SpinnerNumberModel(3, 0, 24, 1));
    final JSpinner marketDrop = new JSpinner(new SpinnerNumberModel(3.0, 0.1, 50.0, 0.5));
    final JSpinner recoveryMonths = new JSpinner(new SpinnerNumberModel(6, 1, 36, 1));
    final JSpinner rebound = new JSpinner(new SpinnerNumberModel(3.0, 0.1, 100.0, 0.5));
    final JTextArea patternSummary = paragraph("", 3), eventDetails = paragraph("Seleziona un episodio per leggere minimi, recuperi e date di verifica.", 5);
    final DefaultTableModel patternRows = new DefaultTableModel(new String[]{"Segnale", "Δ HICP (pp)", "EUR/USD", "BTP ETF", "EURO 50", "Esito", "Verifica entro"}, 0) {
        public boolean isCellEditable(final int row, final int column) { return false; }
    };
    List<PatternStudy.Event> events = List.of();
    final YearMonth studyFirst = YearMonth.of(1998, 1);
    PatternStudy.Rules appliedRules = new PatternStudy.Rules(3, 1.5, 3, 3, 6, 3);
    final JLabel status = new JLabel("Scarica i dati online per iniziare.");
    final JTextArea insight = paragraph("", 3);
    final JLabel pageTitle = new JLabel();
    final JLabel periodSummary = new JLabel();
    final JTextArea pageHelp = paragraph("", 3);
    final JTextArea transformHelp = paragraph("", 4), methodHelp = paragraph("", 4), currencyHelp = paragraph("", 4), lagHelp = paragraph("", 4);
    final JPanel pairControls = column(), rollingControls = column();
    final JButton png = button("Salva immagine");
    final javax.swing.table.DefaultTableModel dataRows = new DefaultTableModel(new String[]{"Indicatore", "Ultimo valore", "Mese", "Copertura / origine"}, 0) {
         public boolean isCellEditable(final int row,
                                       final int column) {
             return false;
         }
    };
    final JTextField start = new JTextField("2000-01", 7), end = new JTextField(YearMonth.now().minusMonths(1).toString(), 7);
    final JComboBox<String> transform = new JComboBox<>(new String[]{"Variazioni mensili", "Valori originali"});
    final JComboBox<String> method = new JComboBox<>(new String[]{"Pearson", "Spearman"});
    final JComboBox<DataService.Source> xChoice = new JComboBox<>(DataService.SOURCES), yChoice = new JComboBox<>(DataService.SOURCES);
    final JSpinner lag = new JSpinner(new SpinnerNumberModel(0, -24, 24, 1));
    final JSpinner window = new JSpinner(new SpinnerNumberModel(24, 6, 120, 1));
    final JCheckBox euroSox = new JCheckBox("Semiconduttori in euro");
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
        for (final PatternStudy.Kind kind : PatternStudy.Kind.values()) patternRules.put(kind, new PatternStudy.Rules(3, 1.5, 3, 3, 6, 3, kind));
        try (final Reader reader = Files.newBufferedReader(directory.resolve("appearance.properties"))) {
            appearance.load(reader);
        } catch (final java.io.IOException ignored) { /* Prima apertura: palette predefinita. */ }
        for (int i = 0; i < seriesColors.length + 4; i++) {
            Color color;
            try {
                color = new Color(Integer.parseInt(appearance.getProperty("color." + i)));
            } catch (final RuntimeException ignored) {
                color = i < seriesColors.length ? DataService.SOURCES[i].color() : i == seriesColors.length ? inflationColor : i == seriesColors.length + 1 ? declineColor : i == seriesColors.length + 2 ? recoveryColor : inflationRiseColor;
            }
            if (i < seriesColors.length) seriesColors[i] = color;
            else if (i == seriesColors.length) inflationColor = color;
            else if (i == seriesColors.length + 1) declineColor = color;
            else if (i == seriesColors.length + 2) recoveryColor = color;
            else inflationRiseColor = color;
        }
        transform.setSelectedIndex(1);
        setLayout(new BorderLayout(18, 14));
        setBorder(new EmptyBorder(18, 22, 12, 22));
        setBackground(BG);

        final JPanel heading = row();
        final JPanel introduction = column();
        final JLabel title = new JLabel("MacroLab");
        title.setFont(new Font("Segoe UI", Font.BOLD, 29));
        introduction.add(title);
        introduction.add(Box.createVerticalStrut(3));
        introduction.add(label("OSSERVATORIO MACRO  /  Europa e mercati", MUTED));
        introduction.add(Box.createVerticalStrut(3));
        periodSummary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        periodSummary.setForeground(MUTED);
        introduction.add(periodSummary);
        heading.add(introduction);
        heading.add(Box.createHorizontalGlue());
        refresh.setToolTipText("Scarica le serie aggiornate. Se una fonte non risponde, usa la copia già salvata.");
        refresh.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        refresh.setBackground(ACCENT);
        refresh.setForeground(Color.WHITE);
        refresh.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT), new EmptyBorder(8, 13, 8, 13)));
        heading.add(refresh);
        heading.add(Box.createHorizontalStrut(7));
        final JButton offline = button("Dati salvati");
        offline.setToolTipText("Apri l'ultimo download senza collegarti a Internet.");
        heading.add(offline);
        heading.add(Box.createHorizontalStrut(7));
        final JButton export = button("Esporta dati");
        export.setToolTipText("Salva i dati e le correlazioni in CSV, più una nota con fonti e parametri.");
        heading.add(export);
        heading.add(Box.createHorizontalStrut(7));
        png.setToolTipText("Salva il grafico aperto come immagine PNG.");
        heading.add(png);
        add(heading, BorderLayout.NORTH);

        final JPanel settings = new Sidebar();
        settings.add(pairControls);
        settings.setBorder(new EmptyBorder(4, 0, 12, 16));
        settings.add(section("Periodo da analizzare"));
        final JPanel dates = new JPanel(new GridLayout(1, 2, 10, 0));
        dates.setOpaque(false);
        dates.setAlignmentX(Component.LEFT_ALIGNMENT);
        dates.setMinimumSize(new Dimension(0, 58));
        dates.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        dates.add(field("Dal mese", start));
        dates.add(field("Al mese", end));
        settings.add(dates);
        settings.add(paragraph("Dal 2000, dove la fonte dispone di dati. Formato anno-mese; mese corrente escluso.", 3));
        final JButton apply = button("Applica periodo");
        apply.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(apply);
        final JPanel presets = row();
        presets.setAlignmentX(Component.LEFT_ALIGNMENT);
        presets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        for (final int year : new int[]{2000, 2008, 2020}) {
            final JButton preset = button("" + year);
            preset.addActionListener((final ActionEvent e) -> { start.setText(year + "-01"); calculate(); });
            presets.add(preset);
            presets.add(Box.createHorizontalStrut(6));
        }
        settings.add(Box.createVerticalStrut(6));
        settings.add(presets);
        patternControls.add(section("Pattern di inflazione e mercati"));
        patternControls.add(field("Pattern da analizzare", patternKind));
        showPatterns.setOpaque(false);
        showPatterns.addActionListener((final ActionEvent e) -> overview.repaint());
        patternControls.add(showPatterns);
        patternControls.add(patternHelp);
        final JButton study = button("Apri lo studio degli episodi");
        study.addActionListener((final ActionEvent e) -> tabs.setSelectedIndex(6));
        patternControls.add(study);
        final JToggleButton configure = new JToggleButton("Soglie e colori…");
        configure.setAlignmentX(Component.LEFT_ALIGNMENT);
        patternControls.add(Box.createVerticalStrut(8));
        patternControls.add(configure);
        final JPanel options = column();
        options.setVisible(false);
        configure.addActionListener((final ActionEvent e) -> { options.setVisible(configure.isSelected()); revalidate(); });
        options.add(field("Variazione HICP: finestra in mesi", inflationMonths));
        options.add(field("Variazione minima HICP (punti %)", inflationDrop));
        options.add(field("Discesa: mesi dopo il segnale", declineMonths));
        options.add(field("Calo minimo di ogni mercato (%)", marketDrop));
        options.add(field("Recupero: mesi dal minimo", recoveryMonths));
        options.add(field("Rialzo minimo dal minimo (%)", rebound));
        options.add(paragraph("Confronto storico sui livelli. Il segnale è datato al mese HICP; la conferma richiede dati successivi.", 4));
        final JPanel bandColors = new JPanel(new GridLayout(2, 2, 6, 6));
        bandColors.setOpaque(false);
        bandColors.setAlignmentX(Component.LEFT_ALIGNMENT);
        bandColors.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        for (int i = 0; i < 4; i++) {
            final int band = i;
            final JButton color = button(new String[]{"Calo HICP", "Discesa", "Recupero", "Salita HICP"}[i]);
            color.setForeground(i == 0 ? inflationColor : i == 1 ? declineColor : i == 2 ? recoveryColor : inflationRiseColor);
            color.addActionListener((final ActionEvent e) -> {
                final Color chosen;
                if ((chosen = JColorChooser.showDialog(this, "Colore della banda", band == 0 ? inflationColor : band == 1 ? declineColor : band == 2 ? recoveryColor : inflationRiseColor)) != null) {
                    if (band == 0) inflationColor = chosen;
                    else if (band == 1) declineColor = chosen;
                    else if (band == 2) recoveryColor = chosen;
                    else inflationRiseColor = chosen;
                    color.setForeground(chosen);
                    saveAppearance();
                    overview.repaint();
                }
            });
            bandColors.add(color);
        }
        options.add(bandColors);
        patternControls.add(options);
        settings.add(patternControls);
        patternKind.addActionListener((final ActionEvent e) -> selectPattern());
        for (final JSpinner control : new JSpinner[]{inflationMonths, inflationDrop, declineMonths, marketDrop, recoveryMonths, rebound}) {
            control.addChangeListener((final javax.swing.event.ChangeEvent e) -> calculate());
        }
        settings.add(section("Come confrontare i dati"));
        settings.add(field("Valori da usare", transform));
        settings.add(transformHelp);
        settings.add(field("Metodo di correlazione", method));
        settings.add(methodHelp);
        euroSox.setOpaque(false);
        euroSox.setAlignmentX(Component.LEFT_ALIGNMENT);
        euroSox.setToolTipText("SOX è quotato in dollari. La conversione in euro usa il cambio EUR/USD di ogni mese.");
        settings.add(euroSox);
        settings.add(currencyHelp);

        pairControls.add(section("Due indicatori a confronto"));
        pairControls.add(field("Primo indicatore (asse X)", xChoice));
        pairControls.add(Box.createVerticalStrut(8));
        pairControls.add(field("Secondo indicatore (asse Y)", yChoice));
        yChoice.setSelectedIndex(1);
        pairControls.add(Box.createVerticalStrut(10));
        pairControls.add(field("Sposta il secondo indicatore (mesi)", lag));
        lag.setToolTipText("0: stesso mese. +3: X a gennaio e Y ad aprile. -3: X ad aprile e Y a gennaio.");
        pairControls.add(lagHelp);
        rollingControls.add(field("Mesi in ogni calcolo", window));
        rollingControls.add(paragraph("Con 24 mesi, ogni punto usa i due anni che terminano in quel mese. Una finestra breve mostra più oscillazioni.", 4));
        pairControls.add(rollingControls);
        settings.add(Box.createVerticalGlue());
        final JScrollPane settingsScroll = new JScrollPane(settings);
        settingsScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, GRID));
        settingsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        settingsScroll.setPreferredSize(new Dimension(300, 400));
        settingsScroll.getViewport().setBackground(BG);
        settingsScroll.getVerticalScrollBar().setUnitIncrement(18);
        add(settingsScroll, BorderLayout.WEST);

        final JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        final JPanel pageHeading = column();
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 19));
        pageTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        pageHeading.add(pageTitle);
        pageHeading.add(Box.createVerticalStrut(5));
        pageHelp.setPreferredSize(new Dimension(0, 60));
        pageHeading.add(pageHelp);
        content.add(pageHeading, BorderLayout.NORTH);

        final JPanel linePage = new JPanel(new BorderLayout());
        linePage.setBackground(PANEL);
        final JPanel legend = new JPanel(new GridLayout(0, 3, 12, 5));
        legend.setBackground(PANEL);
        legend.setBorder(new EmptyBorder(12, 15, 8, 15));
        for (int i = 0; i < visible.length; i++) {
            visible[i] = new JCheckBox(i == 2 ? "Inflazione (HICP)" : DataService.SOURCES[i].shortName(), i == 0 || i == 2 || i == 4 || i == 5);
            visible[i].setForeground(INK);
            visible[i].setOpaque(false);
            visible[i].setToolTipText("<html><body width='350'>" + DataService.SOURCES[i].description() + "</body></html>");
            visible[i].addActionListener((final ActionEvent e) -> overview.repaint());
            final int index = i;
            final JPanel item = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
            item.setOpaque(false);
            item.add(visible[i]);
            colorButtons[i] = button("");
            colorButtons[i].setIcon(new Icon() {
                public int getIconWidth() { return 16; }
                public int getIconHeight() { return 16; }
                public void paintIcon(final Component component, final Graphics graphics, final int x, final int y) {
                    final Graphics2D dot = (Graphics2D) graphics.create();
                    try {
                        dot.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        dot.setColor(component.getForeground());
                        dot.fillOval(x + 2, y + 2, 11, 11);
                        dot.setColor(MUTED);
                        dot.drawOval(x + 2, y + 2, 11, 11);
                    } finally { dot.dispose(); }
                }
            });
            colorButtons[i].setForeground(seriesColors[i]);
            colorButtons[i].setBorder(new EmptyBorder(1, 6, 1, 6));
            colorButtons[i].setToolTipText("Scegli colore · " + DataService.SOURCES[i].name());
            colorButtons[i].getAccessibleContext().setAccessibleName("Colore " + DataService.SOURCES[i].name());
            colorButtons[i].addActionListener((final ActionEvent e) -> {
                final Color chosen;
                if ((chosen = JColorChooser.showDialog(this, "Colore · " + DataService.SOURCES[index].name(), seriesColors[index])) != null) {
                    seriesColors[index] = chosen;
                    colorButtons[index].setForeground(chosen);
                    saveAppearance();
                    overview.repaint();
                }
            });
            item.add(colorButtons[i]);
            legend.add(item);
        }
        linePage.add(legend, BorderLayout.NORTH);
        linePage.add(overview, BorderLayout.CENTER);
        tabs.addTab("Andamento", linePage);
        tabs.addTab("Correlazioni", matrix);
        tabs.addTab("Due indicatori", scatter);
        tabs.addTab("Nel tempo", rolling);
        tabs.setToolTipTextAt(0, "Sovrapponi le serie per confrontarne l'andamento.");
        tabs.setToolTipTextAt(1, "Leggi la relazione tra ogni coppia di indicatori nello stesso mese.");
        tabs.setToolTipTextAt(2, "Ogni punto mette a confronto due osservazioni mensili.");
        tabs.setToolTipTextAt(3, "Verifica come cambia la correlazione da un periodo all'altro.");

        final JTable dataTable = new JTable(dataRows);
        dataTable.setRowHeight(36);
        dataTable.setFillsViewportHeight(true);
        dataTable.setShowVerticalLines(false);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.getTableHeader().setReorderingAllowed(false);
        dataTable.getColumnModel().getColumn(0).setPreferredWidth(270);
        final JTextArea dataDescription = paragraph("Seleziona una riga per sapere che cosa misura l'indicatore.", 5);
        dataTable.getSelectionModel().addListSelectionListener((final javax.swing.event.ListSelectionEvent e) -> {
            final int selected;
            if ((selected = dataTable
                    .getSelectedRow()) >= 0) {
                dataDescription.setText(DataService.SOURCES[selected].description());
            }
        });
        final JPanel dataPage = new JPanel(new BorderLayout(0, 12));
        dataPage.setBackground(PANEL);
        dataPage.setBorder(new EmptyBorder(14, 14, 14, 14));
        final JScrollPane dataScroll = new JScrollPane(dataTable);
        dataScroll.setColumnHeaderView(dataTable.getTableHeader());
        dataScroll.setBorder(BorderFactory.createLineBorder(GRID));
        dataPage.add(dataScroll, BorderLayout.CENTER);
        dataPage.add(dataDescription, BorderLayout.SOUTH);
        tabs.addTab("Dati", dataPage);
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setMargin(new Insets(20, 22, 20, 22));
        notes.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabs.addTab("Fonti", new JScrollPane(notes));
        final JTable eventTable = new JTable(patternRows) {
            public String getToolTipText(final MouseEvent e) {
                final int row = rowAtPoint(e.getPoint());
                return row < 0 ? null : eventText(events.get(convertRowIndexToModel(row)));
            }
        };
        eventTable.setRowHeight(42);
        eventTable.setShowVerticalLines(false);
        eventTable.setFillsViewportHeight(true);
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.getTableHeader().setReorderingAllowed(false);
        eventTable.getColumnModel().getColumn(5).setPreferredWidth(155);
        eventTable.getSelectionModel().addListSelectionListener((final javax.swing.event.ListSelectionEvent e) -> {
            final int selected;
            if ((selected = eventTable.getSelectedRow()) >= 0) eventDetails.setText(eventText(events.get(selected)));
        });
        final JPanel patternPage = new JPanel(new BorderLayout(0, 12));
        patternPage.setBackground(PANEL);
        patternPage.setBorder(new EmptyBorder(14, 16, 14, 16));
        patternPage.add(patternSummary, BorderLayout.NORTH);
        final JScrollPane eventScroll = new JScrollPane(eventTable);
        eventScroll.setColumnHeaderView(eventTable.getTableHeader());
        eventScroll.setBorder(BorderFactory.createLineBorder(GRID));
        patternPage.add(eventScroll, BorderLayout.CENTER);
        eventDetails.setPreferredSize(new Dimension(0, 130));
        eventDetails.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        final JPanel eventFooter = new JPanel(new BorderLayout(0, 6));
        eventFooter.setOpaque(false);
        eventFooter.add(eventDetails, BorderLayout.CENTER);
        final JButton focusEvent = button("Inquadra l'episodio nel grafico");
        focusEvent.addActionListener((final ActionEvent e) -> {
            final int selected;
            if ((selected = eventTable.getSelectedRow()) < 0) { status.setText("Seleziona prima un episodio nella tabella."); return; }
            final PatternStudy.Event event = events.get(selected);
            final YearMonth currentEnd = first.plusMonths(size - 1);
            final YearMonth focusStart = studyFirst.plusMonths(event.start()).minusMonths(3);
            start.setText((focusStart.isBefore(YearMonth.of(2000, 1)) ? YearMonth.of(2000, 1) : focusStart).toString());
            final YearMonth focusEnd = studyFirst.plusMonths(event.signal() + appliedRules.horizonMonths() + 3);
            end.setText((focusEnd.isAfter(currentEnd) ? currentEnd : focusEnd).toString());
            transform.setSelectedIndex(1);
            for (int i = 0; i < visible.length; i++) visible[i].setSelected(i == 0 || i == 2 || i == 4 || i == 5);
            showPatterns.setSelected(true);
            calculate();
            tabs.setSelectedIndex(0);
        });
        eventFooter.add(focusEvent, BorderLayout.SOUTH);
        patternPage.add(eventFooter, BorderLayout.SOUTH);
        tabs.addTab("Pattern", patternPage);
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            protected void installDefaults() {
                super.installDefaults();
                tabInsets = new Insets(9, 12, 9, 12);
                contentBorderInsets = new Insets(1, 0, 0, 0);
                tabAreaInsets = new Insets(0, 0, 0, 0);
            }
            protected void paintTabBackground(final Graphics g, final int placement, final int index, final int x, final int y, final int width, final int height, final boolean selected) {
                g.setColor(selected ? PANEL : BG);
                g.fillRect(x, y, width, height);
            }
            protected void paintTabBorder(final Graphics g, final int placement, final int index, final int x, final int y, final int width, final int height, final boolean selected) {
                if (selected) { g.setColor(ACCENT); g.fillRect(x + 10, y + height - 3, width - 20, 2); }
            }
            protected void paintContentBorder(final Graphics g, final int placement, final int selected) {
                g.setColor(GRID);
                g.drawLine(0, calculateTabAreaHeight(placement, runCount, maxTabHeight), tabPane.getWidth(), calculateTabAreaHeight(placement, runCount, maxTabHeight));
            }
        });
        content.add(tabs, BorderLayout.CENTER);
        insight.setForeground(INK);
        /* Prima apertura: l'area era nascosta, quindi la larghezza può essere 0.
           Il testo a capo chiederebbe migliaia di pixel e schiaccerebbe il grafico. */
        insight.setPreferredSize(new Dimension(0, 75));
        insight.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, GRID), new EmptyBorder(10, 0, 0, 0)));
        content.add(insight, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
        status.setForeground(MUTED);
        status.setBorder(new EmptyBorder(6, 0, 0, 0));
        add(status, BorderLayout.SOUTH);

        tabs.addChangeListener((final javax.swing.event.ChangeEvent e) -> updateHints());
        refresh.addActionListener((final ActionEvent e) -> load(true));
        offline.addActionListener((final ActionEvent e) -> load(false));
        apply.addActionListener((final ActionEvent e) -> calculate());
        start.addActionListener((final ActionEvent e) -> calculate());
        end.addActionListener((final ActionEvent e) -> calculate());
        transform.addActionListener((final ActionEvent e) -> { updateHints(); calculate(); });
        method.addActionListener((final ActionEvent e) -> { updateHints(); calculate(); });
        euroSox.addActionListener((final ActionEvent e) -> { updateHints(); calculate(); });
        xChoice.addActionListener((final ActionEvent e) -> calculate());
        yChoice.addActionListener((final ActionEvent e) -> calculate());
        lag.addChangeListener((final javax.swing.event.ChangeEvent e) -> { updateHints(); calculate(); });
        window.addChangeListener((final javax.swing.event.ChangeEvent e) -> calculate());
        export.addActionListener((final ActionEvent e) -> save(false));
        png.addActionListener((final ActionEvent e) -> save(true));
        updateData();
        updateHints();
    }

    static final class Sidebar extends JPanel implements Scrollable {
        Sidebar() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }
        /* La colonna segue la larghezza disponibile; solo l'altezza scorre.
           Altrimenti i campi data allargano la vista e tagliano le spiegazioni. */
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(300, 400); }
        public int getScrollableUnitIncrement(final Rectangle visible, final int orientation, final int direction) { return 18; }
        public int getScrollableBlockIncrement(final Rectangle visible, final int orientation, final int direction) { return Math.max(18, visible.height - 18); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static JPanel column() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    static JTextArea paragraph(final String text, final int rows) {
        final JTextArea area = new JTextArea(text, rows, 20);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        area.setForeground(MUTED);
        area.setMargin(new Insets(7, 0, 5, 0));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * 19 + 12));
        return area;
    }

    static JLabel section(final String text) {
        final JLabel heading = new JLabel(text);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 14));
        heading.setBorder(new EmptyBorder(15, 0, 10, 0));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        return heading;
    }

    static JPanel field(final String title, final JComponent control) {
        final JPanel group = column();
        final JLabel caption = new JLabel(title);
        caption.setLabelFor(control);
        group.add(caption);
        control.getAccessibleContext().setAccessibleName(title);
        group.add(Box.createVerticalStrut(5));
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMinimumSize(new Dimension(0, 32));
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        control.setPreferredSize(new Dimension(220, 32));
        group.add(control);
        group.setMinimumSize(new Dimension(0, 56));
        group.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        return group;
    }

    void selectPattern() {
        patternRules.put(displayedPattern, currentPatternRules(displayedPattern));
        final PatternStudy.Rules next = patternRules.get((PatternStudy.Kind) patternKind.getSelectedItem());
        displayedPattern = next.kind();
        switchingPattern = true;
        try {
            inflationMonths.setValue(next.inflationMonths());
            inflationDrop.setValue(next.inflationDrop());
            declineMonths.setValue(next.declineMonths());
            marketDrop.setValue(next.marketDrop());
            recoveryMonths.setValue(next.recoveryMonths());
            rebound.setValue(next.rebound());
        } finally { switchingPattern = false; }
        updateHints();
        calculate();
    }

    PatternStudy.Rules currentPatternRules(final PatternStudy.Kind kind) {
        return new PatternStudy.Rules((int) inflationMonths.getValue(), ((Number) inflationDrop.getValue()).doubleValue(),
                (int) declineMonths.getValue(), ((Number) marketDrop.getValue()).doubleValue(),
                (int) recoveryMonths.getValue(), ((Number) rebound.getValue()).doubleValue(), kind);
    }

    void updateHints() {
        final int tab = tabs.getSelectedIndex();
        final boolean recoveryRequired = patternKind.getSelectedItem() == PatternStudy.Kind.DISINFLATION_REBOUND;
        recoveryMonths.setEnabled(recoveryRequired);
        rebound.setEnabled(recoveryRequired);
        patternHelp.setText(recoveryRequired
                ? "Banda ocra: calo HICP. Barre rosse e verdi: discesa e recupero dei tre mercati, solo nei casi confermati."
                : "Banda viola: salita HICP. Barre rosse: discesa dei tre mercati nei casi confermati. Nessun recupero richiesto.");
        pairControls.setVisible(tab == 2 || tab == 3);
        rollingControls.setVisible(tab == 3);
        insight.setVisible(tab == 2 || tab == 3);
        png.setEnabled(tab < 4);
        patternControls.setVisible(tab == 0 || tab == 6);
        method.setEnabled(tab >= 1 && tab <= 3);
        transformHelp.setText(transform.getSelectedIndex() == 0
                ? "Confronta i cambiamenti mensili. Prezzo 100 → 102: +2%. Tasso 3% → 3,25%: +0,25 punti."
                : "Usa i valori originali. Un trend comune può far apparire una correlazione: leggila con cautela.");
        methodHelp.setText(tab == 0 ? "Usato nelle tre viste di correlazione. Non modifica le linee di questo grafico."
                : method.getSelectedIndex() == 0 ? "Pearson misura quanto i valori seguono un legame lineare. I valori molto estremi possono influenzarlo."
                : "Spearman confronta l'ordine dei valori: cerca un legame crescente o decrescente, anche quando non segue una retta.");
        currencyHelp.setText(euroSox.isSelected()
                ? "Il SOX è convertito da dollari a euro. Il suo andamento comprende quindi anche l'effetto del cambio."
                : "Il SOX resta in dollari. Seleziona questa opzione per confrontarlo dal punto di vista di chi investe in euro.");
        final int delay = (int) lag.getValue();
        lagHelp.setText(delay == 0 ? "Stai confrontando i due indicatori nello stesso mese."
                : delay > 0 ? "Il secondo indicatore è osservato " + delay + " mesi dopo il primo. Esempio con +3: X a gennaio, Y ad aprile."
                : "Il secondo indicatore è osservato " + -delay + " mesi prima del primo. Esempio con -3: X ad aprile, Y a gennaio.");
        pageTitle.setText(switch (tab) {
            case 0 -> "Come si muovono gli indicatori";
            case 1 -> "Quanto sono legati tra loro?";
            case 2 -> "Il confronto tra due indicatori";
            case 3 -> "Il legame cambia nel tempo?";
            case 4 -> "I dati che stai utilizzando";
            case 6 -> "Il pattern regge nei dati?";
            default -> "Fonti e dettagli dei calcoli";
        });
        pageHelp.setText(switch (tab) {
            case 0 -> "Seleziona le serie; il punto colorato cambia il colore. Scala comune: 0 = media del periodo. Le bande evidenziano gli episodi; passa sul grafico per date e dettagli.";
            case 1 -> "Verde: movimenti concordi. Rosso: opposti. Vicino a 0: legame debole. Le celle confrontano gli stessi mesi. Passa su una cella per leggere gli indicatori o cliccala per approfondire.";
            case 2 -> "Ogni punto è una coppia di osservazioni. Il primo indicatore è sull'asse orizzontale, il secondo su quello verticale. A sinistra puoi scegliere le serie e spostare i mesi.";
            case 3 -> "Ogni punto usa solo i mesi della finestra scelta. La linea mostra come cambia il legame; il riepilogo in basso riguarda invece l’intero periodo. I tratti vuoti non sono calcolabili.";
            case 4 -> "Ultimi valori disponibili, indipendenti dal filtro. La copertura indica lo storico realmente scaricato: nessuna ricostruzione prima dell’avvio degli ETF.";
            case 6 -> recoveryRequired
                    ? "Calo HICP: tutti gli attraversamenti di soglia, inclusi gli insuccessi. Conferma se EUR/USD, ETF BTP Italia ed EURO STOXX 50 scendono e poi recuperano."
                    : "Salita HICP: tutti gli attraversamenti di soglia, inclusi gli insuccessi. Conferma se EUR/USD, ETF BTP Italia ed EURO STOXX 50 scendono nella finestra scelta. I minimi possono cadere in mesi diversi.";
            default -> "Qui trovi la provenienza dei dati, le date dei download e le formule usate. Le correlazioni descrivono un'associazione, non dimostrano un rapporto di causa ed effetto.";
        });
        revalidate();
    }

    static JPanel row() { final JPanel row = new JPanel(); row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS)); row.setOpaque(false); return row; }
    static JLabel label(final String text, final Color color) { final JLabel label = new JLabel(text); label.setForeground(color); return label; }
    static JButton button(final String text) {
        final JButton b = new JButton(text);
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setBackground(PANEL);
        b.setForeground(INK);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GRID), new EmptyBorder(8, 12, 8, 12)));
        return b;
    }
    static String number(final double d) { return Double.isFinite(d) ? String.format(Locale.ITALY, "%.2f", d) : "n/d"; }

    void load(final boolean online) {
        if (!refresh
                .isEnabled()) return;
        refresh.setEnabled(false); status.setText(online ? "Sto aggiornando i dati da BCE e Yahoo Finance…" : "Apertura dei dati salvati…");
        new SwingWorker<DataService.Dataset, Void>() {
            protected DataService.Dataset doInBackground() throws Exception { return DataService.load(directory, online); }
            protected void done() {
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
                origin = series.values().firstKey() + " → " + series.values().lastKey() + (series.status().startsWith("Online") ? " · online" : " · cache");
            }
            dataRows.addRow(new Object[]{source.name(), value, month, origin});
        }
    }

    void calculate() {
        if (dataset == null || switchingPattern) return;
        try {
            final YearMonth from = YearMonth.parse(start.getText().trim()), to = YearMonth.parse(end.getText().trim());
            final long count;
            if ((count = java.time.temporal.ChronoUnit.MONTHS
                    .between(from, to) + 1) < 3
                    || count > 600) throw new IllegalArgumentException("Scegli un intervallo tra 3 e 600 mesi.");
            if (from.isBefore(YearMonth.of(2000, 1))) throw new IllegalArgumentException("Lo studio parte da gennaio 2000.");
            if (!to
                    .isBefore(YearMonth.now(java.time.ZoneId.of("Europe/Rome")))) throw new IllegalArgumentException("Il mese corrente è incompleto: scegli al massimo il mese scorso.");
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
            appliedRules = currentPatternRules(displayedPattern);
            patternRules.put(appliedRules.kind(), appliedRules);
            /* Rilevamento su tutto lo storico fino al limite scelto. Cambiare il
               bordo sinistro non sposta i segnali e non cancella il loro contesto. */
            events = PatternStudy.detect(dataset.align(studyFirst, to, false), appliedRules).stream()
                    .filter((final PatternStudy.Event event) -> !studyFirst.plusMonths(event.signal()).isBefore(from))
                    .toList();
            updatePatterns();
            /* Leggo metodo e ritardo fuori dai cicli; nessun accesso ripetuto ai
               controlli Swing durante la costruzione delle serie derivate. */
            final boolean spearman = method.getSelectedIndex() == 1;
            correlations = new Stats.Correlation[analysis.length][analysis.length];
            for (int i = 0; i < analysis.length; i++) {
                for (int j = i; j < analysis.length; j++) {
                    /* Ritardo zero: r(X,Y) = r(Y,X), anche eliminando i mancanti
                       per coppia. Nove serie: 9*10/2 = 45 calcoli invece di 81. */
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
            insight.setText("Intero periodo — correlazione: " + number(c.value()) + "   ·   " + c.count() + " mesi confrontabili" + (c.count() < 24 ? "   ·   Poche osservazioni" : "")
                    + "\n" + (Double.isFinite(c.value()) ? "Da −1 (movimenti opposti) a +1 (concordi). Il risultato non dimostra una causa." : "Servono almeno tre coppie valide e due serie non costanti per calcolarla."));
            int available = 0;
            for (final DataService.Series s : dataset.series()) if (!s
                    .values()
                    .isEmpty()) available++;
            status.setText(available + " indicatori disponibili su " + DataService.SOURCES.length + "  ·  Periodo applicato: " + from + " → " + to + "  ·  " + events.size() + " episodi HICP");
            periodSummary.setText("Periodo applicato: " + from + " — " + to);
            notes.setText(report()); notes.setCaretPosition(0); repaint();
        } catch (final Exception e) { size = 0; events = List.of(); patternRows.setRowCount(0); patternSummary.setText("Periodo non valido: correggi i parametri."); periodSummary.setText("Periodo non valido"); insight.setText("Parametri non validi: correggi il periodo e premi Applica."); status.setText("Controlla i parametri: " + e.getMessage()); repaint(); }
    }

    String name(final int index) { return DataService.SOURCES[index].shortName() + (index == 3 ? (euroSox.isSelected() ? " EUR" : " USD") : ""); }
    Stats.Correlation pair(final int from, final int to, final int delay) { return Stats.correlate(analysis[xChoice.getSelectedIndex()], analysis[yChoice.getSelectedIndex()], from, to, delay, method.getSelectedIndex() == 1); }

    void saveAppearance() {
        for (int i = 0; i < seriesColors.length + 4; i++) appearance.setProperty("color." + i,
                Integer.toString((i < seriesColors.length ? seriesColors[i] : i == seriesColors.length ? inflationColor : i == seriesColors.length + 1 ? declineColor : i == seriesColors.length + 2 ? recoveryColor : inflationRiseColor).getRGB()));
        try {
            Files.createDirectories(directory);
            try (final Writer writer = Files.newBufferedWriter(directory.resolve("appearance.properties"))) {
                appearance.store(writer, "MacroLab palette");
            }
        } catch (final java.io.IOException e) { status.setText("Colore applicato; salvataggio palette fallito: " + e.getMessage()); }
    }

    void updatePatterns() {
        patternRows.setRowCount(0);
        eventDetails.setText("Seleziona un episodio per leggere minimi e date di verifica. Le percentuali sono le discese dai livelli iniziali; i minimi possono cadere in mesi diversi.");
        int confirmed = 0, rejected = 0, missing = 0, pending = 0;
        for (final PatternStudy.Event event : events) {
            switch (event.outcome()) {
                case CONFIRMED -> confirmed++;
                case REJECTED -> rejected++;
                case MISSING -> missing++;
                case PENDING -> pending++;
            }
            final Object[] row = new Object[7];
            row[0] = studyFirst.plusMonths(event.signal());
            row[1] = (event.inflationChange() > 0 ? "+" : "") + number(event.inflationChange());
            for (int i = 0; i < event.legs().size(); i++) row[2 + i] = Double.isFinite(event.legs().get(i).fall()) ? number(event.legs().get(i).fall()) + "%" : "n/d";
            row[5] = event.outcome();
            row[6] = studyFirst.plusMonths(event.signal() + appliedRules.horizonMonths());
            patternRows.addRow(row);
        }
        patternSummary.setText((appliedRules.kind().recoveryRequired ? "Calo HICP" : "Salita HICP") + " · " + events.size() + " episodi  /  " + confirmed + " confermati su " + (confirmed + rejected) + " valutabili"
                + (confirmed + rejected > 0 ? " (" + number(100.0 * confirmed / (confirmed + rejected)) + "%)" : "")
                + "\n" + rejected + " non confermati · " + pending + " in attesa · " + missing + " con dati insufficienti. "
                + "Frequenza descrittiva, senza test statistico; episodi eventualmente sovrapposti.");
    }

    String eventText(final PatternStudy.Event event) {
        final StringBuilder text = new StringBuilder("Segnale ").append(studyFirst.plusMonths(event.signal()))
                .append(" · ").append(event.kind()).append(" · HICP ").append(event.inflationChange() > 0 ? "+" : "").append(number(event.inflationChange())).append(" pp dal ").append(studyFirst.plusMonths(event.start()))
                .append(" · ").append(event.outcome());
        for (final PatternStudy.Leg leg : event.legs()) {
            text.append("\n").append(DataService.SOURCES[leg.market()].shortName()).append(": ").append(leg.outcome());
            if (leg.trough() >= 0) {
                text.append(" · minimo ").append(studyFirst.plusMonths(leg.trough())).append(" (").append(number(leg.fall())).append("%)");
                if (event.kind().recoveryRequired) text.append(" · rialzo max ").append(number(leg.bounce())).append("% · soglia recupero ")
                        .append(leg.recovery() < 0 ? "non raggiunta" : studyFirst.plusMonths(leg.recovery()));
            }
        }
        return text.append("\nVerifica completa entro ").append(studyFirst.plusMonths(event.signal() + appliedRules.horizonMonths()))
                .append(". Analisi retrospettiva; HICP per mese di riferimento, non di pubblicazione.").toString();
    }

    String patternMethod() {
        return "Studio: " + appliedRules.kind() + "\n"
                + (appliedRules.kind().recoveryRequired ? "Calo" : "Salita") + " HICP di almeno " + number(appliedRules.inflationDrop()) + " punti percentuali in " + appliedRules.inflationMonths() + " mesi. "
                + "Segnale al primo attraversamento della soglia nella direzione scelta; un solo episodio contiguo sopra soglia. "
                + "Baseline mercati: livello all'inizio della finestra HICP. Minimo dalla baseline inclusa fino a " + appliedRules.declineMonths() + " mesi dopo il segnale. "
                + "Discesa richiesta almeno " + number(appliedRules.marketDrop()) + "% in ciascuno di EUR/USD, ETF BTP Italia ed EURO STOXX 50. "
                + (appliedRules.kind().recoveryRequired
                        ? "Serve anche un recupero di almeno " + number(appliedRules.rebound()) + "% dal rispettivo minimo entro " + appliedRules.recoveryMonths() + " mesi. "
                        : "Nessun recupero richiesto o analizzato: le impostazioni del recupero sono ignorate e non allungano la finestra. ")
                + "I minimi possono cadere in mesi diversi, anche prima del segnale: non si richiede una discesa sincronizzata né una discesa di tutti i mercati mondiali. "
                + "Tutti i mesi fino a segnale + " + appliedRules.horizonMonths() + " devono essere disponibili; una coda incompleta resta in attesa, un buco interno rende la gamba non valutabile. Un fallimento completo di una gamba basta a respingere il pattern comune. "
                + "Frequenza = confermati / (confermati + non confermati); dati insufficienti e attese sono esclusi. "
                + "Calcolo sui livelli originali, indipendente da trasformazione, valuta SOX, serie visibili e bordo sinistro. Il termine finale limita anche i dati di conferma. "
                + (appliedRules.kind().recoveryRequired
                        ? "Banda ocra: calo HICP; corsie rosse/verdi: discesa/recupero nei casi confermati. "
                        : "Banda viola: salita HICP; corsie rosse: discesa nei casi confermati. ")
                + "Colori personalizzabili. Ordine delle tre corsie: EUR/USD, BTP, EURO 50. La discesa usa il prezzo iniziale, non un massimo mobile. "
                + "Chiusure mensili non rettificate: cedole/dividendi possono influire. Analisi retrospettiva con mesi successivi, HICP per mese di riferimento, non di pubblicazione. "
                + "Le soglie sono esplorative e modificabili; nessuna prova di causalità o previsione.\n\n";
    }

    String report() {
        final StringBuilder text = new StringBuilder("Analisi dei mercati\n\n");
        text.append("Periodo: ").append(first).append(" → ").append(first.plusMonths(size - 1)).append("\nMetodo: ").append(method.getSelectedItem()).append("; ").append(transform.getSelectedItem()).append("\nSOX: ").append(euroSox.isSelected() ? "convertito in EUR" : "USD").append("\n").append(insight.getText()).append("\n\n");
        text.append("Come leggere i risultati\nr va da −1 (associazione inversa) a +1 (diretta); vicino a 0 indica poca associazione secondo il metodo scelto. Pearson misura il legame lineare, Spearman quello monotono con ranghi medi per i pareggi. n indica le coppie valide. Una serie costante o meno di 3 coppie restituiscono n/d. Nessun test di significatività o inferenza causale è effettuato.\n\n");
        text.append("Calcoli\nFrequenza mensile; mese corrente escluso. Prezzi e cambi: 100 × (P[t]/P[t−1] − 1). Tassi, rendimento decennale e inflazione a/a: x[t] − x[t−1] in punti percentuali. Non si tratta dell'inflazione mensile. Sono usati mesi consecutivi, senza interpolare dati mancanti. La prima variazione del filtro usa il mese precedente. Matrice: correlazioni contemporanee, esclusione dei mancanti per coppia, campioni eventualmente diversi.\n\n");
        text.append("Grafici\nAndamento: z-score delle serie selezionate nel periodo, media 0 e deviazione standard campionaria 1; non è un rendimento cumulato. Confronto: valori nelle unità della trasformazione. Ritardo positivo k confronta X[t] con Y[t+k]: X precede Y. Ritardo negativo: Y precede X. La matrice ignora il ritardo. Correlazione mobile: finestra di ").append(window.getValue()).append(" mesi di calendario; entrambe le osservazioni devono ricadere nella finestra; ritardo ").append(lag.getValue()).append(".\n\n");
        text.append("Interpretazione\nA parità di flussi e scadenza, un aumento del rendimento richiesto riduce il prezzo di un'obbligazione. Il tasso BCE non coincide con il rendimento dei BTP: contano anche aspettative e rischio sovrano. Inflazione, banche, cambio e semiconduttori possono rispondere a più fattori comuni; segni e intensità cambiano nel tempo. I dati di inflazione sono riferiti al mese economico e possono essere rivisti: questa analisi non è un backtest con informazioni disponibili in tempo reale.\n\n");
        text.append("Indicatori utilizzati\nETF BTP e banche sono proxy espliciti, con costi, composizione e stacchi cedolari propri. Prezzi di chiusura NON rettificati: non includono reinvestimento di dividendi o cedole. Un singolo BTP richiede ISIN e prezzi storici coerenti. Il rendimento italiano 10 anni è una media mensile; gli altri mercati e il tasso BCE sono di fine mese. La valuta base influenza le correlazioni: SOX in EUR = SOX in USD / USD per EUR, senza copertura valutaria.\n\n");
        text.append("Fonti e disponibilità\nDownload BCE ufficiale via SDMX; quotazioni Yahoo Finance tramite endpoint pubblico non contrattualizzato, soggetto a cambiamenti o limiti. Nessun dato simulato. In caso di errore si usa solo la cache disponibile, identificata qui sotto. Gli ultimi valori nella tabella Dati sono quelli disponibili e non dipendono dal filtro.\n\n");
        text.append("Storico dal 2000\nSi richiedono anche osservazioni precedenti per le finestre e la prima variazione. La selezione parte da gennaio 2000; gli ETF mantengono l'inizio effettivo restituito dalla fonte. Nessuna estensione sintetica né sostituzione retroattiva di uno strumento.\n\n");
        text.append(patternMethod()).append(patternSummary.getText()).append("\n\n");
        for (final PatternStudy.Event event : events) text.append(eventText(event)).append("\n\n");
        text.append("Riferimenti settore auto e cambio\nhttps://stoxx.com/index/SXAP/\nhttps://www.ishares.com/uk/individual/en/products/251932/ishares-stoxx-europe-600-automobiles-parts-ucits-etf-de-fund\nhttps://www.ecb.europa.eu/press/economic-bulletin/articles/2020/html/ecb.ebart202003_01~7fc0abdec2.en.html\n\n");
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
                .showSaveDialog(this)
                != JFileChooser.APPROVE_OPTION)
            return;
        try {
            final Path folder = chooser.getSelectedFile().toPath();
            if (image) {
                final Component component = tabs.getSelectedComponent();
                final BufferedImage picture = new BufferedImage(
                        component.getWidth(),
                        component.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                final Graphics2D graphics = picture.createGraphics();
                component.paint(graphics);
                graphics.dispose();
                ImageIO.write(picture,
                        "png", folder.resolve("macrolab-grafico-"
                                        + System.currentTimeMillis()
                                        + ".png")
                                .toFile());
            } else export(folder);
            status.setText("Esportazione completata in " + folder);
        } catch (final Exception e) {
            status.setText("Esportazione fallita: " + e.getMessage());
        }
    }

    void export(final Path folder) throws Exception {
        final String suffix = "-" + System.currentTimeMillis();
        for (int kind = 0; kind < 2; kind++) {
            final StringBuilder csv = new StringBuilder("month");
            for (int i = 0; i < DataService.SOURCES.length; i++)
                csv.append(',')
                        .append(DataService.SOURCES[i].id())
                        .append(i == 3
                                && euroSox
                                .isSelected()
                                ? "_eur"
                                : "");
            csv.append('\n');
            for (int t = 0; t < size; t++) {
                csv.append(first.plusMonths(t));
                for (final double[] series : kind == 0
                        ? levels
                        : analysis) {
                    csv.append(',');
                    if (Double.isFinite(series[t]))
                        csv.append(series[t]);
                }
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
        final StringBuilder eventCsv = new StringBuilder("pattern,signal,baseline,inflation_change_pp,event_outcome,verification_end,market,trough,fall_pct,recovery,rebound_max_pct,market_outcome\n");
        for (final PatternStudy.Event event : events) for (final PatternStudy.Leg leg : event.legs()) {
            eventCsv.append(event.kind().name()).append(',').append(studyFirst.plusMonths(event.signal())).append(',').append(studyFirst.plusMonths(event.start())).append(',')
                    .append(event.inflationChange()).append(',').append(event.outcome()).append(',')
                    .append(studyFirst.plusMonths(event.signal() + appliedRules.horizonMonths())).append(',')
                    .append(DataService.SOURCES[leg.market()].id()).append(',')
                    .append(leg.trough() < 0 ? "" : studyFirst.plusMonths(leg.trough())).append(',')
                    .append(Double.isFinite(leg.fall()) ? leg.fall() : "").append(',')
                    .append(leg.recovery() < 0 ? "" : studyFirst.plusMonths(leg.recovery())).append(',')
                    .append(Double.isFinite(leg.bounce()) ? leg.bounce() : "").append(',').append(leg.outcome()).append('\n');
        }
        Files.writeString(folder.resolve("episodi-pattern" + suffix + ".csv"), eventCsv);
        Files.writeString(folder.resolve("metodo-e-fonti" + suffix + ".txt"), report());
    }

    final class Plot extends JPanel {
        final int kind;
        Plot(final int kind) {
            this.kind = kind; setBackground(PANEL); setPreferredSize(new Dimension(1120, 430));
            ToolTipManager.sharedInstance().registerComponent(this);
            if (kind == 1) addMouseListener(new MouseAdapter() {
                public void mouseClicked(final MouseEvent e) {
                    if (size == 0) return;
                    final int cell;
                    if ((cell = Math.min((getWidth() - 180) / DataService.SOURCES.length, (getHeight() - 96) / DataService.SOURCES.length)) <= 0) return;
                    /* Coordinate acquisite prima dei controlli: preservo l'ordine
                       delle letture dell'evento, anche sui bordi esterni. */
                    final int x = (e.getX() - 142) / cell, y = (e.getY() - 64) / cell;
                    if (e
                            .getX() >= 142
                            && e
                            .getY() >= 64
                            && x < DataService.SOURCES.length
                            && y < DataService.SOURCES.length) { xChoice.setSelectedIndex(x); yChoice.setSelectedIndex(y); tabs.setSelectedIndex(2); }
                }
            });
        }
        public String getToolTipText(final MouseEvent e) {
            if (size == 0) return null;
            if (kind == 1) {
                final int cell;
                if ((cell = Math.min((getWidth() - 180) / DataService.SOURCES.length, (getHeight() - 96) / DataService.SOURCES.length)) <= 0
                        || e
                        .getX() < 142
                        || e
                        .getY() < 64) return null;
                final int x = (e.getX() - 142) / cell;
                final int y;
                if ((y = (e
                        .getY() - 64) / cell) >= DataService.SOURCES.length
                        || x >= DataService.SOURCES.length) return null;
                final Stats.Correlation c = correlations[x][y];
                return "<html>" + DataService.SOURCES[x].name() + " / " + DataService.SOURCES[y].name() + "<br>Correlazione: " + number(c.value()) + " · Mesi confrontabili: " + c.count() + "<br>Clicca per aprire il confronto.</html>";
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
            if (showPatterns.isSelected()) for (final PatternStudy.Event event : events) {
                if (!first.plusMonths(t).isBefore(studyFirst.plusMonths(event.start()))
                        && !first.plusMonths(t).isAfter(studyFirst.plusMonths(event.signal() + appliedRules.horizonMonths()))) {
                    tip.append("<br><br>").append(eventText(event).replace("\n", "<br>"));
                }
            }
            return tip.append("</html>").toString();
        }
        protected void paintComponent(final Graphics graphics) {
            super.paintComponent(graphics);
            final Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
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
                if (kind == 0 && showPatterns.isSelected()) paintPatterns(g, left, top, width, height);
                axes(g, left, top, width, height, min, max);
                g.setColor(INK); g.drawString(kind == 3 ? "Correlazione su " + window.getValue() + " mesi  ·  " + method.getSelectedItem() : "Scostamento dalla media  ·  " + transform.getSelectedItem(), left, 28);
                for (int i = 0; i < values.length; i++) {
                    if (kind == 0
                            && !visible[i]
                            .isSelected()) continue;
                    g.setColor(kind == 3 ? ACCENT : seriesColors[i]); g.setStroke(new BasicStroke(kind == 3 ? 2.6f : 1.8f));
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
                g.drawString(kind == 3 ? "Passa sulla linea per leggere correlazione e numero di mesi." : showPatterns.isSelected() ? appliedRules.kind().recoveryRequired ? "Calo HICP · barre: EUR/USD, BTP, EURO 50 · discesa / recupero" : "Salita HICP · barre: EUR/USD, BTP, EURO 50 · discesa" : "Scala comune: 1 = una deviazione standard; non è un rendimento.", left, getHeight() - 10);
            } finally { g.dispose(); }
        }
        void paintPatterns(final Graphics2D g, final int left, final int top, final int width, final int height) {
            final long offset = java.time.temporal.ChronoUnit.MONTHS.between(first, studyFirst);
            final double step = (double) width / (size - 1);
            final Shape clip = g.getClip();
            g.clipRect(left, top, width + 1, height + 1);
            int ordinal = 0;
            for (final PatternStudy.Event event : events) {
                ordinal++;
                final int signal = left + (int) Math.round((offset + event.signal()) * step);
                final int begin = left + (int) Math.round((offset + event.start() - 0.5) * step);
                final Color bandColor = event.kind().recoveryRequired ? inflationColor : inflationRiseColor;
                g.setColor(new Color(bandColor.getRed(), bandColor.getGreen(), bandColor.getBlue(), 28));
                g.fillRect(begin, top, Math.max(2, signal - begin + (int) (step * 0.5)), height);
                g.setColor(bandColor);
                g.setStroke(new BasicStroke(1));
                g.drawLine(signal, top + 16, signal, top + height);
                g.drawString(Integer.toString(ordinal), signal + 3, top + 12);
                if (event.outcome() != PatternStudy.Outcome.CONFIRMED) continue;
                for (int i = 0; i < event.legs().size(); i++) {
                    final PatternStudy.Leg leg = event.legs().get(i);
                    final int trough = left + (int) Math.round((offset + leg.trough()) * step);
                    g.setColor(declineColor);
                    g.fillRect(begin, top + height - 22 + i * 7, Math.max(2, trough - begin), 5);
                    if (event.kind().recoveryRequired) {
                        g.setColor(recoveryColor);
                        g.fillRect(trough, top + height - 22 + i * 7, Math.max(2, left + (int) Math.round((offset + leg.recovery()) * step) - trough), 5);
                    }
                }
            }
            g.setClip(clip);
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
            if ((cell = Math.min((getWidth() - 180) / DataService.SOURCES.length, (getHeight() - 96) / DataService.SOURCES.length)) < 10) return;
            g.setColor(INK); g.drawString(method.getSelectedItem() + "  ·  stesso mese  ·  n = mesi confrontabili", 26, 26);
            for (int i = 0; i < DataService.SOURCES.length; i++) {
                g.setColor(MUTED); g.drawString(name(i), 16, 64 + i * cell + cell / 2); g.setFont(new Font("Segoe UI", Font.PLAIN, 11)); g.drawString(MATRIX_NAMES[i], 142 + i * cell, 52); g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                for (int j = 0; j < DataService.SOURCES.length; j++) {
                    final Stats.Correlation c = correlations[j][i];
                    final double strength = Double.isFinite(c.value()) ? Math.abs(c.value()) : 0;
                    final Color target = c.value() >= 0 ? POSITIVE : NEGATIVE;
                    g.setColor(new Color((int) (PANEL.getRed() * (1 - strength) + target.getRed() * strength), (int) (PANEL.getGreen() * (1 - strength) + target.getGreen() * strength), (int) (PANEL.getBlue() * (1 - strength) + target.getBlue() * strength)));
                    g.fillRect(142 + j * cell, 64 + i * cell, cell - 2, cell - 2); g.setColor(INK);
                    g.drawString(number(c.value()), 148 + j * cell, 64 + i * cell + cell / 2);
                    if (cell >= 40) { g.setFont(new Font("Segoe UI", Font.PLAIN, 10)); g.drawString("n " + c.count(), 148 + j * cell, 78 + i * cell + cell / 2); g.setFont(new Font("Segoe UI", Font.PLAIN, 13)); }
                }
            }
            final int lx;
            if (getWidth() - (lx = 162 + DataService.SOURCES.length * cell) > 180) {
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
            g.setColor(INK); g.drawString(name(xChoice.getSelectedIndex()) + " (X) e " + name(yChoice.getSelectedIndex()) + " (Y)", left, 28);
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
            g.drawString("X: " + unit(xChoice.getSelectedIndex()) + "   ·   Y: " + unit(yChoice.getSelectedIndex()) + "", left, getHeight() - 10);
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
        for (final String key : new String[]{"Viewport.background", "TextArea.background", "TextField.background", "Table.background", "TableHeader.background"}) UIManager.put(key, PANEL);
        for (final String key : new String[]{"Label.foreground", "Button.foreground", "TextField.foreground", "TextArea.foreground", "ComboBox.foreground", "CheckBox.foreground", "TabbedPane.foreground", "Table.foreground"}) UIManager.put(key, INK);
        UIManager.put("TextField.caretForeground", INK); UIManager.put("Table.gridColor", GRID);
        UIManager.put("Table.selectionBackground", new Color(229, 237, 232));
        UIManager.put("Table.selectionForeground", INK);
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
            theme(); final JFrame frame = new JFrame("MacroLab · Osservatorio macro"); final MacroLab app = new MacroLab(data);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); frame.setContentPane(app); final Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); frame.setSize(Math.min(1360, screen.width), Math.min(900, screen.height)); frame.setMinimumSize(new Dimension(Math.min(1000, screen.width), Math.min(650, screen.height))); frame.setLocationRelativeTo(null); frame.setVisible(true); app.load(true);
        });
    }
}
