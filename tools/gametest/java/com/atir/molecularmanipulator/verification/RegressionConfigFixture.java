package com.atir.molecularmanipulator.verification;

import com.atir.molecularmanipulator.config.ModConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import java.util.List;
import net.minecraftforge.fml.config.ConfigTracker;

/** Test-only config mutations must not race Forge's asynchronous file reloads. */
public final class RegressionConfigFixture {
    private static boolean isolated;

    private RegressionConfigFixture() { }

    public static void isolate() {
        if (isolated) return;
        var tracked = ConfigTracker.INSTANCE.fileMap().values().stream()
                .filter(config -> config.getSpec() == ModConfig.SERVER_SPEC).findFirst().orElseThrow();
        FileWatcher.defaultInstance().removeWatch(tracked.getFullPath());
        ModConfig.SERVER_SPEC.setConfig(copy(tracked.getConfigData()));
        isolated = true;
    }

    private static CommentedConfig copy(UnmodifiableConfig source) {
        var target = CommentedConfig.inMemory();
        source.valueMap().forEach((key, value) -> target.set(List.of(key), copyValue(value)));
        if (source instanceof com.electronwill.nightconfig.core.UnmodifiableCommentedConfig comments) {
            target.putAllComments(comments);
        }
        return target;
    }

    private static Object copyValue(Object value) {
        if (value instanceof UnmodifiableConfig config) return copy(config);
        if (value instanceof List<?> list) return list.stream().map(RegressionConfigFixture::copyValue).toList();
        return value;
    }
}
