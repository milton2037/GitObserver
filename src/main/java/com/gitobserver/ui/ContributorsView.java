package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.github.QueryCancelledException;
import com.gitobserver.github.StatsAggregator;
import com.gitobserver.model.Account;
import com.gitobserver.model.CommitRecord;
import com.gitobserver.model.ContributorRef;
import com.gitobserver.model.FetchReport;
import com.gitobserver.model.PersonContribution;
import com.gitobserver.model.RepoContribution;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ContributorsView {
    private final AppContext context;
    private final VBox root = new VBox(12);
    private final ComboBox<String> repositories = new ComboBox<>();
    private final TextField search = new TextField();
    private final CheckBox allAccounts = new CheckBox("Consultar todas las cuentas");
    private final CheckBox hideBots = new CheckBox("Ocultar bots");
    private final Button loadButton = Ui.primary("Cargar contribuciones");
    private final Button cancelButton = Ui.secondary("Cancelar");
    private final Label progress = new Label();
    private final Label summary = new Label("Carga las contribuciones para ver el total de cada colaborador.");
    private final ObservableList<PersonContribution> people = FXCollections.observableArrayList();
    private final FilteredList<PersonContribution> filtered = new FilteredList<>(people, person -> true);
    private final TableView<PersonContribution> table = new TableView<>();
    private final Label detailTitle = new Label("Detalle");
    private final Label detailSummary = Ui.muted("Selecciona un colaborador para ver sus repositorios y cambios.");
    private final ListView<RepoContribution> detail = new ListView<>();
    private final TableView<CommitRecord> commits = new TableView<>();
    private final ChangeDetailPane changeDetail = new ChangeDetailPane();
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private List<Account> queriedAccounts = List.of();
    private int detailTicket;

    public ContributorsView(AppContext context) {
        this.context = context;
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
        search.setPromptText("Buscar colaborador");
        search.setPrefWidth(220);
        search.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
        repositories.setPrefWidth(280);
        progress.getStyleClass().add("muted");
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);
        loadButton.setOnAction(event -> load());
        cancelButton.setDisable(true);
        cancelButton.setOnAction(event -> {
            cancelRequested.set(true);
            progress.setText("Cancelando...");
        });

        Label hint = Ui.muted("Los totales salen de la rama principal e incluyen líneas añadidas y eliminadas. Al elegir un colaborador se cargan sus cambios recientes, con archivos y diff.");
        HBox filters = new HBox(10, new Label("Repositorio"), repositories, search, allAccounts, hideBots);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, loadButton, cancelButton, progress);
        actions.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        configureDetail();
        VBox tableCard = new VBox(table);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox detailCard = new VBox(8, detailTitle, detailSummary, detail, commits, changeDetail.root());
        detailCard.getStyleClass().add("card");
        detailCard.setPadding(new Insets(16));
        detailTitle.getStyleClass().add("section-title");
        detail.setMaxHeight(110);
        VBox.setVgrow(commits, Priority.ALWAYS);
        VBox.setVgrow(changeDetail.root(), Priority.ALWAYS);

        SplitPane split = new SplitPane(tableCard, detailCard);
        split.setDividerPositions(0.46);
        split.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(split, Priority.ALWAYS);

        root.setPadding(Ui.pagePadding());
        root.getChildren().addAll(hint, filters, actions, summary, split);
    }

    private void configureTable() {
        table.setItems(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(Ui.muted("Sin colaboradores cargados."));

        TableColumn<PersonContribution, String> name = new TableColumn<>("Colaborador");
        name.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().name()));
        TableColumn<PersonContribution, Integer> commitsColumn = new TableColumn<>("Commits");
        commitsColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().totalCommits()));
        commitsColumn.setMaxWidth(110);
        TableColumn<PersonContribution, String> additions = new TableColumn<>("+ Líneas");
        additions.setCellValueFactory(data -> new ReadOnlyStringWrapper(signed(data.getValue().additions(), true)));
        additions.setMaxWidth(110);
        TableColumn<PersonContribution, String> deletions = new TableColumn<>("- Líneas");
        deletions.setCellValueFactory(data -> new ReadOnlyStringWrapper(signed(data.getValue().deletions(), false)));
        deletions.setMaxWidth(110);
        TableColumn<PersonContribution, Integer> repos = new TableColumn<>("Repositorios");
        repos.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().repositories().size()));
        repos.setMaxWidth(130);
        table.getColumns().setAll(List.of(name, commitsColumn, additions, deletions, repos));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, person) -> showDetail(person));
    }

    private void configureDetail() {
        detail.setPlaceholder(Ui.muted("Sin repositorios."));
        detail.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(RepoContribution item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String lines = item.hasLineStats() ? "  ·  " + Ui.lineDelta(item.additions(), item.deletions()) : "";
                setText(item.repository() + "  ·  " + item.commits() + " commits" + lines);
            }
        });

        commits.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        commits.setPlaceholder(Ui.muted("Los cambios recientes aparecen al elegir un colaborador."));
        commits.setMinHeight(160);
        TableColumn<CommitRecord, String> when = new TableColumn<>("Fecha");
        when.setCellValueFactory(data -> new ReadOnlyStringWrapper(Ui.formatInstant(data.getValue().committedAt())));
        when.setMaxWidth(140);
        TableColumn<CommitRecord, String> repository = new TableColumn<>("Repositorio");
        repository.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().repository()));
        TableColumn<CommitRecord, String> message = new TableColumn<>("Cambio");
        message.setCellValueFactory(data -> new ReadOnlyStringWrapper(firstLine(data.getValue().message())));
        TableColumn<CommitRecord, String> lines = new TableColumn<>("Líneas");
        lines.setCellValueFactory(data -> new ReadOnlyStringWrapper(Ui.lineDelta(data.getValue().additions(), data.getValue().deletions())));
        lines.setMaxWidth(120);
        commits.getColumns().setAll(List.of(when, repository, message, lines));
        commits.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, commit) -> {
            if (commit == null) {
                changeDetail.showStatus("Selecciona un cambio para ver archivos y diff.");
            } else {
                changeDetail.showCommit(commit);
            }
        });
        commits.setRowFactory(view -> {
            javafx.scene.control.TableRow<CommitRecord> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Ui.openUrl(row.getItem().htmlUrl());
                }
            });
            return row;
        });
    }

    private void load() {
        List<Account> accounts = selectedAccounts();
        if (accounts.isEmpty()) {
            return;
        }
        if (!confirmScope(accounts)) {
            return;
        }
        String onlyRepo = RepoChoices.only(repositories.getValue());
        boolean bots = hideBots.isSelected();
        queriedAccounts = List.copyOf(accounts);
        cancelRequested.set(false);
        Task<FetchReport<ContributorRef>> task = new Task<>() {
            @Override
            protected FetchReport<ContributorRef> call() {
                return context.monitor().loadContributors(accounts, onlyRepo, bots, cancelRequested::get, this::updateMessage);
            }
        };
        Background.run(context, task, () -> {
            setRunning(true);
            progress.textProperty().bind(task.messageProperty());
        }, this::showReport, this::showError);
    }

    private void showReport(FetchReport<ContributorRef> report) {
        progress.textProperty().unbind();
        progress.setText("");
        setRunning(false);
        refreshRepositories();
        people.setAll(StatsAggregator.aggregate(report.items()));
        applySearch();
        int commitsTotal = people.stream().mapToInt(PersonContribution::totalCommits).sum();
        int additions = sumKnown(people.stream().mapToInt(PersonContribution::additions).toArray());
        int deletions = sumKnown(people.stream().mapToInt(PersonContribution::deletions).toArray());
        String text = people.size() + " colaboradores · " + commitsTotal + " commits";
        if (additions >= 0 || deletions >= 0) {
            text = text + " · " + Ui.lineDelta(Math.max(additions, 0), Math.max(deletions, 0));
        }
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
        context.statusProperty().set(report.cancelled() ? "Consulta cancelada." : "Contribuciones actualizadas.");
        if (!people.isEmpty()) {
            table.getSelectionModel().selectFirst();
        } else {
            showDetail(null);
        }
        if (report.items().isEmpty() && !report.errors().isEmpty() && !report.cancelled()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudieron leer las contribuciones", join(report.errors()));
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
        Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudieron leer las contribuciones", Ui.messageOf(error));
    }

    private void showDetail(PersonContribution person) {
        detailTicket++;
        commits.getItems().clear();
        if (person == null) {
            detailTitle.setText("Detalle");
            detailSummary.setText("Selecciona un colaborador para ver sus repositorios y cambios.");
            detail.getItems().clear();
            changeDetail.showStatus("Selecciona un cambio para ver archivos y diff.");
            return;
        }
        int total = Math.max(1, people.stream().mapToInt(PersonContribution::totalCommits).sum());
        int percent = (int) Math.round(person.totalCommits() * 100.0 / total);
        String lines = person.hasLineStats() ? " · " + Ui.lineDelta(person.additions(), person.deletions()) : "";
        detailTitle.setText(person.name());
        detailSummary.setText(person.totalCommits() + " commits" + lines + " · " + person.repositories().size()
                + " repositorios · " + percent + "% del total cargado");
        detail.getItems().setAll(person.repositories());
        loadAuthorChanges(person, detailTicket);
    }

    private void loadAuthorChanges(PersonContribution person, int ticket) {
        if (queriedAccounts.isEmpty() || person.repositories().isEmpty()) {
            commits.setPlaceholder(Ui.muted("No hay repositorios para consultar los cambios."));
            return;
        }
        commits.setPlaceholder(Ui.muted("Leyendo los cambios recientes..."));
        changeDetail.showStatus("Leyendo los cambios recientes de " + person.name() + "...");
        List<String> repositories = person.repositories().stream().map(RepoContribution::repository).toList();
        List<Account> accounts = queriedAccounts;
        Thread thread = new Thread(() -> {
            try {
                FetchReport<CommitRecord> report = context.monitor().loadAuthorChanges(
                        accounts,
                        person.name(),
                        repositories,
                        () -> ticket != detailTicket,
                        message -> { }
                );
                Platform.runLater(() -> {
                    if (ticket != detailTicket) {
                        return;
                    }
                    commits.getItems().setAll(report.items());
                    commits.setPlaceholder(Ui.muted(report.items().isEmpty()
                            ? "No hay commits recientes de este usuario en la rama principal."
                            : "Sin cambios."));
                    if (!report.items().isEmpty()) {
                        commits.getSelectionModel().selectFirst();
                    } else {
                        changeDetail.showStatus("No hay commits recientes para mostrar archivos.");
                    }
                    if (!report.errors().isEmpty()) {
                        context.statusProperty().set("Algunos cambios no se pudieron leer. " + report.errors().get(0));
                    }
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (ticket == detailTicket) {
                        changeDetail.showStatus(Ui.messageOf(ex));
                    }
                });
            }
        }, "gitobserver-author");
        thread.setDaemon(true);
        thread.start();
    }

    private void applySearch() {
        String needle = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        filtered.setPredicate(person -> needle.isEmpty() || person.name().toLowerCase(Locale.ROOT).contains(needle));
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
                ? "Se van a leer los repositorios y después sus colaboradores. Si son muchos, la consulta tarda y consume cuota de la API."
                : "Se van a consultar " + known + " repositorios, incluidos los cambios de cada colaborador. Puede tardar y consumir cuota de la API.";
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
        loadButton.setDisable(running);
        cancelButton.setDisable(!running);
        repositories.setDisable(running);
        search.setDisable(running);
        allAccounts.setDisable(running);
        hideBots.setDisable(running);
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static String signed(int value, boolean addition) {
        if (value < 0) {
            return "—";
        }
        return (addition ? "+" : "-") + value;
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.lines().findFirst().orElse(message);
    }

    private static int sumKnown(int[] values) {
        boolean known = false;
        int total = 0;
        for (int value : values) {
            if (value >= 0) {
                known = true;
                total += value;
            }
        }
        return known ? total : -1;
    }

    private static String join(List<String> lines) {
        int limit = Math.min(lines.size(), 12);
        return String.join("\n", lines.subList(0, limit)) + (lines.size() > limit ? "\n..." : "");
    }
}
