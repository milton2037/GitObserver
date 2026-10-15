package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.AppInfo;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Pantalla de inicio de sesión de escritorio para GitObserver.
 * Diseñada para pantallas de ordenador con distribución horizontal (panel lateral de marca + área de desbloqueo).
 */
public final class LoginView {
    private final AppContext context;
    private final Runnable onSuccess;
    private final Runnable onExit;
    private final BorderPane root = new BorderPane();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField passwordVisible = new TextField();
    private final CheckBox showPassword = new CheckBox("Mostrar contraseña");
    private final Label errorBanner = new Label();
    private final Button loginButton = Ui.primary("Desbloquear aplicación");
    private final Button exitButton = Ui.secondary("Cerrar aplicación");
    private boolean syncing;

    public LoginView(AppContext context, Runnable onSuccess, Runnable onExit) {
        this.context = context;
        this.onSuccess = onSuccess;
        this.onExit = onExit;
        build();
    }

    public Parent root() {
        return root;
    }

    public void reset() {
        passwordField.clear();
        passwordVisible.clear();
        errorBanner.setText("");
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);

        String configuredUser = context.security().getUsername();
        if (configuredUser != null && !configuredUser.isBlank()) {
            usernameField.setText(configuredUser);
            Platform.runLater(passwordField::requestFocus);
        } else {
            usernameField.clear();
            Platform.runLater(usernameField::requestFocus);
        }
    }

    private void build() {
        root.getStyleClass().add("login-root");

        VBox sidebar = buildSidebar();
        StackPane mainArea = buildMainArea();

        root.setLeft(sidebar);
        root.setCenter(mainArea);

        // Teclas de acceso rápido en toda la ventana
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && onExit != null) {
                onExit.run();
            }
        });
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.getStyleClass().add("login-sidebar");
        sidebar.setPrefWidth(370);
        sidebar.setMinWidth(330);
        sidebar.setMaxWidth(420);

        // Marca de la aplicación
        ImageView logoView = new ImageView();
        var iconStream = getClass().getResourceAsStream("/icons/app-icon-128.png");
        if (iconStream != null) {
            logoView.setImage(new Image(iconStream));
            logoView.setFitWidth(42);
            logoView.setFitHeight(42);
            logoView.setPreserveRatio(true);
            logoView.setSmooth(true);
        }

        Label gitLabel = new Label("Git");
        gitLabel.setStyle("-fx-text-fill: white; -fx-font-size: 21px; -fx-font-weight: 800;");
        Label observerLabel = new Label("Observer");
        observerLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 21px; -fx-font-weight: 800;");
        HBox brandText = new HBox(0, gitLabel, observerLabel);
        brandText.setAlignment(Pos.CENTER_LEFT);

        Label brandSub = new Label("Monitor de GitHub · Edición de escritorio");
        brandSub.getStyleClass().add("brand-sub");

        VBox brandInfo = new VBox(2, brandText, brandSub);
        HBox brandRow = new HBox(12, logoView, brandInfo);
        brandRow.setAlignment(Pos.CENTER_LEFT);

        VBox brandBox = new VBox(brandRow);

        // Titular y resumen
        Label heroTitle = new Label("Control de acceso y seguridad");
        heroTitle.getStyleClass().add("login-hero-title");

        Label heroDesc = new Label("Protección local de cuentas, tokens y consultas para este equipo de trabajo.");
        heroDesc.getStyleClass().add("login-hero-desc");
        heroDesc.setWrapText(true);

        // Tarjetas informativas de características de escritorio
        VBox feature1 = featureCard(
                "🔒 Acceso protegido por contraseña",
                "Requiere autenticación maestra antes de abrir repositorios y estadísticas."
        );
        VBox feature2 = featureCard(
                "🛡️ Tokens resguardados en este equipo",
                "Las claves se almacenan en tu perfil local con hash criptográfico PBKDF2."
        );
        VBox feature3 = featureCard(
                "📊 Monitor de actividad y colaboradores",
                "Consulta commits por fecha y hora, ranking de cambios, issues y pull requests."
        );

        VBox features = new VBox(10, feature1, feature2, feature3);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Pie de autor y versión
        Label appVersion = new Label(AppInfo.NAME + " v" + AppInfo.VERSION + " (" + AppInfo.YEAR + ")");
        appVersion.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label author = new Label(AppInfo.AUTHOR + " (@" + AppInfo.GITHUB_USER + ")");
        author.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        VBox footer = new VBox(2, appVersion, author);

        sidebar.getChildren().addAll(
                brandBox,
                new Separator(),
                heroTitle,
                heroDesc,
                features,
                spacer,
                footer
        );

        return sidebar;
    }

    private StackPane buildMainArea() {
        StackPane main = new StackPane();
        main.getStyleClass().add("login-main");
        main.setAlignment(Pos.CENTER);

        VBox formCard = new VBox(14);
        formCard.getStyleClass().add("login-desktop-box");
        formCard.setMinWidth(440);
        formCard.setPrefWidth(480);
        formCard.setMaxWidth(520);
        formCard.setAlignment(Pos.CENTER_LEFT);

        // Cabecera de la tarjeta
        Label badge = new Label("Sesión protegida");
        badge.getStyleClass().add("badge-public");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label localTag = new Label("Equipo local");
        localTag.getStyleClass().addAll("mono", "muted");
        localTag.setStyle("-fx-font-size: 11px;");

        HBox topRow = new HBox(8, badge, topSpacer, localTag);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Desbloquear aplicación");
        title.getStyleClass().add("title");

        Label subtitle = Ui.muted("Ingresa tu usuario y contraseña para continuar en tu espacio de trabajo.");
        subtitle.setWrapText(true);

        // Campos de usuario y contraseña
        Label userLabel = fieldLabel("Nombre de usuario");
        usernameField.setPromptText("Ej. admin o tu nombre");
        usernameField.setPrefHeight(36);

        Label passLabel = fieldLabel("Contraseña");
        passwordField.setPromptText("Tu contraseña");
        passwordField.setPrefHeight(36);
        passwordVisible.setPromptText("Tu contraseña");
        passwordVisible.setPrefHeight(36);
        passwordVisible.setVisible(false);
        passwordVisible.setManaged(false);

        passwordField.textProperty().addListener((obs, o, n) -> {
            if (!syncing) {
                syncing = true;
                passwordVisible.setText(n);
                syncing = false;
            }
        });
        passwordVisible.textProperty().addListener((obs, o, n) -> {
            if (!syncing) {
                syncing = true;
                passwordField.setText(n);
                syncing = false;
            }
        });

        showPassword.selectedProperty().addListener((obs, o, selected) -> {
            if (selected) {
                passwordVisible.setText(passwordField.getText());
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                passwordVisible.setVisible(true);
                passwordVisible.setManaged(true);
            } else {
                passwordField.setText(passwordVisible.getText());
                passwordVisible.setVisible(false);
                passwordVisible.setManaged(false);
                passwordField.setVisible(true);
                passwordField.setManaged(true);
            }
        });

        Region optSpacer = new Region();
        HBox.setHgrow(optSpacer, Priority.ALWAYS);

        Label enterHint = new Label("↵ [Enter] para acceder");
        enterHint.getStyleClass().add("login-hint-text");

        HBox optionsRow = new HBox(10, showPassword, optSpacer, enterHint);
        optionsRow.setAlignment(Pos.CENTER_LEFT);

        // Banner de error
        errorBanner.setWrapText(true);
        errorBanner.getStyleClass().add("login-error-banner");
        errorBanner.setMaxWidth(Double.MAX_VALUE);
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);

        // Botones de acción
        loginButton.setPrefHeight(36);
        exitButton.setPrefHeight(36);
        loginButton.setOnAction(event -> submit());
        exitButton.setOnAction(event -> {
            if (onExit != null) {
                onExit.run();
            }
        });

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actions = new HBox(12, exitButton, actionSpacer, loginButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(10, 0, 0, 0));

        // Eventos de teclado en campos
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (showPassword.isSelected()) {
                    passwordVisible.requestFocus();
                } else {
                    passwordField.requestFocus();
                }
            }
        });
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                submit();
            }
        });
        passwordVisible.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                submit();
            }
        });

        // Nota sobre almacenamiento seguro
        Label pathLabel = new Label("Datos resguardados en: " + context.store().file());
        pathLabel.getStyleClass().addAll("mono", "muted");
        pathLabel.setStyle("-fx-font-size: 11px;");
        pathLabel.setWrapText(true);

        formCard.getChildren().addAll(
                topRow,
                title,
                subtitle,
                new Separator(),
                userLabel,
                usernameField,
                passLabel,
                passwordField,
                passwordVisible,
                optionsRow,
                errorBanner,
                actions,
                new Separator(),
                pathLabel
        );

        main.getChildren().add(formCard);
        return main;
    }

    private void submit() {
        String user = usernameField.getText();
        String pass = showPassword.isSelected() ? passwordVisible.getText() : passwordField.getText();

        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            showError("Ingresa tu usuario y contraseña de acceso.");
            return;
        }

        if (!context.checkCredentials(user, pass)) {
            showError("Usuario o contraseña incorrectos. Verifica tus credenciales.");
            passwordField.clear();
            passwordVisible.clear();
            if (showPassword.isSelected()) {
                passwordVisible.requestFocus();
            } else {
                passwordField.requestFocus();
            }
            return;
        }

        errorBanner.setText("");
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);
        passwordField.clear();
        passwordVisible.clear();

        if (onSuccess != null) {
            onSuccess.run();
        }
    }

    private void showError(String message) {
        errorBanner.setText(message);
        errorBanner.setVisible(true);
        errorBanner.setManaged(true);
    }

    private static VBox featureCard(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("login-feature-title");

        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("login-feature-text");
        descLabel.setWrapText(true);

        VBox box = new VBox(3, titleLabel, descLabel);
        box.getStyleClass().add("login-feature-item");
        return box;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }
}
