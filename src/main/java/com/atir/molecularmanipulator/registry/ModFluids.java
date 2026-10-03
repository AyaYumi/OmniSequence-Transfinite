package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Liquid singularity sequence matter, stored in ME fluid cells in millibuckets. */
public final class ModFluids {
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(
            NeoForgeRegistries.Keys.FLUID_TYPES, MolecularManipulator.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(
            Registries.FLUID, MolecularManipulator.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> SEQUENCE_MATTER_TYPE = FLUID_TYPES.register(
            "singularity_sequence_matter", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.molecularmanipulator.singularity_sequence_matter")
                    .density(1500).viscosity(1600).temperature(320).lightLevel(10)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    public static final DeferredHolder<Fluid, FlowingFluid> SEQUENCE_MATTER = FLUIDS.register(
            "singularity_sequence_matter", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_SEQUENCE_MATTER = FLUIDS.register(
            "flowing_singularity_sequence_matter", () -> new BaseFlowingFluid.Flowing(properties()));
    public static final DeferredBlock<LiquidBlock> SEQUENCE_MATTER_BLOCK = ModContent.BLOCKS.register(
            "singularity_sequence_matter", () -> new LiquidBlock(SEQUENCE_MATTER.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).lightLevel(state -> 10)));
    public static final DeferredItem<BucketItem> SEQUENCE_MATTER_BUCKET = ModContent.ITEMS.register(
            "singularity_sequence_matter_bucket", () -> new BucketItem(SEQUENCE_MATTER.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(SEQUENCE_MATTER_TYPE, SEQUENCE_MATTER, FLOWING_SEQUENCE_MATTER)
                .bucket(SEQUENCE_MATTER_BUCKET).block(SEQUENCE_MATTER_BLOCK)
                .tickRate(10).slopeFindDistance(4).levelDecreasePerBlock(1).explosionResistance(100);
    }

    public static void register(IEventBus eventBus) {
        FLUID_TYPES.register(eventBus);
        FLUIDS.register(eventBus);
    }

    private ModFluids() {}
}
