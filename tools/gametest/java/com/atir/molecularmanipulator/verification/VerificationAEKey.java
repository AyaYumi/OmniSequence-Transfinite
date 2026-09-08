package com.atir.molecularmanipulator.verification;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** A third-party key type, registered only in the isolated GameTest source set. */
@EventBusSubscriber(modid = "molecularmanipulator")
public final class VerificationAEKey extends AEKey {
    private static final ResourceLocation TYPE_ID = ResourceLocation.parse("molecularmanipulator:verification_resource");
    private static final MapCodec<VerificationAEKey> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(key -> key.resource),
            Codec.STRING.optionalFieldOf("variant", "").forGetter(key -> key.variant)).apply(instance, VerificationAEKey::new));
    private static final AEKeyType TYPE = new AEKeyType(TYPE_ID, VerificationAEKey.class, Component.literal("Test resources")) {
        @Override public MapCodec<VerificationAEKey> codec() { return VerificationAEKey.CODEC; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return new VerificationAEKey(input.readUtf(), input.readUtf()); }
    };
    private final String resource, variant;

    public VerificationAEKey(String resource, String variant) { this.resource = resource; this.variant = variant; }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(AEKeyType.REGISTRY_KEY, registry -> registry.register(TYPE_ID, TYPE));
    }
    @Override public AEKeyType getType() { return TYPE; }
    @Override public AEKey dropSecondary() { return new VerificationAEKey(resource, ""); }
    @Override public Object getPrimaryKey() { return resource.intern(); }
    @Override public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", resource); }
    @Override public boolean hasComponents() { return !variant.isEmpty(); }
    @Override protected Component computeDisplayName() { return Component.literal(resource + "/" + variant); }
    @Override public CompoundTag toTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag(); tag.putString("id", resource); tag.putString("variant", variant); return tag;
    }
    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) { buffer.writeUtf(resource); buffer.writeUtf(variant); }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    @Override public boolean equals(Object other) {
        return other instanceof VerificationAEKey key && resource.equals(key.resource) && variant.equals(key.variant);
    }
    @Override public int hashCode() { return Objects.hash(resource, variant); }
}
