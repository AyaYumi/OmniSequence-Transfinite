package com.atir.molecularmanipulator;

/** Bootstrap before JUnit loads classes that initialize Minecraft registries. */
public final class ForgeTestLauncherListener implements org.junit.platform.launcher.LauncherSessionListener {
    @Override public void launcherSessionOpened(org.junit.platform.launcher.LauncherSession session) {
        try {
            if (net.minecraftforge.fml.loading.LoadingModList.get() == null)
                net.minecraftforge.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), null);
            ForgeTestBootstrap.bootstrapMinecraft();
        } catch (Exception exception) { throw new IllegalStateException("Cannot bootstrap Forge unit-test runtime", exception); }
    }
}
