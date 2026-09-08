package com.atir.molecularmanipulator.verification;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** A third-party key type, registered only in the isolated GameTest source set. */
@Mod.EventBusSubscriber(modid = "molecularmanipulator", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VerificationAEKey extends AEKey {
    private static final AEKeyType TYPE = new AEKeyType(new ResourceLocation("molecularmanipulator:verification_resource"),
            VerificationAEKey.class, Component.literal("Test resources")) {
        @Override public AEKey loadKeyFromTag(CompoundTag tag) { return new VerificationAEKey(tag.getString("id"), tag.getString("variant")); }
        @Override public AEKey readFromPacket(FriendlyByteBuf input) { return new VerificationAEKey(input.readUtf(), input.readUtf()); }
    };
    private final String resource, variant;

    public VerificationAEKey(String resource, String variant) { this.resource = resource; this.variant = variant; }
    @SubscribeEvent public static void register(RegisterEvent event) {
        if (event.getRegistryKey().location().equals(new ResourceLocation("ae2:keytypes"))) AEKeyTypes.register(TYPE);
    }
    @Override public AEKeyType getType() { return TYPE; }
    @Override public AEKey dropSecondary() { return new VerificationAEKey(resource, ""); }
    @Override public Object getPrimaryKey() { return resource.intern(); }
    @Override public ResourceLocation getId() { return new ResourceLocation("molecularmanipulator", resource); }
    @Override protected Component computeDisplayName() { return Component.literal(resource + "/" + variant); }
    @Override public CompoundTag toTag() {
        var tag = new CompoundTag(); tag.putString("id", resource); tag.putString("variant", variant); return tag;
    }
    @Override public void writeToPacket(FriendlyByteBuf buffer) { buffer.writeUtf(resource); buffer.writeUtf(variant); }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    @Override public boolean equals(Object other) {
        return other instanceof VerificationAEKey key && resource.equals(key.resource) && variant.equals(key.variant);
    }
    @Override public int hashCode() { return Objects.hash(resource, variant); }
}
