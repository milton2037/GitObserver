package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.github.QueryCancelledException;
import com.gitobserver.github.StatsAggregator;
import com.gitobserver.model.Account;
import com.gitobserver.model.AuthorCount;
import com.gitobserver.model.BucketCount;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.FetchReport;
import com.gitobserver.model.RepoActivity;
import com.gitobserver.model.StatsSnapshot;
import com.gitobserver.model.TimeRange;
import com.gitobserver.report.StatsPdf;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.stage.FileChooser;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StatisticsView {
    private static final String LAST_DAY = "Últimas 24 horas";
    private static final String LAST_WEEK = "Últimos 7 días";
    private static final String LAST_MONTH = "Últimos 30 días";
    private static final String THIS_MONTH = "Este mes";
    private static final String CUSTOM = "Personalizado";

    private final AppContext context;
    private final VBox content = new VBox(14);
    private final ScrollPane root = new ScrollPane(content);
    private final ComboBox<String> period = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final Spinner<Integer> fromHour = numberSpinner(0, 23, 0);
    private final Spinner<Integer> fromMinute = numberSpinner(0, 59, 0);
    private final Spinner<Integer> toHour = numberSpinner(0, 23, 0);
    private final Spinner<Integer> toMinute = numberSpinner(0, 59, 0);
    private final ComboBox<String> repositories = new ComboBox<>();
    private final CheckBox allAccounts = new CheckBox("Todas las cuentas");
    private final CheckBox hideBots = new CheckBox("Ocultar bots");
    private final Button generateButton = Ui.primary("Generar estadísticas");
    private final Button exportButton = Ui.secondary("Exportar PDF");
    private final Button cancelButton = Ui.secondary("Cancelar");
    private final Label progress = new Label();
    private final Label summary = Ui.muted("Elige los parámetros y pulsa Generar estadísticas.");
    private final Label commitsValue = new Label("0");
    private final Label peopleValue = new Label("0");
    private final Label reposValue = new Label("0");
    private final Label peakValue = new Label("—");
    private final BarChart<String, Number> authorsChart = barChart("Commits por colaborador");
    private final BarChart<String, Number> daysChart = barChart("Commits por día");
    private final BarChart<String, Number> hoursChart = barChart("Commits por hora");
    private final TableView<RepoActivity> reposTable = new TableView<>();
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private boolean applyingPreset;
    private StatsSnapshot snapshot;
    private String reportPeriod = "";
    private String reportFrom = "";
    private String reportTo = "";
    private String reportRepository = "";
    private String reportAccounts = "";
    private boolean reportHideBots;

    public StatisticsView(AppContext context) {
        this.context = context;
        build();
        period.setValue(LAST_WEEK);
        applyPreset();
        context.activeAccountProperty().addListener((obs, oldValue, newValue) -> refreshRepositories());
        allAccounts.selectedProperty().addListener((obs, oldValue, newValue) -> refreshRepositories());
        refreshRepositories();
    }

    public Node root() {
        return root;
    }

    public void onShow() {
        refreshRepositories();
    }

    private void build() {
        root.setFitToWidth(true);
        root.getStyleClass().add("stats-scroll");
        content.setPadding(Ui.pagePadding());
        content.setFillWidth(true);

        period.getItems().setAll(LAST_DAY, LAST_WEEK, LAST_MONTH, THIS_MONTH, CUSTOM);
        period.setPrefWidth(180);
        period.valueProperty().addListener((obs, oldValue, newValue) -> applyPreset());
        fromDate.setPrefWidth(140);
        toDate.setPrefWidth(140);
        repositories.setPrefWidth(260);
        progress.getStyleClass().add("muted");
        summary.setWrapText(true);
        markCustomOnEdit();

        generateButton.setOnAction(event -> generate());
        exportButton.setDisable(true);
        exportButton.setOnAction(event -> exportPdf());
        cancelButton.setDisable(true);
        cancelButton.setOnAction(event -> {
            cancelRequested.set(true);
            progress.setText("Cancelando...");
        });

        Label parametersTitle = new Label("Parámetros");
        parametersTitle.getStyleClass().add("section-title");
        HBox rangeRow = new HBox(8,
                new Label("Periodo"), period,
                new Label("Desde"), fromDate, fromHour, muted("h"), fromMinute, muted("min"),
                new Label("Hasta"), toDate, toHour, muted("h"), toMinute, muted("min"));
        rangeRow.setAlignment(Pos.CENTER_LEFT);
        HBox scopeRow = new HBox(10, new Label("Repositorio"), repositories, allAccounts, hideBots, generateButton, exportButton, cancelButton, progress);
        scopeRow.setAlignment(Pos.CENTER_LEFT);
        VBox parameters = Ui.card(parametersTitle, rangeRow, scopeRow, summary);

        HBox cards = new HBox(12,
                statCard("Commits", commitsValue),
                statCard("Colaboradores", peopleValue),
                statCard("Repositorios", reposValue),
                statCard("Hora con más cambios", peakValue)
        );

        authorsChart.setPrefWidth(480);
        daysChart.setPrefWidth(480);
        HBox.setHgrow(authorsChart, Priority.ALWAYS);
        HBox.setHgrow(daysChart, Priority.ALWAYS);
        VBox authorsCard = chartCard(authorsChart);
        VBox daysCard = chartCard(daysChart);
        HBox charts = new HBox(12, authorsCard, daysCard);
        VBox hoursCard = chartCard(hoursChart);

        configureReposTable();
        VBox tableCard = new VBox(8, sectionTitle("Repositorios en el rango"), reposTable);
        tableCard.getStyleClass().add("card");
        reposTable.setPrefHeight(240);
        reposTable.setMinHeight(180);

        content.getChildren().addAll(parameters, cards, charts, hoursCard, tableCard);
    }

    private void configureReposTable() {
        reposTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        reposTable.setPlaceholder(Ui.muted("Genera las estadísticas para ver los repositorios."));
        TableColumn<RepoActivity, String> name = new TableColumn<>("Repositorio");
        name.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().repository()));
        TableColumn<RepoActivity, Integer> commits = new TableColumn<>("Commits");
        commits.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().commits()));
        commits.setMaxWidth(120);
        TableColumn<RepoActivity, Integer> people = new TableColumn<>("Colaboradores");
        people.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().collaborators()));
        people.setMaxWidth(150);
        reposTable.getColumns().setAll(List.of(name, commits, people));
        reposTable.setItems(FXCollections.observableArrayList());
    }

    private void generate() {
        List<Account> accounts = selectedAccounts();
        if (accounts.isEmpty()) {
            return;
        }
        TimeRange range;
        try {
            if (fromDate.getValue() == null || toDate.getValue() == null) {
                throw new IllegalArgumentException("Indica la fecha de inicio y la fecha de fin.");
            }
            range = TimeRange.of(
                    fromDate.getValue(),
                    readSpinner(fromHour, 0, 23),
                    readSpinner(fromMinute, 0, 59),
                    toDate.getValue(),
                    readSpinner(toHour, 0, 23),
                    readSpinner(toMinute, 0, 59),
                    ZoneId.systemDefault()
            );
        } catch (IllegalArgumentException ex) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Parámetros no válidos", ex.getMessage());
            return;
        }
        if (!confirmScope(accounts)) {
            return;
        }
        String onlyRepo = RepoChoices.only(repositories.getValue());
        boolean bots = hideBots.isSelected();
        reportPeriod = period.getValue() == null ? "" : period.getValue();
        reportFrom = Ui.formatInstant(range.start());
        reportTo = Ui.formatInstant(range.end());
        reportRepository = onlyRepo == null ? RepoChoices.ALL : onlyRepo;
        reportAccounts = allAccounts.isSelected() ? "Todas las cuentas (" + accounts.size() + ")" : accounts.get(0).label();
        reportHideBots = bots;
        cancelRequested.set(false);
        Task<FetchReport<CommitRecord>> task = new Task<>() {
            @Override
            protected FetchReport<CommitRecord> call() {
                return context.monitor().loadCommits(
                        accounts, onlyRepo, range, "", bots, cancelRequested::get, this::updateMessage);
            }
        };
        Background.run(context, task, () -> {
            setRunning(true);
            progress.textProperty().bind(task.messageProperty());
        }, this::showReport, this::showError);
    }

    private void showReport(FetchReport<CommitRecord> report) {
        progress.textProperty().unbind();
        progress.setText("");
        setRunning(false);
        refreshRepositories();
        List<CommitRecord> commits = report.items();
        ZoneId zone = ZoneId.systemDefault();
        List<AuthorCount> authors = StatsAggregator.commitsByAuthor(commits);
        List<BucketCount> days = StatsAggregator.commitsByDay(commits, zone);
        List<BucketCount> hours = StatsAggregator.commitsByHour(commits, zone);
        List<RepoActivity> repositoriesActivity = StatsAggregator.commitsByRepository(commits);

        String peak = StatsAggregator.peakHour(commits, zone);
        snapshot = new StatsSnapshot(
                Instant.now(),
                reportPeriod,
                reportFrom,
                reportTo,
                reportRepository,
                reportAccounts,
                reportHideBots,
                report.cancelled(),
                report.errors().size(),
                commits.size(),
                authors.size(),
                repositoriesActivity.size(),
                peak,
                authors,
                days,
                hours,
                repositoriesActivity
        );
        commitsValue.setText(Integer.toString(commits.size()));
        peopleValue.setText(Integer.toString(authors.size()));
        reposValue.setText(Integer.toString(repositoriesActivity.size()));
        peakValue.setText(peak);
        fillAuthors(authors);
        fillBuckets(daysChart, days);
        fillBuckets(hoursChart, hours);
        reposTable.getItems().setAll(repositoriesActivity);

        String text = "Rango aplicado: " + Ui.formatInstant(rangeStart()) + " — " + Ui.formatInstant(rangeEnd())
                + " · " + period.getValue();
        if (report.cancelled()) {
            text = "Consulta cancelada. " + text;
        }
        if (!report.errors().isEmpty()) {
            text = text + " · Avisos: " + report.errors().size();
            Tooltip tooltip = new Tooltip(join(report.errors()));
            tooltip.setWrapText(true);
            summary.setTooltip(tooltip);
        } else {
            summary.setTooltip(null);
        }
        summary.setText(text);
        exportButton.setDisable(false);
        context.statusProperty().set(report.cancelled() ? "Consulta cancelada." : "Estadísticas actualizadas.");
        if (commits.isEmpty() && !report.errors().isEmpty() && !report.cancelled()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudieron generar las estadísticas", join(report.errors()));
        }
    }

    private void showError(Throwable error) {
        progress.textProperty().unbind();
        progress.setText("");
        setRunning(false);
        if (error instanceof QueryCancelledException || cancelRequested.get()) {
            context.statusProperty().set("Consulta cancelada.");
            summary.setText("Consulta cancelada.");
            return;
        }
        Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudieron generar las estadísticas", Ui.messageOf(error));
    }

    private void fillAuthors(List<AuthorCount> authors) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        int shown = 0;
        for (AuthorCount author : authors) {
            if (shown == 8) {
                break;
            }
            shown++;
            series.getData().add(new XYChart.Data<>(author.author(), author.commits()));
        }
        authorsChart.getData().setAll(series);
    }

    private void fillBuckets(BarChart<String, Number> chart, List<BucketCount> buckets) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (BucketCount bucket : buckets) {
            series.getData().add(new XYChart.Data<>(bucket.label(), bucket.commits()));
        }
        chart.getData().setAll(series);
    }

    private void applyPreset() {
        String selected = period.getValue();
        if (selected == null || CUSTOM.equals(selected)) {
            return;
        }
        applyingPreset = true;
        LocalDateTime end = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime start = switch (selected) {
            case LAST_DAY -> end.minusHours(24);
            case LAST_MONTH -> end.minusDays(30).withHour(0).withMinute(0);
            case THIS_MONTH -> end.withDayOfMonth(1).withHour(0).withMinute(0);
            default -> end.minusDays(7).withHour(0).withMinute(0);
        };
        fromDate.setValue(start.toLocalDate());
        setSpinner(fromHour, start.getHour());
        setSpinner(fromMinute, start.getMinute());
        toDate.setValue(end.toLocalDate());
        setSpinner(toHour, end.getHour());
        setSpinner(toMinute, end.getMinute());
        applyingPreset = false;
    }

    private void markCustomOnEdit() {
        Runnable mark = () -> {
            if (!applyingPreset && !CUSTOM.equals(period.getValue())) {
                period.setValue(CUSTOM);
            }
        };
        fromDate.valueProperty().addListener((obs, oldValue, newValue) -> mark.run());
        toDate.valueProperty().addListener((obs, oldValue, newValue) -> mark.run());
        fromHour.valueProperty().addListener((obs, oldValue, newValue) -> mark.run());
        fromMinute.valueProperty().addListener((obs, oldValue, newValue) -> mark.run());
        toHour.valueProperty().addListener((obs, oldValue, newValue) -> mark.run());
        toMinute.valueProperty().addListener((obs, oldValue, newValue) -> mark.run());
    }

    private java.time.Instant rangeStart() {
        LocalDate date = fromDate.getValue() == null ? LocalDate.now() : fromDate.getValue();
        return LocalDateTime.of(date, LocalTime.of(spinnerOrZero(fromHour), spinnerOrZero(fromMinute)))
                .atZone(ZoneId.systemDefault()).toInstant();
    }

    private java.time.Instant rangeEnd() {
        LocalDate date = toDate.getValue() == null ? LocalDate.now() : toDate.getValue();
        return LocalDateTime.of(date, LocalTime.of(spinnerOrZero(toHour), spinnerOrZero(toMinute)))
                .atZone(ZoneId.systemDefault()).toInstant();
    }

    private boolean confirmScope(List<Account> accounts) {
        if (RepoChoices.only(repositories.getValue()) != null) {
            return true;
        }
        int known = 0;
        boolean unknown = false;
        for (Account account : accounts) {
            var repos = context.cachedRepos(account.getId());
            if (repos == null) {
                unknown = true;
            } else {
                known += repos.size();
            }
        }
        if (!unknown && known <= 30) {
            return true;
        }
        String detail = unknown
                ? "Se van a leer los repositorios y después sus commits del periodo elegido. Si son muchos, la consulta tarda y consume cuota de la API."
                : "Se van a consultar " + known + " repositorios. Puede tardar y consumir cuota de la API.";
        return Ui.confirm(window(), "Generar estadísticas", detail + " ¿Quieres continuar?");
    }

    private List<Account> selectedAccounts() {
        if (allAccounts.isSelected()) {
            if (context.accounts().isEmpty()) {
                Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Sin cuentas", "Agrega un token en Configuración.");
                return List.of();
            }
            return new ArrayList<>(context.accounts());
        }
        Account active = context.activeAccount();
        if (active == null) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Sin cuenta activa",
                    "Elige una cuenta en la parte superior o marca todas las cuentas.");
            return List.of();
        }
        return List.of(active);
    }

    private void refreshRepositories() {
        RepoChoices.fillActive(repositories, context, allAccounts.isSelected());
    }

    private void exportPdf() {
        if (snapshot == null) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Sin datos",
                    "Genera las estadísticas antes de exportar el reporte.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar reporte PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName("gitobserver-estadisticas-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now()) + ".pdf");
        File file = chooser.showSaveDialog(window());
        if (file == null) {
            return;
        }
        File target = file.getName().toLowerCase().endsWith(".pdf") ? file : new File(file.getParentFile(), file.getName() + ".pdf");
        try {
            StatsPdf.write(target.toPath(), snapshot);
        } catch (Exception ex) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudo crear el PDF", Ui.messageOf(ex));
            return;
        }
        context.statusProperty().set("Reporte guardado: " + target.getAbsolutePath());
        boolean opened = false;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(target);
                opened = true;
            }
        } catch (Exception ex) {
            opened = false;
        }
        Ui.alert(window(), javafx.scene.control.Alert.AlertType.INFORMATION, "Reporte guardado",
                opened ? "Se abrió el PDF:\n" + target.getAbsolutePath() : "Se guardó en:\n" + target.getAbsolutePath());
    }

    private void setRunning(boolean running) {
        generateButton.setDisable(running);
        exportButton.setDisable(running || snapshot == null);
        cancelButton.setDisable(!running);
        period.setDisable(running);
        fromDate.setDisable(running);
        toDate.setDisable(running);
        fromHour.setDisable(running);
        fromMinute.setDisable(running);
        toHour.setDisable(running);
        toMinute.setDisable(running);
        repositories.setDisable(running);
        allAccounts.setDisable(running);
        hideBots.setDisable(running);
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static VBox statCard(String caption, Label value) {
        value.getStyleClass().add("card-value");
        VBox card = new VBox(4, Ui.muted(caption), value);
        card.getStyleClass().addAll("card", "stat-card");
        card.setMinWidth(180);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static VBox chartCard(BarChart<String, Number> chart) {
        VBox card = new VBox(chart);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(8, 12, 12, 12));
        HBox.setHgrow(card, Priority.ALWAYS);
        chart.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static BarChart<String, Number> barChart(String title) {
        CategoryAxis categories = new CategoryAxis();
        NumberAxis values = new NumberAxis();
        values.setMinorTickCount(0);
        values.setForceZeroInRange(true);
        BarChart<String, Number> chart = new BarChart<>(categories, values);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(12);
        chart.setPrefHeight(280);
        chart.setMinHeight(240);
        chart.getStyleClass().add("stats-chart");
        return chart;
    }

    private static Spinner<Integer> numberSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial));
        spinner.setEditable(true);
        spinner.setPrefWidth(76);
        return spinner;
    }

    private static void setSpinner(Spinner<Integer> spinner, int value) {
        spinner.getValueFactory().setValue(value);
        spinner.getEditor().setText(Integer.toString(value));
    }

    private static int spinnerOrZero(Spinner<Integer> spinner) {
        Integer value = spinner.getValue();
        return value == null ? 0 : value;
    }

    private static int readSpinner(Spinner<Integer> spinner, int min, int max) {
        String text = spinner.getEditor().getText();
        try {
            if (text == null) {
                throw new NumberFormatException();
            }
            int value = Integer.parseInt(text.strip());
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            spinner.getValueFactory().setValue(value);
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Indica horas entre 0 y 23, y minutos entre 0 y 59.");
        }
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    private static String join(List<String> lines) {
        int limit = Math.min(lines.size(), 12);
        return String.join("\n", lines.subList(0, limit)) + (lines.size() > limit ? "\n..." : "");
    }
}
