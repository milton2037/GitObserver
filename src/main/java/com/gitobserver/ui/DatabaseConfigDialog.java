package com.gitobserver.ui;

import com.gitobserver.database.DatabaseConfig;
import com.gitobserver.database.DatabaseManager;
import com.gitobserver.database.DatabaseType;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.Optional;

public final class DatabaseConfigDialog extends Dialog<DatabaseConfig> {
    private final ComboBox<DatabaseType> engineCombo = new ComboBox<>();
    private final TextField sqlitePathField = new TextField();
    private final TextField hostField = new TextField();
    private final TextField portField = new TextField();
    private final TextField databaseField = new TextField();
    private final TextField userField = new TextField();
    private final PasswordField passField = new PasswordField();
    private final CheckBox sslCheck = new CheckBox("Usar SSL / Cifrado TLS");
    private final Label statusMessage = new Label();
    private final Button testBtn = Ui.secondary("Probar conexión");

    private final VBox sqliteSection = new VBox(8);
    private final GridPane serverSection = new GridPane();

    public DatabaseConfigDialog(Window owner, DatabaseConfig current) {
        setTitle("Configuración de Base de Datos");
        setHeaderText("Selecciona el motor de almacenamiento para caché e historial");
        initOwner(owner);

        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        engineCombo.getItems().addAll(DatabaseType.values());
        engineCombo.setValue(current.getType());

        sqlitePathField.setText(current.getSqlitePath() != null && !current.getSqlitePath().isBlank()
                ? current.getSqlitePath()
                : current.resolveSqliteFile().toString());
        sqlitePathField.setPrefWidth(380);

        Button browseBtn = Ui.secondary("Explorar...");
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Ubicación del archivo SQLite");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos SQLite (*.db)", "*.db"));
            File file = chooser.showSaveDialog(getDialogPane().getScene().getWindow());
            if (file != null) {
                sqlitePathField.setText(file.getAbsolutePath());
            }
        });

        HBox sqliteFileRow = new HBox(8, sqlitePathField, browseBtn);
        HBox.setHgrow(sqlitePathField, Priority.ALWAYS);

        Label sqliteHelp = Ui.muted("Almacena repositorios, commits, pull requests e issues en un archivo local sin necesidad de instalar servidores.");
        sqliteHelp.setWrapText(true);
        sqliteSection.getChildren().addAll(new Label("Ruta del archivo SQLite:"), sqliteFileRow, sqliteHelp);

        // Server section
        hostField.setText(current.getHost());
        portField.setText(String.valueOf(current.getPort()));
        databaseField.setText(current.getDatabaseName());
        userField.setText(current.getUsername());
        passField.setText(current.getPassword());
        sslCheck.setSelected(current.isSsl());

        serverSection.setHgap(10);
        serverSection.setVgap(10);
        serverSection.add(new Label("Servidor / Host:"), 0, 0);
        serverSection.add(hostField, 1, 0);
        serverSection.add(new Label("Puerto:"), 0, 1);
        serverSection.add(portField, 1, 1);
        serverSection.add(new Label("Base de datos:"), 0, 2);
        serverSection.add(databaseField, 1, 2);
        serverSection.add(new Label("Usuario:"), 0, 3);
        serverSection.add(userField, 1, 3);
        serverSection.add(new Label("Contraseña:"), 0, 4);
        serverSection.add(passField, 1, 4);
        serverSection.add(sslCheck, 1, 5);

        Label serverHelp = Ui.muted("Conecta a un servidor de base de datos corporativo o local para sincronizar métricas entre varios equipos.");
        serverHelp.setWrapText(true);

        VBox serverWrapper = new VBox(10, serverSection, serverHelp);

        // Visibility listener
        engineCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSqlite = newVal == DatabaseType.SQLITE;
            sqliteSection.setVisible(isSqlite);
            sqliteSection.setManaged(isSqlite);
            serverWrapper.setVisible(!isSqlite);
            serverWrapper.setManaged(!isSqlite);
            if (newVal == DatabaseType.MYSQL && (portField.getText().isBlank() || portField.getText().equals("5432"))) {
                portField.setText("3306");
            } else if (newVal == DatabaseType.POSTGRESQL && (portField.getText().isBlank() || portField.getText().equals("3306"))) {
                portField.setText("5432");
            }
        });

        boolean isSqliteInitial = current.getType() == DatabaseType.SQLITE;
        sqliteSection.setVisible(isSqliteInitial);
        sqliteSection.setManaged(isSqliteInitial);
        serverWrapper.setVisible(!isSqliteInitial);
        serverWrapper.setManaged(!isSqliteInitial);

        statusMessage.setWrapText(true);
        statusMessage.setVisible(false);
        statusMessage.setManaged(false);

        testBtn.setOnAction(e -> runTestConnection());

        HBox testRow = new HBox(12, testBtn, statusMessage);
        testRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14,
                new Label("Motor de base de datos:"),
                engineCombo,
                new Separator(),
                sqliteSection,
                serverWrapper,
                new Separator(),
                testRow
        );
        content.setPadding(new Insets(14));
        content.setPrefWidth(480);
        getDialogPane().setContent(content);

        setResultConverter(btn -> {
            if (btn == saveType) {
                return buildResultFromFields();
            }
            return null;
        });
    }

    private void runTestConnection() {
        testBtn.setDisable(true);
        statusMessage.setVisible(true);
        statusMessage.setManaged(true);
        statusMessage.setText("Probando conexión...");
        statusMessage.setStyle("-fx-text-fill: #475569;");

        DatabaseConfig cfg = buildResultFromFields();

        Task<DatabaseManager.TestResult> task = new Task<>() {
            @Override
            protected DatabaseManager.TestResult call() {
                return DatabaseManager.testConnection(cfg);
            }
        };

        task.setOnSucceeded(e -> {
            testBtn.setDisable(false);
            DatabaseManager.TestResult res = task.getValue();
            if (res.success()) {
                statusMessage.setText("✓ " + res.message());
                statusMessage.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
            } else {
                statusMessage.setText("✗ " + res.message());
                statusMessage.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            }
        });

        task.setOnFailed(e -> {
            testBtn.setDisable(false);
            statusMessage.setText("✗ Error al probar: " + task.getException().getMessage());
            statusMessage.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        });

        Thread t = new Thread(task, "db-test-thread");
        t.setDaemon(true);
        t.start();
    }

    private DatabaseConfig buildResultFromFields() {
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.setType(engineCombo.getValue());
        cfg.setSqlitePath(sqlitePathField.getText().strip());
        cfg.setHost(hostField.getText().strip());
        try {
            cfg.setPort(Integer.parseInt(portField.getText().strip()));
        } catch (NumberFormatException ignored) {
            cfg.setPort(cfg.getType() == DatabaseType.MYSQL ? 3306 : 5432);
        }
        cfg.setDatabaseName(databaseField.getText().strip());
        cfg.setUsername(userField.getText().strip());
        cfg.setPassword(passField.getText());
        cfg.setSsl(sslCheck.isSelected());
        cfg.setEnabled(true);
        return cfg;
    }
}
