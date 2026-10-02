package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.github.QueryCancelledException;
import com.gitobserver.github.StatsAggregator;
import com.gitobserver.model.Account;
import com.gitobserver.model.AuthorCount;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.FetchReport;
import com.gitobserver.model.TimeRange;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ActivityView {
    private final AppContext context;
    private final VBox root = new VBox(12);
    private final DatePicker fromDate = new DatePicker(LocalDate.now().minusDays(7));
    private final DatePicker toDate = new DatePicker(LocalDate.now());
    private final Spinner<Integer> fromHour = numberSpinner(0, 23, 0);
    private final Spinner<Integer> fromMinute = numberSpinner(0, 59, 0);
    private final Spinner<Integer> toHour;
    private final Spinner<Integer> toMinute;
    private final ComboBox<String> repositories = new ComboBox<>();
    private final TextField authorFilter = new TextField();
    private final CheckBox allAccounts = new CheckBox("Consultar todas las cuentas");
    private final CheckBox hideBots = new CheckBox("Ocultar bots");
    private final Button searchButton = Ui.primary("Consultar");
    private final Button cancelButton = Ui.secondary("Cancelar");
    private final Label progress = new Label();
    private final Label summary = new Label("Elige un rango y pulsa Consultar.");
    private final TableView<CommitRecord> table = new TableView<>();
    private final VBox ranking = new VBox(8);
    private final ChangeDetailPane changeDetail = new ChangeDetailPane();
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private List<Account> queriedAccounts = List.of();
    private int detailTicket;

    public ActivityView(AppContext context) {
        this.context = context;
        LocalTime now = LocalTime.now();
        this.toHour = numberSpinner(0, 23, now.getHour());
        this.toMinute = numberSpinner(0, 59, now.getMinute());
        build();
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
        authorFilter.setPromptText("Colaborador (opcional)");
        authorFilter.setPrefWidth(200);
        repositories.setPrefWidth(280);
        fromDate.setPrefWidth(140);
        toDate.setPrefWidth(140);
        progress.getStyleClass().add("muted");
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);

        searchButton.setOnAction(event -> search());
        cancelButton.setDisable(true);
        cancelButton.setOnAction(event -> {
            cancelRequested.set(true);
            progress.setText("Cancelando...");
        });

        Label hint = Ui.muted("La consulta usa la rama predeterminada. Selecciona un commit para ver archivos y diff. Doble clic lo abre en GitHub.");
        HBox fromRow = new HBox(8, new Label("Desde"), fromDate, fromHour, labeled("h"), fromMinute, labeled("min"),
                new Label("Hasta"), toDate, toHour, labeled("h"), toMinute, labeled("min"));
        fromRow.setAlignment(Pos.CENTER_LEFT);
        HBox filters = new HBox(10, new Label("Repositorio"), repositories, authorFilter, allAccounts, hideBots);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, searchButton, cancelButton, progress);
        actions.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox tableCard = new VBox(table);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        tableCard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ranking.setFillWidth(true);
        showRanking(List.of());
        VBox side = new VBox(14, ranking, changeDetail.root());
        side.getStyleClass().add("card");
        side.setPrefWidth(360);
        side.setMinWidth(300);
        side.setPadding(new Insets(16));
        VBox.setVgrow(changeDetail.root(), Priority.ALWAYS);

        BorderPane results = new BorderPane();
        results.setCenter(tableCard);
        results.setRight(side);
        results.setMaxHeight(Double.MAX_VALUE);
        BorderPane.setMargin(side, new Insets(0, 0, 0, 12));
        VBox.setVgrow(results, Priority.ALWAYS);

        root.setPadding(Ui.pagePadding());
        root.getChildren().addAll(hint, fromRow, filters, actions, summary, results);
    }

    private void configureTable() {
        table.setItems(FXCollections.observableArrayList());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.muted("Sin commits en el rango elegido."));

        table.getColumns().setAll(List.of(
                textColumn("Cuenta", 140, CommitRecord::accountAlias),
                textColumn("Colaborador", 150, CommitRecord::author),
                textColumn("Repositorio", 220, CommitRecord::repository),
                textColumn("Fecha y hora", 150, commit -> Ui.formatInstant(commit.committedAt())),
                messageColumn()
        ));
        table.setRowFactory(view -> {
            TableRow<CommitRecord> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Ui.openUrl(row.getItem().htmlUrl());
                }
            });
            return row;
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, commit) -> loadCommitFiles(commit));
    }

    private void search() {
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
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Rango no válido", ex.getMessage());
            return;
        }
        if (!confirmScope(accounts)) {
            return;
        }
        String onlyRepo = RepoChoices.only(repositories.getValue());
        String author = authorFilter.getText();
        boolean bots = hideBots.isSelected();
        queriedAccounts = List.copyOf(accounts);
        cancelRequested.set(false);
        Task<FetchReport<CommitRecord>> task = new Task<>() {
            @Override
            protected FetchReport<CommitRecord> call() {
                return context.monitor().loadCommits(
                        accounts, onlyRepo, range, author, bots, cancelRequested::get, this::updateMessage);
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
        table.getItems().setAll(report.items());
        changeDetail.showStatus("Selecciona un commit para ver los archivos modificados.");
        List<AuthorCount> counts = StatsAggregator.commitsByAuthor(report.items());
        showRanking(counts);
        long repos = report.items().stream().map(CommitRecord::repository).distinct().count();
        String text = report.items().size() + " commits · " + counts.size() + " colaboradores · " + repos + " repositorios";
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
        context.statusProperty().set(report.cancelled() ? "Consulta cancelada." : "Consulta de actividad terminada.");
        if (report.items().isEmpty() && !report.errors().isEmpty() && !report.cancelled()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudo consultar la actividad", join(report.errors()));
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
        Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudo consultar la actividad", Ui.messageOf(error));
    }

    private void loadCommitFiles(CommitRecord commit) {
        int ticket = ++detailTicket;
        if (commit == null) {
            changeDetail.showStatus("Selecciona un commit para ver los archivos modificados.");
            return;
        }
        if (queriedAccounts.isEmpty()) {
            changeDetail.showStatus("Vuelve a consultar la actividad para leer los archivos.");
            return;
        }
        changeDetail.showStatus("Leyendo archivos de " + shortSha(commit.sha()) + "...");
        List<Account> accounts = queriedAccounts;
        Thread thread = new Thread(() -> {
            try {
                CommitRecord detailed = context.monitor().loadCommitDetail(accounts, commit, () -> ticket != detailTicket);
                Platform.runLater(() -> {
                    if (ticket == detailTicket) {
                        changeDetail.showCommit(detailed);
                    }
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (ticket == detailTicket) {
                        changeDetail.showStatus(Ui.messageOf(ex));
                    }
                });
            }
        }, "gitobserver-files");
        thread.setDaemon(true);
        thread.start();
    }

    private static String shortSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return "commit";
        }
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }

    private void showRanking(List<AuthorCount> counts) {
        ranking.getChildren().clear();
        Label title = new Label("En este rango");
        title.getStyleClass().add("section-title");
        ranking.getChildren().add(title);
        if (counts.isEmpty()) {
            ranking.getChildren().add(Ui.muted("Sin commits para mostrar."));
            return;
        }
        int max = Math.max(1, counts.get(0).commits());
        int shown = 0;
        for (AuthorCount count : counts) {
            if (shown == 12) {
                break;
            }
            shown++;
            Label name = new Label(count.author());
            name.setPrefWidth(110);
            name.setMinWidth(90);
            name.setMaxWidth(120);
            javafx.scene.layout.Region bar = new javafx.scene.layout.Region();
            bar.getStyleClass().add("bar");
            bar.setPrefHeight(10);
            bar.setMinHeight(10);
            bar.setPrefWidth(Math.max(8, 120.0 * count.commits() / max));
            Label value = Ui.muted(Integer.toString(count.commits()));
            HBox row = new HBox(8, name, bar, value);
            row.setAlignment(Pos.CENTER_LEFT);
            ranking.getChildren().add(row);
        }
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
                ? "Se van a leer los repositorios y después sus commits. Si son muchos, la consulta tarda y consume cuota de la API."
                : "Se van a consultar " + known + " repositorios. Puede tardar y consumir cuota de la API.";
        return Ui.confirm(window(), "Consultar varios repositorios", detail + " ¿Quieres continuar?");
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
                    "Elige una cuenta en la parte superior o marca consultar todas las cuentas.");
            return List.of();
        }
        return List.of(active);
    }

    private void refreshRepositories() {
        RepoChoices.fillActive(repositories, context, allAccounts.isSelected());
    }

    private void setRunning(boolean running) {
        searchButton.setDisable(running);
        cancelButton.setDisable(!running);
        fromDate.setDisable(running);
        toDate.setDisable(running);
        fromHour.setDisable(running);
        fromMinute.setDisable(running);
        toHour.setDisable(running);
        toMinute.setDisable(running);
        repositories.setDisable(running);
        authorFilter.setDisable(running);
        allAccounts.setDisable(running);
        hideBots.setDisable(running);
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static Spinner<Integer> numberSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial));
        spinner.setEditable(true);
        spinner.setPrefWidth(76);
        return spinner;
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

    private static Label labeled(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    private static TableColumn<CommitRecord, String> textColumn(
            String title,
            double width,
            java.util.function.Function<CommitRecord, String> extractor
    ) {
        TableColumn<CommitRecord, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(extractor.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static TableColumn<CommitRecord, String> messageColumn() {
        TableColumn<CommitRecord, String> column = new TableColumn<>("Mensaje");
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().message()));
        column.setCellFactory(value -> new TableCell<>() {
            private final Tooltip tooltip = new Tooltip();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item.lines().findFirst().orElse(item));
                tooltip.setText(item);
                setTooltip(tooltip);
            }
        });
        return column;
    }

    private static String join(List<String> lines) {
        int limit = Math.min(lines.size(), 12);
        return String.join("\n", lines.subList(0, limit)) + (lines.size() > limit ? "\n..." : "");
    }
}
