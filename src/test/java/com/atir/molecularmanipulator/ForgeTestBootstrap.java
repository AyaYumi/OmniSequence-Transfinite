package com.atir.molecularmanipulator;

/** Minimal bootstrap for plain JUnit, without Forge event transformers. */
public final class ForgeTestBootstrap {
    public static void bootstrapMinecraft() throws Exception {
        // Plain JUnit does not run Forge's event transformer, which normally
        // adds the default event constructor needed by NetworkHooks bootstrap.
        var listenerLookup = net.minecraftforge.eventbus.api.EventListenerHelper.class
                .getDeclaredMethod("getListenerListInternal", Class.class, boolean.class);
        listenerLookup.setAccessible(true);
        listenerLookup.invoke(null, net.minecraftforge.network.NetworkEvent.class, true);
        for (var nested : net.minecraftforge.network.NetworkEvent.class.getDeclaredClasses()) {
            if (net.minecraftforge.eventbus.api.Event.class.isAssignableFrom(nested)) {
                initializeEventListenerList(listenerLookup, nested);
            }
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    public static void bootstrapAeKeyTypes() {
        try {
            appeng.api.stacks.AEKeyTypesInternal.getRegistry();
            return;
        } catch (IllegalStateException | NullPointerException uninitialized) {
            var builder = new net.minecraftforge.registries.RegistryBuilder<appeng.api.stacks.AEKeyType>()
                    .setName(new net.minecraft.resources.ResourceLocation("ae2", "keytypes")).disableSaving();
            try {
                var create = builder.getClass().getDeclaredMethod("create");
                create.setAccessible(true);
                @SuppressWarnings("unchecked")
                var registry = (net.minecraftforge.registries.IForgeRegistry<appeng.api.stacks.AEKeyType>) create.invoke(builder);
                appeng.api.stacks.AEKeyTypesInternal.setRegistry(() -> registry);
                appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.items());
                appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.fluids());
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    private static void initializeEventListenerList(java.lang.reflect.Method lookup, Class<?> event) throws Exception {
        var parent = event.getSuperclass();
        if (parent != net.minecraftforge.eventbus.api.Event.class) initializeEventListenerList(lookup, parent);
        lookup.invoke(null, event, true);
    }
}
