package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.concurrent.SynchronizedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enforces exact option schemas for this mod's TOML files.
 *
 * <p>Missing current options and invalid current values retain NeoForge's normal
 * correction behavior. Any option that is not present in the current schema is
 * treated as evidence of an outdated config and resets the complete file to the
 * latest defaults.</p>
 */
public final class ConfigSchemaGuard {
    private static final int MAX_BACKUPS = 5;
    private static final int MAX_LOGGED_PATHS = 16;
    private static final Map<ModConfigSpec, String> STRICT_SPECS = new IdentityHashMap<>();

    private ConfigSchemaGuard() {
    }

    public static synchronized void registerStrictSpec(
            ModConfigSpec spec, String displayName) {
        STRICT_SPECS.put(spec, displayName);
    }

    /**
     * Called from the ModConfigSpec mixin before NeoForge performs its normal
     * correction. Existing disk configs are already backed up by ConfigTracker
     * at this point.
     */
    public static void resetLoadedTomlIfOutdated(
            ModConfigSpec spec, CommentedConfig config) {
        String displayName = strictSpecName(spec);
        var unexpectedPaths = unexpectedLoadedTomlPaths(spec, config);
        if (unexpectedPaths.isEmpty()) {
            return;
        }

        MolecularManipulator.LOGGER.warn(
                "Outdated {} configuration contains options that are not in the current schema: {}. "
                        + "Resetting the complete configuration to current defaults; "
                        + "NeoForge keeps the previous file as a TOML backup.",
                displayName, summarize(unexpectedPaths));
        config.clear();
        config.clearComments();
    }

    /**
     * Proactively refreshes config/defaultconfig files that might not be selected
     * as the active server config for the current world.
     */
    public static boolean regenerateFileIfOutdated(
            Path file, ModConfigSpec spec, String displayName) {
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

    private static synchronized @Nullable String strictSpecName(ModConfigSpec spec) {
        return STRICT_SPECS.get(spec);
    }

    private static List<String> unexpectedLoadedTomlPaths(
            ModConfigSpec spec, CommentedConfig config) {
        if (strictSpecName(spec) == null
                || config.configFormat() != TomlFormat.instance()) {
            return List.of();
        }
        return findUnexpectedPaths(config, spec.getSpec());
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

    private static UnmodifiableConfig readToml(Path file) throws IOException {
        var config = new SynchronizedConfig(TomlFormat.instance(), LinkedHashMap::new);
        try (var reader = Files.newBufferedReader(file)) {
            config.bulkCommentedUpdate(view -> {
                new TomlParser().parse(reader, view, ParsingMode.REPLACE);
            });
        }
        return config;
    }

    private static void writeDefaults(Path file, ModConfigSpec spec) throws IOException {
        var defaults = new SynchronizedConfig(TomlFormat.instance(), LinkedHashMap::new);
        defaults.bulkCommentedUpdate(config -> {
            spec.correct(config);
        });
        new TomlWriter().write(defaults, file, WritingMode.REPLACE_ATOMIC);
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
