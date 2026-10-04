package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.GitObserverApp;
import com.gitobserver.github.QueryCancelledException;
import com.gitobserver.model.Account;
import com.gitobserver.model.FetchReport;
import com.gitobserver.model.IssueItem;
import com.gitobserver.model.IssueNote;
import com.gitobserver.model.PullItem;
import com.gitobserver.model.ReviewNote;
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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SeguimientoView {
    private static final String OPEN = "Abiertos";
    private static final String CLOSED = "Cerrados";
    private static final String ALL_STATES = "Todos";

    private final AppContext context;
    private final VBox root = new VBox(12);
    private final ComboBox<String> repositories = new ComboBox<>();
    private final ComboBox<String> states = new ComboBox<>();
    private final CheckBox allAccounts = new CheckBox("Todas las cuentas");
    private final TextField search = new TextField();
    private final Button loadButton = Ui.primary("Cargar");
    private final Button instructButton = Ui.primary("Nueva instrucción");
    private final Button cancelButton = Ui.secondary("Cancelar");
    private final Label progress = new Label();
    private final Label summary = new Label("Elige uno o varios repositorios y pulsa Cargar.");
    private final TabPane tabs = new TabPane();
    private final ObservableList<IssueItem> issueSource = FXCollections.observableArrayList();
    private final FilteredList<IssueItem> issueRows = new FilteredList<>(issueSource, issue -> true);
    private final TableView<IssueItem> issueTable = new TableView<>();
    private final Label issueTitle = new Label("Issue");
    private final Label issueMeta = Ui.muted("Selecciona un issue para ver el texto, comentarlo o cerrarlo.");
    private final TextArea issueBody = new TextArea();
    private final ListView<IssueNote> notes = new ListView<>();
    private final TextArea commentBox = new TextArea();
    private final Button editIssueButton = Ui.secondary("Editar");
    private final Button stateButton = Ui.secondary("Cerrar issue");
    private final Button openIssueButton = Ui.secondary("Abrir en GitHub");
    private final Button commentButton = Ui.primary("Publicar comentario");
    private final ObservableList<PullItem> pullSource = FXCollections.observableArrayList();
    private final FilteredList<PullItem> pullRows = new FilteredList<>(pullSource, pull -> true);
    private final TableView<PullItem> pullTable = new TableView<>();
    private final Label pullTitle = new Label("Pull request");
    private final Label pullMeta = Ui.muted("Selecciona un pull request para dejar instrucciones o una revisión.");
    private final TextArea pullBody = new TextArea();
    private final ListView<ReviewNote> reviews = new ListView<>();
    private final TextArea reviewBox = new TextArea();
    private final Button commentReviewButton = Ui.secondary("Comentar");
    private final Button requestButton = Ui.primary("Pedir cambios");
    private final Button approveButton = Ui.secondary("Aprobar");
    private final Button openPullButton = Ui.secondary("Abrir en GitHub");
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private IssueItem pendingIssue;
    private PullItem pendingPull;
    private int noteTicket;
    private int reviewTicket;
    private boolean replacing;

    public SeguimientoView(AppContext context) {
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
        search.setPromptText("Buscar por repositorio, título o autor");
        search.setPrefWidth(280);
        search.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
        repositories.setPrefWidth(260);
        states.getItems().setAll(OPEN, CLOSED, ALL_STATES);
        states.setValue(OPEN);
        progress.getStyleClass().add("muted");
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);
        loadButton.setOnAction(event -> loadActive());
        instructButton.setOnAction(event -> createInstruction());
        cancelButton.setDisable(true);
        cancelButton.setOnAction(event -> {
            cancelRequested.set(true);
            progress.setText("Cancelando...");
        });

        Label hint = Ui.muted("Los issues sirven para dar instrucciones. Las revisiones se publican sobre los pull requests. Puedes trabajar con un repositorio o con todos los del alcance.");
        HBox filters = new HBox(10,
                new Label("Repositorio"), repositories,
                new Label("Estado"), states,
                allAccounts, search);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, loadButton, instructButton, cancelButton, progress);
        actions.setAlignment(Pos.CENTER_LEFT);

        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("follow-tabs");
        tabs.getTabs().add(new Tab("Issues", issuePane()));
        tabs.getTabs().add(new Tab("Revisiones", reviewPane()));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        root.setPadding(Ui.pagePadding());
        root.getChildren().addAll(hint, filters, actions, summary, tabs);
        setIssueActions(null);
        setPullActions(null);
    }

    private VBox issuePane() {
        configureIssueTable();
        VBox tableCard = new VBox(issueTable);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(8));
        VBox.setVgrow(issueTable, Priority.ALWAYS);

        issueTitle.getStyleClass().add("section-title");
        issueTitle.setWrapText(true);
        issueMeta.setWrapText(true);
        issueBody.setEditable(false);
        issueBody.setWrapText(true);
        issueBody.getStyleClass().add("note-view");
        issueBody.setPrefRowCount(5);
        notes.setPlaceholder(Ui.muted("Sin comentarios cargados."));
        notes.setCellFactory(list -> noteCell(list));
        notes.setPrefHeight(160);
        commentBox.setPromptText("Instrucción o comentario para los colaboradores");
        commentBox.setWrapText(true);
        commentBox.getStyleClass().add("composer");
        commentBox.setPrefRowCount(3);
        editIssueButton.setOnAction(event -> editSelected());
        stateButton.setOnAction(event -> toggleState());
        openIssueButton.setOnAction(event -> {
            IssueItem issue = issueTable.getSelectionModel().getSelectedItem();
            if (issue != null) {
                Ui.openUrl(issue.htmlUrl());
            }
        });
        commentButton.setOnAction(event -> publishComment());
        HBox issueActions = new HBox(8, editIssueButton, stateButton, openIssueButton);
        issueActions.setAlignment(Pos.CENTER_LEFT);
        HBox commentActions = new HBox(8, commentButton);
        VBox detail = new VBox(8, issueTitle, issueMeta, issueActions, issueBody, notes, commentBox, commentActions);
        detail.getStyleClass().add("card");
        VBox.setVgrow(notes, Priority.ALWAYS);
        detail.setMinWidth(320);

        SplitPane split = new SplitPane(tableCard, detail);
        split.setDividerPositions(0.5);
        split.setMaxHeight(Double.MAX_VALUE);
        VBox pane = new VBox(split);
        pane.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(split, Priority.ALWAYS);
        pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return pane;
    }

    private VBox reviewPane() {
        configurePullTable();
        VBox tableCard = new VBox(pullTable);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(8));
        VBox.setVgrow(pullTable, Priority.ALWAYS);

        pullTitle.getStyleClass().add("section-title");
        pullTitle.setWrapText(true);
        pullMeta.setWrapText(true);
        pullBody.setEditable(false);
        pullBody.setWrapText(true);
        pullBody.getStyleClass().add("note-view");
        pullBody.setPrefRowCount(4);
        reviews.setPlaceholder(Ui.muted("Sin revisiones cargadas."));
        reviews.setCellFactory(list -> reviewCell(list));
        reviews.setPrefHeight(140);
        reviewBox.setPromptText("Instrucciones para quien abrió el pull request");
        reviewBox.setWrapText(true);
        reviewBox.getStyleClass().add("composer");
        reviewBox.setPrefRowCount(3);
        commentReviewButton.setOnAction(event -> publishReview("COMMENT", "Publicar comentario",
                "Se publicará un comentario en la revisión. El autor lo verá en el pull request."));
        requestButton.setOnAction(event -> publishReview("REQUEST_CHANGES", "Pedir cambios",
                "Se pedirán cambios. El autor tendrá que atender las instrucciones antes de poder fusionar, si el repositorio lo exige."));
        approveButton.setOnAction(event -> publishReview("APPROVE", "Aprobar revisión",
                "Se aprobará el pull request y se publicará el texto como revisión."));
        openPullButton.setOnAction(event -> {
            PullItem pull = pullTable.getSelectionModel().getSelectedItem();
            if (pull != null) {
                Ui.openUrl(pull.htmlUrl());
            }
        });
        HBox reviewActions = new HBox(8, commentReviewButton, requestButton, approveButton, openPullButton);
        reviewActions.setAlignment(Pos.CENTER_LEFT);
        VBox detail = new VBox(8, pullTitle, pullMeta, pullBody, reviews, reviewBox, reviewActions);
        detail.getStyleClass().add("card");
        VBox.setVgrow(reviews, Priority.ALWAYS);
        detail.setMinWidth(320);

        SplitPane split = new SplitPane(tableCard, detail);
        split.setDividerPositions(0.48);
        split.setMaxHeight(Double.MAX_VALUE);
        VBox pane = new VBox(split);
        pane.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(split, Priority.ALWAYS);
        pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return pane;
    }

    private void configureIssueTable() {
        issueTable.setItems(issueRows);
        issueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        issueTable.setPlaceholder(Ui.muted("Carga los issues del alcance elegido."));
        TableColumn<IssueItem, String> repo = column("Repositorio", IssueItem::repository);
        TableColumn<IssueItem, Integer> number = new TableColumn<>("#");
        number.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().number()));
        number.setMaxWidth(70);
        TableColumn<IssueItem, String> title = column("Título", IssueItem::title);
        TableColumn<IssueItem, String> state = new TableColumn<>("Estado");
        state.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().state()));
        state.setCellFactory(col -> stateCell());
        state.setMaxWidth(110);
        TableColumn<IssueItem, String> author = column("Autor", IssueItem::author);
        author.setMaxWidth(140);
        TableColumn<IssueItem, Integer> comments = new TableColumn<>("Comentarios");
        comments.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().commentCount()));
        comments.setMaxWidth(120);
        issueTable.getColumns().setAll(List.of(repo, number, title, state, author, comments));
        issueTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, issue) -> {
            if (replacing) {
                return;
            }
            showIssue(issue);
            notes.getItems().clear();
            pendingIssue = issue;
            if (issue != null) {
                loadNotes(issue);
            }
        });
        issueTable.setRowFactory(view -> {
            TableRow<IssueItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Ui.openUrl(row.getItem().htmlUrl());
                }
            });
            return row;
        });
    }

    private void configurePullTable() {
        pullTable.setItems(pullRows);
        pullTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        pullTable.setPlaceholder(Ui.muted("Carga los pull requests del alcance elegido."));
        TableColumn<PullItem, String> repo = pullColumn("Repositorio", PullItem::repository);
        TableColumn<PullItem, Integer> number = new TableColumn<>("#");
        number.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().number()));
        number.setMaxWidth(70);
        TableColumn<PullItem, String> title = pullColumn("Título", PullItem::title);
        TableColumn<PullItem, String> state = new TableColumn<>("Estado");
        state.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().stateLabel()));
        state.setMaxWidth(120);
        TableColumn<PullItem, String> author = pullColumn("Autor", PullItem::author);
        author.setMaxWidth(140);
        pullTable.getColumns().setAll(List.of(repo, number, title, state, author));
        pullTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, pull) -> {
            if (replacing) {
                return;
            }
            showPull(pull);
            reviews.getItems().clear();
            pendingPull = pull;
            if (pull != null) {
                loadReviews(pull);
            }
        });
        pullTable.setRowFactory(view -> {
            TableRow<PullItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Ui.openUrl(row.getItem().htmlUrl());
                }
            });
            return row;
        });
    }

    private void loadActive() {
        if (tabs.getSelectionModel().getSelectedIndex() == 1) {
            loadPulls();
        } else {
            loadIssues();
        }
    }

    private void loadIssues() {
        List<Account> accounts = selectedAccounts();
        if (accounts.isEmpty() || !confirmScope(accounts, "Cargar issues")) {
            return;
        }
        String onlyRepo = RepoChoices.only(repositories.getValue());
        String state = apiState();
        cancelRequested.set(false);
        Task<FetchReport<IssueItem>> task = new Task<>() {
            @Override
            protected FetchReport<IssueItem> call() {
                return context.monitor().loadIssues(accounts, onlyRepo, state, cancelRequested::get, this::updateMessage);
            }
        };
        Background.run(context, task, () -> begin(task), report -> showIssues(report), this::showError);
    }

    private void loadPulls() {
        List<Account> accounts = selectedAccounts();
        if (accounts.isEmpty() || !confirmScope(accounts, "Cargar pull requests")) {
            return;
        }
        String onlyRepo = RepoChoices.only(repositories.getValue());
        String state = apiState();
        cancelRequested.set(false);
        Task<FetchReport<PullItem>> task = new Task<>() {
            @Override
            protected FetchReport<PullItem> call() {
                return context.monitor().loadPulls(accounts, onlyRepo, state, cancelRequested::get, this::updateMessage);
            }
        };
        Background.run(context, task, () -> begin(task), report -> showPulls(report), this::showError);
    }

    private void createInstruction() {
        List<Account> accounts = selectedAccounts();
        if (accounts.isEmpty()) {
            return;
        }
        boolean everyAvailable = RepoChoices.only(repositories.getValue()) == null;
        String label = everyAvailable ? "todos los repositorios del alcance" : repositories.getValue();
        Optional<Instruction> draft = new InstructionDialog(window(), "Nueva instrucción", label, everyAvailable, null).showAndWait();
        if (draft.isEmpty()) {
            return;
        }
        Instruction instruction = draft.get();
        String onlyRepo = instruction.everyRepository() ? null : RepoChoices.only(repositories.getValue());
        if (onlyRepo == null && !instruction.everyRepository()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Elige un repositorio",
                    "Selecciona un repositorio o marca la opción de publicarlo en todos.");
            return;
        }
        if (instruction.everyRepository() && !confirmScope(accounts, "Publicar la instrucción")) {
            return;
        }
        if (instruction.everyRepository()) {
            int known = knownRepos(accounts);
            String detail = known < 0
                    ? "Se creará el mismo issue en cada repositorio del alcance."
                    : "Se creará el mismo issue en " + known + " repositorios.";
            if (!Ui.confirm(window(), "Publicar en varios repositorios", detail + " Los colaboradores lo verán como un issue abierto. ¿Quieres continuar?")) {
                return;
            }
        }
        cancelRequested.set(false);
        Task<FetchReport<IssueItem>> task = new Task<>() {
            @Override
            protected FetchReport<IssueItem> call() {
                return context.monitor().publishIssues(
                        accounts, onlyRepo, instruction.title(), instruction.body(), instruction.assignees(),
                        cancelRequested::get, this::updateMessage);
            }
        };
        Background.run(context, task, () -> begin(task), this::showPublished, this::showError);
    }

    private void editSelected() {
        IssueItem issue = issueTable.getSelectionModel().getSelectedItem();
        List<Account> accounts = selectedAccounts();
        if (issue == null || accounts.isEmpty()) {
            return;
        }
        Optional<Instruction> draft = new InstructionDialog(
                window(), "Editar issue", issue.repository() + " #" + issue.number(), false, issue).showAndWait();
        if (draft.isEmpty()) {
            return;
        }
        Instruction instruction = draft.get();
        cancelRequested.set(false);
        Task<IssueItem> task = new Task<>() {
            @Override
            protected IssueItem call() {
                return context.monitor().updateIssue(
                        accounts, issue, instruction.title(), instruction.body(), instruction.assignees(), null, cancelRequested::get);
            }
        };
        Background.run(context, task, () -> begin(task), updated -> {
            finish();
            replaceIssue(updated);
            context.statusProperty().set("Issue actualizado en " + updated.repository() + ".");
        }, this::showError);
    }

    private void toggleState() {
        IssueItem issue = issueTable.getSelectionModel().getSelectedItem();
        List<Account> accounts = selectedAccounts();
        if (issue == null || accounts.isEmpty()) {
            return;
        }
        boolean closing = issue.open();
        String title = closing ? "Cerrar issue" : "Reabrir issue";
        String detail = closing
                ? "Se cerrará #" + issue.number() + " en " + issue.repository() + "."
                : "Se reabrirá #" + issue.number() + " en " + issue.repository() + ".";
        if (!Ui.confirm(window(), title, detail + " ¿Quieres continuar?")) {
            return;
        }
        String state = closing ? "closed" : "open";
        cancelRequested.set(false);
        Task<IssueItem> task = new Task<>() {
            @Override
            protected IssueItem call() {
                return context.monitor().updateIssue(accounts, issue, null, null, null, state, cancelRequested::get);
            }
        };
        Background.run(context, task, () -> begin(task), updated -> {
            finish();
            replaceIssue(updated);
            context.statusProperty().set(updated.open() ? "Issue reabierto." : "Issue cerrado.");
        }, this::showError);
    }

    private void publishComment() {
        IssueItem issue = issueTable.getSelectionModel().getSelectedItem();
        List<Account> accounts = selectedAccounts();
        String body = commentBox.getText() == null ? "" : commentBox.getText().strip();
        if (issue == null || accounts.isEmpty()) {
            return;
        }
        if (body.isBlank()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Comentario vacío", "Escribe el comentario antes de publicarlo.");
            return;
        }
        cancelRequested.set(false);
        Task<IssueNote> task = new Task<>() {
            @Override
            protected IssueNote call() {
                return context.monitor().publishNote(accounts, issue, body, cancelRequested::get);
            }
        };
        Background.run(context, task, () -> begin(task), note -> {
            finish();
            commentBox.clear();
            notes.getItems().add(note);
            replaceIssue(withCount(issue, issue.commentCount() + 1));
            context.statusProperty().set("Comentario publicado en " + issue.repository() + " #" + issue.number() + ".");
        }, this::showError);
    }

    private void publishReview(String event, String title, String detail) {
        PullItem pull = pullTable.getSelectionModel().getSelectedItem();
        List<Account> accounts = selectedAccounts();
        String body = reviewBox.getText() == null ? "" : reviewBox.getText().strip();
        if (pull == null || accounts.isEmpty()) {
            return;
        }
        if (body.isBlank()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Revisión vacía", "Escribe las instrucciones de la revisión.");
            return;
        }
        if (!Ui.confirm(window(), title, detail + "\n\n" + pull.repository() + " #" + pull.number())) {
            return;
        }
        cancelRequested.set(false);
        Task<ReviewNote> task = new Task<>() {
            @Override
            protected ReviewNote call() {
                return context.monitor().publishReview(accounts, pull, event, body, cancelRequested::get);
            }
        };
        Background.run(context, task, () -> begin(task), review -> {
            finish();
            reviewBox.clear();
            reviews.getItems().add(review);
            context.statusProperty().set("Revisión publicada en " + pull.repository() + " #" + pull.number() + ".");
        }, this::showError);
    }

    private void loadNotes(IssueItem issue) {
        List<Account> accounts = selectedAccounts();
        if (accounts.isEmpty()) {
            return;
        }
        int ticket = ++noteTicket;
        cancelRequested.set(false);
        Task<List<IssueNote>> task = new Task<>() {
            @Override
            protected List<IssueNote> call() {
                return context.monitor().loadNotes(accounts, issue, cancelRequested::get);
            }
        };
        boolean started = Background.tryRun(context, task, () -> begin(task), loaded -> {
            finish();
            if (ticket == noteTicket) {
                notes.getItems().setAll(loaded);
            }
            resumePendingIssue(issue);
        }, error -> {
            finish();
            if (ticket == noteTicket) {
                showError(error);
            }
            resumePendingIssue(issue);
        });
        if (!started) {
            issueMeta.setText("Hay otra consulta en curso. Este hilo se cargará al terminar.");
        }
    }

    private void loadReviews(PullItem pull) {
        List<Account> accounts = selectedAccounts();
        if (accounts.isEmpty()) {
            return;
        }
        int ticket = ++reviewTicket;
        cancelRequested.set(false);
        Task<List<ReviewNote>> task = new Task<>() {
            @Override
            protected List<ReviewNote> call() {
                return context.monitor().loadReviews(accounts, pull, cancelRequested::get);
            }
        };
        boolean started = Background.tryRun(context, task, () -> begin(task), loaded -> {
            finish();
            if (ticket == reviewTicket) {
                reviews.getItems().setAll(loaded);
            }
            resumePendingPull(pull);
        }, error -> {
            finish();
            if (ticket == reviewTicket) {
                showError(error);
            }
            resumePendingPull(pull);
        });
        if (!started) {
            pullMeta.setText("Hay otra consulta en curso. Las revisiones se cargarán al terminar.");
        }
    }

    private void resumePendingIssue(IssueItem requested) {
        if (pendingIssue != null && !sameIssue(pendingIssue, requested)) {
            loadNotes(pendingIssue);
        }
    }

    private void resumePendingPull(PullItem requested) {
        if (pendingPull != null && !samePull(pendingPull, requested)) {
            loadReviews(pendingPull);
        }
    }

    private void showIssues(FetchReport<IssueItem> report) {
        finishReport(report.errors(), report.cancelled(), report.items().size() + " issues en el alcance.");
        issueSource.setAll(report.items());
        applySearch();
        if (report.items().isEmpty() && !report.errors().isEmpty() && !report.cancelled()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudieron leer los issues", join(report.errors()));
        }
    }

    private void showPulls(FetchReport<PullItem> report) {
        finishReport(report.errors(), report.cancelled(), report.items().size() + " pull requests en el alcance.");
        pullSource.setAll(report.items());
        applySearch();
        if (report.items().isEmpty() && !report.errors().isEmpty() && !report.cancelled()) {
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "No se pudieron leer los pull requests", join(report.errors()));
        }
    }

    private void showPublished(FetchReport<IssueItem> report) {
        finish();
        String text = "Instrucciones publicadas: " + report.items().size() + ".";
        if (report.cancelled()) {
            text = "Publicación cancelada. " + text;
        }
        summary.setText(text);
        if (!report.errors().isEmpty()) {
            summary.setTooltip(tooltip(report.errors()));
            Ui.alert(window(), javafx.scene.control.Alert.AlertType.WARNING, "Algunas instrucciones no se publicaron", join(report.errors()));
        } else {
            summary.setTooltip(null);
        }
        context.statusProperty().set(text);
        if (!report.items().isEmpty()) {
            tabs.getSelectionModel().select(0);
            loadIssues();
        }
    }

    private void showIssue(IssueItem issue) {
        setIssueActions(issue);
        if (issue == null) {
            issueTitle.setText("Issue");
            issueMeta.setText("Selecciona un issue para ver el texto, comentarlo o cerrarlo.");
            issueBody.clear();
            return;
        }
        issueTitle.setText("#" + issue.number() + "  " + issue.title());
        String assignees = issue.assignees() == null || issue.assignees().isBlank() ? "sin asignar" : issue.assignees();
        issueMeta.setText(issue.repository() + "  ·  " + issue.stateLabel() + "  ·  " + issue.author()
                + "  ·  " + assignees + "  ·  " + Ui.formatInstant(issue.updatedAt()));
        issueBody.setText(issue.body() == null || issue.body().isBlank() ? "Este issue no tiene descripción." : issue.body());
        stateButton.setText(issue.open() ? "Cerrar issue" : "Reabrir issue");
    }

    private void showPull(PullItem pull) {
        setPullActions(pull);
        if (pull == null) {
            pullTitle.setText("Pull request");
            pullMeta.setText("Selecciona un pull request para dejar instrucciones o una revisión.");
            pullBody.clear();
            return;
        }
        pullTitle.setText("#" + pull.number() + "  " + pull.title());
        pullMeta.setText(pull.repository() + "  ·  " + pull.stateLabel() + "  ·  " + pull.author()
                + "  ·  " + Ui.formatInstant(pull.updatedAt()));
        pullBody.setText(pull.body() == null || pull.body().isBlank() ? "Este pull request no tiene descripción." : pull.body());
    }

    private void replaceIssue(IssueItem updated) {
        replacing = true;
        try {
            for (int index = 0; index < issueSource.size(); index++) {
                if (sameIssue(issueSource.get(index), updated)) {
                    issueSource.set(index, updated);
                    break;
                }
            }
            issueTable.getSelectionModel().select(updated);
            showIssue(updated);
            pendingIssue = updated;
        } finally {
            replacing = false;
        }
    }

    private void showError(Throwable error) {
        finish();
        if (error instanceof QueryCancelledException || cancelRequested.get()) {
            context.statusProperty().set("Consulta cancelada.");
            summary.setText("Consulta cancelada.");
            return;
        }
        Ui.alert(window(), javafx.scene.control.Alert.AlertType.ERROR, "GitHub no completó la acción", Ui.messageOf(error));
    }

    private void begin(Task<?> task) {
        setRunning(true);
        progress.textProperty().bind(task.messageProperty());
    }

    private void finish() {
        if (progress.textProperty().isBound()) {
            progress.textProperty().unbind();
        }
        progress.setText("");
        setRunning(false);
    }

    private void finishReport(List<String> errors, boolean cancelled, String text) {
        finish();
        if (cancelled) {
            text = "Consulta cancelada. " + text;
        }
        if (!errors.isEmpty()) {
            text = text + " Avisos: " + errors.size() + ".";
            summary.setTooltip(tooltip(errors));
        } else {
            summary.setTooltip(null);
        }
        summary.setText(text);
        context.statusProperty().set(cancelled ? "Consulta cancelada." : "Seguimiento actualizado.");
    }

    private void applySearch() {
        String needle = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        issueRows.setPredicate(issue -> needle.isEmpty() || contains(needle, issue.repository(), issue.title(), issue.author(), issue.assignees()));
        pullRows.setPredicate(pull -> needle.isEmpty() || contains(needle, pull.repository(), pull.title(), pull.author()));
    }

    private void setRunning(boolean running) {
        loadButton.setDisable(running);
        instructButton.setDisable(running);
        cancelButton.setDisable(!running);
        repositories.setDisable(running);
        states.setDisable(running);
        allAccounts.setDisable(running);
        search.setDisable(running);
        tabs.setDisable(running);
        editIssueButton.setDisable(running || issueTable.getSelectionModel().getSelectedItem() == null);
        stateButton.setDisable(running || issueTable.getSelectionModel().getSelectedItem() == null);
        commentButton.setDisable(running || issueTable.getSelectionModel().getSelectedItem() == null);
        commentReviewButton.setDisable(running || pullTable.getSelectionModel().getSelectedItem() == null);
        requestButton.setDisable(running || pullTable.getSelectionModel().getSelectedItem() == null);
        approveButton.setDisable(running || pullTable.getSelectionModel().getSelectedItem() == null);
    }

    private void setIssueActions(IssueItem issue) {
        boolean enabled = issue != null;
        editIssueButton.setDisable(!enabled);
        stateButton.setDisable(!enabled);
        openIssueButton.setDisable(!enabled);
        commentButton.setDisable(!enabled);
    }

    private void setPullActions(PullItem pull) {
        boolean enabled = pull != null;
        commentReviewButton.setDisable(!enabled);
        requestButton.setDisable(!enabled);
        approveButton.setDisable(!enabled);
        openPullButton.setDisable(!enabled);
    }

    private boolean confirmScope(List<Account> accounts, String title) {
        if (RepoChoices.only(repositories.getValue()) != null) {
            return true;
        }
        int known = knownRepos(accounts);
        if (known >= 0 && known <= 30) {
            return true;
        }
        String detail = known < 0
                ? "Se van a leer los repositorios y después sus issues o pull requests. Si son muchos, la consulta tarda y consume cuota de la API."
                : "Se van a consultar " + known + " repositorios. Puede tardar y consumir cuota de la API.";
        return Ui.confirm(window(), title, detail + " ¿Quieres continuar?");
    }

    private int knownRepos(List<Account> accounts) {
        int known = 0;
        for (Account account : accounts) {
            var repos = context.cachedRepos(account.getId());
            if (repos == null) {
                return -1;
            }
            known += repos.size();
        }
        return known;
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

    private String apiState() {
        return switch (states.getValue() == null ? OPEN : states.getValue()) {
            case CLOSED -> "closed";
            case ALL_STATES -> "all";
            default -> "open";
        };
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static TableColumn<IssueItem, String> column(String name, java.util.function.Function<IssueItem, String> reader) {
        TableColumn<IssueItem, String> column = new TableColumn<>(name);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(reader.apply(data.getValue())));
        return column;
    }

    private static TableColumn<PullItem, String> pullColumn(String name, java.util.function.Function<PullItem, String> reader) {
        TableColumn<PullItem, String> column = new TableColumn<>(name);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(reader.apply(data.getValue())));
        return column;
    }

    private static TableCell<IssueItem, String> stateCell() {
        return new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                boolean open = "open".equalsIgnoreCase(item);
                badge.setText(open ? "Abierto" : "Cerrado");
                badge.getStyleClass().setAll(open ? "badge-public" : "badge-closed");
                setGraphic(badge);
            }
        };
    }

    private static ListCell<IssueNote> noteCell(ListView<IssueNote> list) {
        return new ListCell<>() {
            private final Label text = new Label();

            {
                text.setWrapText(true);
                text.maxWidthProperty().bind(list.widthProperty().subtract(28));
            }

            @Override
            protected void updateItem(IssueNote item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                text.setText(item.author() + "  ·  " + Ui.formatInstant(item.createdAt()) + "\n" + item.body());
                setGraphic(text);
            }
        };
    }

    private static ListCell<ReviewNote> reviewCell(ListView<ReviewNote> list) {
        return new ListCell<>() {
            private final Label text = new Label();

            {
                text.setWrapText(true);
                text.maxWidthProperty().bind(list.widthProperty().subtract(28));
            }

            @Override
            protected void updateItem(ReviewNote item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                String body = item.body() == null || item.body().isBlank() ? "Sin texto." : item.body();
                text.setText(item.stateLabel() + "  ·  " + item.author() + "  ·  " + Ui.formatInstant(item.submittedAt()) + "\n" + body);
                setGraphic(text);
            }
        };
    }

    private static IssueItem withCount(IssueItem issue, int count) {
        return new IssueItem(
                issue.accountAlias(), issue.repository(), issue.number(), issue.title(), issue.body(),
                issue.state(), issue.author(), issue.htmlUrl(), issue.updatedAt(), count, issue.assignees()
        );
    }

    private static boolean sameIssue(IssueItem left, IssueItem right) {
        return left != null && right != null && left.number() == right.number() && left.repository().equals(right.repository());
    }

    private static boolean samePull(PullItem left, PullItem right) {
        return left != null && right != null && left.number() == right.number() && left.repository().equals(right.repository());
    }

    private static boolean contains(String needle, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Tooltip tooltip(List<String> lines) {
        Tooltip tooltip = new Tooltip(join(lines));
        tooltip.setWrapText(true);
        return tooltip;
    }

    private static String join(List<String> lines) {
        int limit = Math.min(lines.size(), 12);
        return String.join("\n", lines.subList(0, limit)) + (lines.size() > limit ? "\n..." : "");
    }

    private record Instruction(String title, String body, List<String> assignees, boolean everyRepository) {
    }

    private static final class InstructionDialog extends Dialog<Instruction> {
        private InstructionDialog(Window owner, String heading, String repositoryLabel, boolean allowAll, IssueItem existing) {
            setTitle(heading);
            setHeaderText(heading);
            if (owner != null) {
                initOwner(owner);
            }
            TextField title = new TextField(existing == null ? "" : existing.title());
            title.setPromptText("Qué tienen que atender");
            TextArea body = new TextArea(existing == null ? "" : existing.body());
            body.setPromptText("Instrucciones para los colaboradores");
            body.setWrapText(true);
            body.setPrefRowCount(8);
            body.getStyleClass().add("composer");
            TextField assignees = new TextField(existing == null ? "" : existing.assignees());
            assignees.setPromptText("usuarios de GitHub, separados por coma");
            CheckBox every = new CheckBox("Publicar en todos los repositorios del alcance");
            every.setVisible(allowAll);
            every.setManaged(allowAll);
            Label where = Ui.muted(repositoryLabel);
            VBox form = new VBox(8,
                    field("Repositorio"), where,
                    field("Título"), title,
                    field("Instrucciones"), body,
                    field("Asignar a"), assignees,
                    every
            );
            form.setPrefWidth(520);
            form.setPadding(new Insets(8, 0, 0, 0));
            getDialogPane().setContent(form);
            ButtonType saveType = new ButtonType(existing == null ? "Publicar" : "Guardar", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
            Button save = (Button) getDialogPane().lookupButton(saveType);
            save.getStyleClass().add("primary");
            save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                if (title.getText() == null || title.getText().isBlank() || body.getText() == null || body.getText().isBlank()) {
                    event.consume();
                    Ui.alert(owner, javafx.scene.control.Alert.AlertType.WARNING, "Faltan datos",
                            "Escribe el título y las instrucciones.");
                }
            });
            setResultConverter(button -> {
                if (button != saveType) {
                    return null;
                }
                return new Instruction(title.getText().strip(), body.getText().strip(), assigneesOf(assignees.getText()), every.isSelected());
            });
            var stylesheet = GitObserverApp.class.getResource(GitObserverApp.STYLESHEET);
            if (stylesheet != null) {
                getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
            }
        }

        private static Label field(String text) {
            Label label = new Label(text);
            label.getStyleClass().add("field-label");
            return label;
        }

        private static List<String> assigneesOf(String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            List<String> logins = new ArrayList<>();
            for (String part : text.split("[,\\s]+")) {
                String login = part.strip();
                if (!login.isBlank() && !logins.contains(login)) {
                    logins.add(login);
                }
            }
            return logins;
        }
    }
}
