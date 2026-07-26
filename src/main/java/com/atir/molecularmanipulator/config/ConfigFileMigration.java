package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigFileMigration {
    public static final String CLIENT_FILE = "omnisequence-transfinite-client.toml";
    public static final String SERVER_FILE = "omnisequence-transfinite-server.toml";

    private static final String LEGACY_CLIENT_FILE = "molecularmanipulator-client.toml";
    private static final String LEGACY_SERVER_FILE = "molecularmanipulator-server.toml";
    private static final LevelResource SERVER_CONFIG_DIRECTORY = new LevelResource("serverconfig");

    private ConfigFileMigration() {
    }

    public static void migrateGlobalConfigs() {
        migrateDirectory(FMLPaths.CONFIGDIR.get());
        migrateDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"));
    }

    public static void refreshGlobalConfigSchemas(
            ForgeConfigSpec serverSpec, ForgeConfigSpec clientSpec) {
        refreshDirectory(FMLPaths.CONFIGDIR.get(), serverSpec, clientSpec);
        refreshDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"),
                serverSpec, clientSpec);
    }

    public static void migrateServerConfig(MinecraftServer server) {
        var directory = server.getWorldPath(SERVER_CONFIG_DIRECTORY);
        migrateFile(directory, LEGACY_SERVER_FILE, SERVER_FILE);
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(SERVER_FILE), ModConfig.SERVER_SPEC, "server/save");
    }

    private static void migrateDirectory(Path directory) {
        migrateFile(directory, LEGACY_CLIENT_FILE, CLIENT_FILE);
        migrateFile(directory, LEGACY_SERVER_FILE, SERVER_FILE);
    }

    private static void refreshDirectory(Path directory,
            ForgeConfigSpec serverSpec, ForgeConfigSpec clientSpec) {
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(SERVER_FILE), serverSpec, "server/default");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(CLIENT_FILE), clientSpec, "client");
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
