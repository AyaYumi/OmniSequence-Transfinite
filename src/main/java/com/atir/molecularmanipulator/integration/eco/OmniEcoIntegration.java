package com.atir.molecularmanipulator.integration.eco;

import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.api.me.ECOExternalCrafting;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import com.atir.molecularmanipulator.blockentity.MolecularManipulatorLogic;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchHost;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.world.level.Level;

public final class OmniEcoIntegration {
    private OmniEcoIntegration() {}

    public static void register() {
        ECOParallelCraftingProviders.registerFastPath(MolecularManipulatorLogic.class,
                logic -> context -> EcoMolecularFastPathAdapter.prepare(logic, context));
        ECOParallelCraftingProviders.registerFastPath(MolecularCenterLogic.class,
                logic -> context -> EcoMolecularFastPathAdapter.prepare(logic, context));
        ECOParallelCraftingProviders.registerFastPath(TileAssemblerMatrixPattern.class,
                logic -> context -> EcoMolecularFastPathAdapter.prepare(logic, context));
        ECOParallelCraftingProviders.registerFastPath(MatterFabricationPatternLogic.class, MatterProvider::new,
                MatterFabricationPatternLogic::pushPatternForJob);
        ECOExternalCrafting.register(new CpuAdapter());
    }

    private static final class CpuAdapter implements ECOExternalCrafting.Adapter {
        @Override public boolean ownsDispatch(Object cpuLogic) {
            return cpuLogic instanceof EcoCpuDispatchAccess && cpuLogic instanceof OmniCpuDispatchHost host
                    && host.omniDispatch$enabled();
        }

        @Override public boolean accepts(ICraftingCPU cpu, ICraftingPlan plan) {
            return EcoOmniPlanAdmission.supports(cpu, plan);
        }

        @Override public boolean isSubmitting(ICraftingPlan plan) {
            return EcoOmniPlanAdmission.supportsActive(plan);
        }

        @Override public boolean supportsBigOrders(ICraftingCPU cpu) {
            return EcoOmniPlanMenu.isOmni(cpu);
        }

        @Override public boolean bigOrderAvailable(IGrid grid, ICraftingCPU cpu) {
            return EcoOmniPlanMenu.available(grid, cpu);
        }

        @Override public ECOExternalCrafting.BigOrderSubmission submit(ECOExternalCrafting.BigOrderContext context) {
            var outcome = EcoOmniPlanMenu.submit(context.grid(), context.cpu(), context.visible(), context.confirmed(),
                    context.confirmed(), context.options(), context.forced(), context.source());
            return new ECOExternalCrafting.BigOrderSubmission("Omni: " + outcome.reason(), outcome.submitted());
        }

        @Override public BigInteger insert(Iterable<ICraftingCPU> cpus, UUID jobId, AEKey key,
                BigInteger amount, Actionable mode) {
            return EcoCpuDispatchOutput.insert(cpus, jobId, key, amount, mode);
        }

        @Override public void jobCanceled(Level level, UUID jobId) {
            MolecularBatchCancellationData.markCanceled(level, jobId);
        }

        @Override public void jobRestored(Level level, UUID jobId) {
            MolecularBatchCancellationData.resumePersisted(level, jobId);
        }
    }

    private record MatterProvider(MatterFabricationPatternLogic logic) implements ECOFastPathDispatchProvider {
        @Override public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
            return EcoMatterBatchAdapter.prepare(logic, context);
        }

        @Override public ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
            return EcoMatterBatchAdapter.prepareExact(logic, context, requested);
        }
    }
}
