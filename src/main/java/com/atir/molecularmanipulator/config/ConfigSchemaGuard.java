package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Enforces exact option schemas for this mod's TOML files.
 *
 * <p>Missing current options and invalid current values retain Forge's normal
 * correction behavior. Any option that is not present in the current schema is
 * treated as evidence of an outdated config and resets the complete file to the
 * latest defaults.</p>
 */
public final class ConfigSchemaGuard {
    private static final int MAX_BACKUPS = 5;
    private static final int MAX_LOGGED_PATHS = 16;

    private ConfigSchemaGuard() {
    }

    /**
     * Proactively refreshes config/defaultconfig files that might not be selected
     * as the active server config for the current world.
     */
    public static boolean regenerateFileIfOutdated(
            Path file, ForgeConfigSpec spec, String displayName) {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        List<String> unexpectedPaths;
        try {
            unexpectedPaths = findUnexpectedPaths(readToml(file), spec.getSpec());
        } catch (IOException | RuntimeException exception) {
            unexpectedPaths = List.of("<invalid TOML>");
            MolecularManipulator.LOGGER.warn(
                    "Could not parse {} configuration {}; it will be backed up and regenerated",
                    displayName, file, exception);
        }
        if (unexpectedPaths.isEmpty()) {
            return false;
        }

        try {
            backUpConfig(file);
            writeDefaults(file, spec);
            MolecularManipulator.LOGGER.warn(
                    "Regenerated outdated {} configuration {} from current defaults; "
                            + "unsupported options were: {}",
                    displayName, file, summarize(unexpectedPaths));
            return true;
        } catch (IOException | RuntimeException exception) {
            MolecularManipulator.LOGGER.error(
                    "Could not regenerate outdated {} configuration {}; "
                            + "the existing file was left in place",
                    displayName, file, exception);
            return false;
        }
    }

    /**
     * Removes one retired option without resetting any still-supported values.
     */
    public static boolean removeObsoleteOption(
            Path file, String optionPath, String displayName) {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        try {
            var config = readToml(file);
            if (!config.contains(optionPath)) {
                return false;
            }

            backUpConfig(file);
            config.remove(optionPath);
            config.removeComment(optionPath);
            writeConfig(file, config);
            MolecularManipulator.LOGGER.info(
                    "Removed retired option {} from {} configuration {}",
                    optionPath, displayName, file);
            return true;
        } catch (IOException | RuntimeException exception) {
            MolecularManipulator.LOGGER.warn(
                    "Could not remove retired option {} from {} configuration {}; "
                            + "the existing file was left in place",
                    optionPath, displayName, file, exception);
            return false;
        }
    }

    private static List<String> findUnexpectedPaths(
            UnmodifiableConfig actual, UnmodifiableConfig expected) {
        var unexpectedPaths = new ArrayList<String>();
        collectUnexpectedPaths(actual, expected, "", unexpectedPaths);
        return List.copyOf(unexpectedPaths);
    }

    private static void collectUnexpectedPaths(
            UnmodifiableConfig actual, UnmodifiableConfig expected,
            String parentPath, List<String> unexpectedPaths) {
        for (var entry : actual.entrySet()) {
            var key = entry.getKey();
            var path = parentPath.isEmpty() ? key : parentPath + "." + key;
            var singleKeyPath = List.of(key);
            if (!expected.contains(singleKeyPath)) {
                unexpectedPaths.add(path);
                continue;
            }

            Object actualValue = entry.getRawValue();
            Object expectedValue = expected.getRaw(singleKeyPath);
            if (actualValue instanceof UnmodifiableConfig actualSection) {
                if (expectedValue instanceof UnmodifiableConfig expectedSection) {
                    collectUnexpectedPaths(
                            actualSection, expectedSection, path, unexpectedPaths);
                } else {
                    unexpectedPaths.add(path);
                }
            } else if (expectedValue instanceof UnmodifiableConfig) {
                unexpectedPaths.add(path);
            }
        }
    }

    private static CommentedConfig readToml(Path file) throws IOException {
        var config = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        try (var reader = Files.newBufferedReader(file)) {
            new TomlParser().parse(reader, config, ParsingMode.REPLACE);
        }
        return config;
    }

    private static void writeDefaults(Path file, ForgeConfigSpec spec) throws IOException {
        var defaults = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        spec.correct(defaults);
        writeConfig(file, defaults);
    }

    private static void writeConfig(Path file, CommentedConfig config) throws IOException {
        var directory = file.getParent();
        var temporaryFile = Files.createTempFile(
                directory, file.getFileName().toString(), ".tmp");
        try {
            new TomlWriter().write(config, temporaryFile, WritingMode.REPLACE);
            try {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void backUpConfig(Path file) throws IOException {
        var directory = file.getParent();
        var fileName = file.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        var baseName = extensionIndex < 0
                ? fileName
                : fileName.substring(0, extensionIndex);
        var extension = extensionIndex < 0
                ? "bak"
                : fileName.substring(extensionIndex + 1) + ".bak";

        for (int index = MAX_BACKUPS; index >= 1; index--) {
            var oldBackup = directory.resolve(
                    baseName + "-" + index + "." + extension);
            if (!Files.exists(oldBackup)) {
                continue;
            }
            if (index == MAX_BACKUPS) {
                Files.delete(oldBackup);
            } else {
                var nextBackup = directory.resolve(
                        baseName + "-" + (index + 1) + "." + extension);
                Files.move(oldBackup, nextBackup, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.copy(file, directory.resolve(baseName + "-1." + extension),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static String summarize(List<String> paths) {
        if (paths.size() <= MAX_LOGGED_PATHS) {
            return String.join(", ", paths);
        }
        return String.join(", ", paths.subList(0, MAX_LOGGED_PATHS))
                + " (+" + (paths.size() - MAX_LOGGED_PATHS) + " more)";
    }
}
