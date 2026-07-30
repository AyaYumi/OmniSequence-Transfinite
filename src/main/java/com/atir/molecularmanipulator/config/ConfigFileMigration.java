package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigFileMigration {
    public static final String CLIENT_FILE = "omnisequence-transfinite-client.toml";
    public static final String SERVER_FILE = "omnisequence-transfinite-server.toml";

    private static final String LEGACY_CLIENT_FILE = "molecularmanipulator-client.toml";
    private static final String LEGACY_SERVER_FILE = "molecularmanipulator-server.toml";
    private static final String RETIRED_UNSCALED_DISPATCH_LIMIT =
            "omni_unscaled_dispatch_attempts_per_tick";
    private static final String RETIRED_BATCH_SUBSTITUTION_OPTION =
            "omni_batch_allow_substitution_patterns";
    private static final LevelResource SERVER_CONFIG_DIRECTORY = new LevelResource("serverconfig");

    private ConfigFileMigration() {
    }

    public static void migrateGlobalConfigs() {
        migrateDirectory(FMLPaths.CONFIGDIR.get());
        migrateDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"));
        removeRetiredServerOptions(FMLPaths.CONFIGDIR.get(), "server/default");
        removeRetiredServerOptions(
                FMLPaths.GAMEDIR.get().resolve("defaultconfigs"),
                "server/default");
    }

    public static void refreshGlobalConfigSchemas(
            ModConfigSpec serverSpec, ModConfigSpec clientSpec) {
        refreshDirectory(FMLPaths.CONFIGDIR.get(), serverSpec, clientSpec);
        refreshDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"),
                serverSpec, clientSpec);
    }

    public static void migrateServerConfig(MinecraftServer server) {
        var directory = server.getWorldPath(SERVER_CONFIG_DIRECTORY);
        migrateFile(directory, LEGACY_SERVER_FILE, SERVER_FILE);
        removeRetiredServerOptions(directory, "server/save");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(SERVER_FILE), ModConfig.SERVER_SPEC, "server/save");
    }

    private static void migrateDirectory(Path directory) {
        migrateFile(directory, LEGACY_CLIENT_FILE, CLIENT_FILE);
        migrateFile(directory, LEGACY_SERVER_FILE, SERVER_FILE);
    }

    private static void refreshDirectory(Path directory,
            ModConfigSpec serverSpec, ModConfigSpec clientSpec) {
        removeRetiredServerOptions(directory, "server/default");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(SERVER_FILE), serverSpec, "server/default");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(CLIENT_FILE), clientSpec, "client");
    }

    private static void removeRetiredServerOptions(
            Path directory, String displayName) {
        ConfigSchemaGuard.removeObsoleteOption(
                directory.resolve(SERVER_FILE),
                RETIRED_UNSCALED_DISPATCH_LIMIT,
                displayName);
        ConfigSchemaGuard.removeObsoleteOption(
                directory.resolve(SERVER_FILE),
                RETIRED_BATCH_SUBSTITUTION_OPTION,
                displayName);
    }

    static void migrateFile(Path directory, String legacyFileName, String fileName) {
        var legacyFile = directory.resolve(legacyFileName);
        var file = directory.resolve(fileName);
        if (Files.exists(file) || !Files.isRegularFile(legacyFile)) {
            return;
        }

        try {
            Files.copy(legacyFile, file);
            MolecularManipulator.LOGGER.info("Migrated config file {} to {}", legacyFile, file);
        } catch (FileAlreadyExistsException ignored) {
            // Another startup path completed the same non-overwriting migration.
        } catch (IOException exception) {
            MolecularManipulator.LOGGER.warn("Could not migrate config file {} to {}",
                    legacyFile, file, exception);
        }
    }
}
