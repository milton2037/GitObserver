package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.github.QueryCancelledException;
import com.gitobserver.model.Account;
import com.gitobserver.model.RepoRef;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DashboardView {
    private final AppContext context;
    private final Navigator navigator;
    private final StackPane root = new StackPane();
    private final VBox content = new VBox(14);
    private final VBox emptyState;
    private final TextField search = new TextField();
    private final ComboBox<String> visibility = new ComboBox<>();
    private final Button refreshButton = Ui.primary("Actualizar");
    private final Button cancelButton = Ui.secondary("Cancelar");
    private final Label progress = new Label();
    private final Label repoCount = new Label("0");
    private final Label privateCount = new Label("0");
    private final Label recentCount = new Label("0");
    private final Label updatedAt = Ui.muted("Aún no se han leído los repositorios.");
    private final ObservableList<RepoRef> source = FXCollections.observableArrayList();
    private final FilteredList<RepoRef> filtered = new FilteredList<>(source, repo -> true);
    private final TableView<RepoRef> table = new TableView<>();
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    public DashboardView(AppContext context, Navigator navigator) {
        this.context = context;
        this.navigator = navigator;
        this.emptyState = buildEmptyState();
        buildContent();
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        emptyState.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        root.getChildren().addAll(content, emptyState);
        root.setPadding(Ui.pagePadding());
        context.activeAccountProperty().addListener((obs, oldValue, newValue) -> showAccount(newValue));
        showAccount(context.activeAccount());
    }

    public Node root() {
        return root;
    }

    public void onShow() {
        showAccount(context.activeAccount());
    }

    private VBox buildEmptyState() {
        Label message = Ui.muted("Agrega un token de GitHub para ver los repositorios y la actividad de sus colaboradores.");
        message.setWrapText(true);
        Button openSettings = Ui.primary("Abrir configuración");
        openSettings.setOnAction(event -> navigator.showPage(Pages.SETTINGS));
        VBox card = Ui.card(Ui.title("Todavía no hay cuentas"), message, openSettings);
        card.setMaxWidth(560);
        card.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(card);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void buildContent() {
        search.setPromptText("Buscar repositorio, lenguaje o descripción");
        search.setPrefWidth(320);
        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        visibility.getItems().setAll("Todos", "Públicos", "Privados");
        visibility.setValue("Todos");
        visibility.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        refreshButton.setOnAction(event -> reload());
        cancelButton.setDisable(true);
        cancelButton.setOnAction(event -> cancelRequested.set(true));
        progress.getStyleClass().add("muted");

        HBox toolbar = new HBox(10, search, visibility, refreshButton, cancelButton, progress);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        HBox cards = new HBox(12,
                statCard("Repositorios", repoCount),
                statCard("Privados", privateCount),
                statCard("Con push en 30 días", recentCount)
        );

        configureTable();
        VBox tableCard = new VBox(table);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(8));
        tableCard.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        content.getChildren().addAll(toolbar, cards, updatedAt, tableCard);
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    private VBox statCard(String caption, Label value) {
        Label label = Ui.muted(caption);
        value.getStyleClass().add("card-value");
        VBox card = new VBox(4, label, value);
        card.getStyleClass().addAll("card", "stat-card");
        card.setMinWidth(180);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void configureTable() {
        SortedList<RepoRef> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.muted("Pulsa Actualizar para leer los repositorios de la cuenta."));

        TableColumn<RepoRef, String> name = new TableColumn<>("Repositorio");
        name.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().fullName()));

        TableColumn<RepoRef, Boolean> visibilityColumn = new TableColumn<>("Visibilidad");
        visibilityColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().privateRepo()));
        visibilityColumn.setCellFactory(column -> visibilityCell());
        visibilityColumn.setMaxWidth(130);
        visibilityColumn.setMinWidth(110);

        TableColumn<RepoRef, String> language = new TableColumn<>("Lenguaje");
        language.setCellValueFactory(data -> new ReadOnlyStringWrapper(display(data.getValue().language())));
        language.setMaxWidth(140);

        TableColumn<RepoRef, Instant> pushed = new TableColumn<>("Último push");
        pushed.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().pushedAt()));
        pushed.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Instant item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Ui.formatInstant(item));
            }
        });
        pushed.setComparator((left, right) -> {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return -1;
            }
            if (right == null) {
                return 1;
            }
            return left.compareTo(right);
        });
        pushed.setSortType(TableColumn.SortType.DESCENDING);

        TableColumn<RepoRef, String> description = new TableColumn<>("Descripción");
        description.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().description()));

        table.getColumns().setAll(List.of(name, visibilityColumn, language, pushed, description));
        table.getSortOrder().add(pushed);
        table.setRowFactory(view -> {
            TableRow<RepoRef> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Ui.openUrl(row.getItem().htmlUrl());
                }
            });
            return row;
        });
    }

    private TableCell<RepoRef, Boolean> visibilityCell() {
        return new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(item ? "Privado" : "Público");
                badge.getStyleClass().setAll(item ? "badge-private" : "badge-public");
                setGraphic(badge);
            }
        };
    }

    private void showAccount(Account account) {
        boolean missing = account == null;
        emptyState.setVisible(missing);
        emptyState.setManaged(missing);
        content.setVisible(!missing);
        content.setManaged(!missing);
        if (missing) {
            return;
        }
        List<RepoRef> cached = context.cachedRepos(account.getId());
        if (cached == null) {
            source.clear();
            updateCards(List.of());
            updatedAt.setText("Pulsa Actualizar para leer los repositorios de " + account.label() + ".");
            return;
        }
        showRepos(cached, false);
    }

    private void reload() {
        Account account = context.activeAccount();
        if (account == null) {
            return;
        }
        cancelRequested.set(false);
        Task<List<RepoRef>> task = new Task<>() {
            @Override
            protected List<RepoRef> call() {
                return context.monitor().loadRepositories(account, true, cancelRequested::get, this::updateMessage);
            }
        };
        Background.run(context, task, () -> {
            setRunning(true);
            progress.textProperty().bind(task.messageProperty());
        }, repos -> {
            progress.textProperty().unbind();
            progress.setText("");
            setRunning(false);
            if (cancelRequested.get()) {
                context.statusProperty().set("Consulta cancelada.");
                return;
            }
            if (context.activeAccount() == null || !account.getId().equals(context.activeAccount().getId())) {
                context.statusProperty().set("Repositorios actualizados para " + account.getAlias() + ".");
                return;
            }
            showRepos(repos, true);
            context.statusProperty().set("Repositorios actualizados: " + repos.size());
        }, error -> {
            progress.textProperty().unbind();
            progress.setText("");
            setRunning(false);
            if (error instanceof QueryCancelledException || cancelRequested.get()) {
                context.statusProperty().set("Consulta cancelada.");
                return;
            }
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudieron leer los repositorios", Ui.messageOf(error));
        });
    }

    private void showRepos(List<RepoRef> repos, boolean markUpdated) {
        source.setAll(repos);
        updateCards(repos);
        applyFilter();
        if (markUpdated) {
            updatedAt.setText("Actualizado: " + java.time.LocalTime.now().withNano(0)
                    + " · Doble clic abre el repositorio en el navegador.");
        }
    }

    private void updateCards(List<RepoRef> repos) {
        Instant limit = Instant.now().minus(Duration.ofDays(30));
        repoCount.setText(Integer.toString(repos.size()));
        privateCount.setText(Long.toString(repos.stream().filter(RepoRef::privateRepo).count()));
        recentCount.setText(Long.toString(repos.stream()
                .filter(repo -> repo.pushedAt() != null && repo.pushedAt().isAfter(limit))
                .count()));
    }

    private void applyFilter() {
        String needle = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        String mode = visibility.getValue();
        filtered.setPredicate(repo -> {
            if ("Públicos".equals(mode) && repo.privateRepo()) {
                return false;
            }
            if ("Privados".equals(mode) && !repo.privateRepo()) {
                return false;
            }
            if (needle.isEmpty()) {
                return true;
            }
            return contains(repo.fullName(), needle)
                    || contains(repo.description(), needle)
                    || contains(repo.language(), needle);
        });
    }

    private void setRunning(boolean running) {
        refreshButton.setDisable(running);
        cancelButton.setDisable(!running);
        search.setDisable(running);
        visibility.setDisable(running);
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
