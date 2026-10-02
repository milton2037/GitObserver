package com.gitobserver.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gitobserver.model.Account;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

public final class ConfigStore {
    private final Path file;
    private final ObjectMapper mapper;

    public ConfigStore() {
        this(Path.of(System.getProperty("user.home"), ".gitobserver", "accounts.json"));
    }

    public ConfigStore(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public Path file() {
        return file;
    }

    public LoadResult load() {
        if (Files.notExists(file)) {
            return new LoadResult(new AppConfig(), null);
        }
        try {
            AppConfig config = mapper.readValue(file.toFile(), AppConfig.class);
            if (config == null) {
                config = new AppConfig();
            }
            config.normalize();
            return new LoadResult(config, null);
        } catch (IOException ex) {
            backupCorruptFile();
            return new LoadResult(
                    new AppConfig(),
                    "No se pudo leer la configuración guardada. Se dejó un respaldo y la lista de cuentas empieza vacía."
            );
        }
    }

    public void save(AppConfig config) {
        config.normalize();
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writeValue(temporary.toFile(), config);
            moveIntoPlace(temporary);
            restrictToCurrentUser(file);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la configuración en " + file, ex);
        }
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void backupCorruptFile() {
        Path backup = file.resolveSibling("accounts.json.bak");
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // La próxima vez que se guarde se reemplaza el archivo dañado.
        }
    }

    private void restrictToCurrentUser(Path target) {
        try {
            AclFileAttributeView view = Files.getFileAttributeView(target, AclFileAttributeView.class);
            if (view == null) {
                return;
            }
            UserPrincipal user = target.getFileSystem()
                    .getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(user)
                    .setPermissions(AclEntryPermission.values())
                    .build();
            view.setAcl(List.of(entry));
        } catch (Exception ignored) {
            // El archivo queda dentro del perfil del usuario aunque no se pueda ajustar la ACL.
        }
    }

    public record LoadResult(AppConfig config, String warning) {
    }
}
