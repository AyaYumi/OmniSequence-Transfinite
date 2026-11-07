package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.integration.extendedaeplus.MatterFabricationProviderUpload;
import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.client.ProviderListCache;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Uses the real optional EAEP 1.6.4.1 client selector and server provider uploader, not a replica. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterFabricationEaepUploadGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void defaultEaepAutomaticProviderUpload(GameTestHelper helper) {
        MatterFabricationNetworkUploadGameTests.withFixture(helper, 2, true, fixture -> upload(helper, fixture, false));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void renamedEaepAutomaticProviderUpload(GameTestHelper helper) {
        MatterFabricationNetworkUploadGameTests.withFixture(helper, 3, true, fixture -> upload(helper, fixture, true));
    }

    private static void upload(GameTestHelper helper, MatterFabricationNetworkUploadGameTests.Fixture fixture, boolean renamed) {
        var assembly = fixture.assembly();
        var originalName = ProviderUploadUtil.getProviderDisplayNameComponent(assembly);
        if (renamed) assembly.rename("Custom well / 重命名总成");
        helper.assertTrue(assembly.getMainNode().isActive()
                        && ProviderUploadUtil.listAvailableProvidersFromGrid(fixture.menu()).contains(assembly),
                "EAEP must see the actual formed assembly as an active ordinary provider on this ME grid");
        var name = ProviderUploadUtil.getProviderDisplayNameComponent(assembly);
        try {
            var ordinaryName = Component.literal("Custom Alloy Smelter");
            ProviderListCache.update(List.of(ordinaryName), List.of(9));
            helper.assertTrue(ordinaryName.equals(ProviderListCache.findUniqueMatch("smelter")),
                    "Non-fabrication categories must retain EAEP's ordinary name matching");
            ProviderListCache.update(List.of(name), List.of(MatterFabricationPatternAssemblyBlockEntity.PATTERN_SLOTS));
            var pattern = encodeSpendingNetworkBlank(helper, fixture);
            if (renamed) {
                helper.assertTrue(ProviderUploadUtil.uploadFromEncodingMenuToNamedProvider(fixture.player(),
                                (IPatternUploadMenu) fixture.menu(), originalName) == ProviderUploadUtil.UploadOutcome.FAILED
                                && !fixture.terminal().getLogic().getEncodedPatternInv().isEmpty()
                                && assembly.getLogic().getPatternInv().isEmpty(),
                        "EAEP's separate stale display-name route must fail without consuming its encoded source");
                System.out.println("MATTER_EAEP_STALE_NAME_REPRO renamed=true old-name=FAILED encoded=retained");
            }
            // This is the real selector called by EAEP after its enabled/Shift checks.
            var category = Component.translatable("gui.molecularmanipulator.fabrication.jei_title").getString();
            var target = ProviderListCache.findUniqueMatch(category);
            helper.assertTrue(target != null, "EAEP fabrication-category auto selection must survive an arbitrary assembly rename");
            var result = ProviderUploadUtil.uploadFromEncodingMenuToNamedProvider(fixture.player(),
                    (IPatternUploadMenu) fixture.menu(), target);
            helper.assertTrue(result == ProviderUploadUtil.UploadOutcome.SUCCESS, "Actual EAEP named-provider upload must succeed");
            var inventory = assembly.getLogic().getPatternInv();
            helper.assertTrue(inventory.getStackInSlot(0).getCount() == 1
                            && pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN)
                                    .equals(inventory.getStackInSlot(0).get(AEComponents.ENCODED_PROCESSING_PATTERN)),
                    "EAEP must insert exactly the complete encoded recipe into the live assembly");
            helper.assertTrue(fixture.terminal().getLogic().getEncodedPatternInv().isEmpty()
                            && fixture.terminal().getLogic().getBlankPatternInv().isEmpty()
                            && fixture.terminal().getInventory().getAvailableStacks().get(AEItemKey.of(AEItems.BLANK_PATTERN.asItem())) == 0,
                    "EAEP must empty its encoded source without spending another blank");
            System.out.println("MATTER_EAEP_UPLOAD_PASS " + (renamed ? "renamed" : "default") + " me=1->0 patterns=1");
        } finally {
            ProviderListCache.update(List.of(), List.of());
        }
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void eaepAutomaticTokenRejectsForeignAndDetachedWells(GameTestHelper helper) {
        MatterFabricationNetworkUploadGameTests.withFixture(helper, 4, true, fixture -> {
            var assembly = fixture.assembly();
            assembly.rename("Unrelated custom name");
            encodeSpendingNetworkBlank(helper, fixture);
            var other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ForeignUploadOwner"));
            fixture.controller().bindToPlayerTeam(other);
            var menu = (IPatternUploadMenu) fixture.menu();
            helper.assertTrue(ProviderUploadUtil.uploadFromEncodingMenuToNamedProvider(fixture.player(), menu,
                            MatterFabricationProviderUpload.automaticTarget()) == ProviderUploadUtil.UploadOutcome.FAILED
                            && assembly.getLogic().getPatternInv().isEmpty(),
                    "Type-based routing must not bypass team ownership");
            fixture.controller().bindToPlayerTeam(fixture.player());
            assembly.setControllerPos(null);
            helper.assertTrue(ProviderUploadUtil.uploadFromEncodingMenuToNamedProvider(fixture.player(), menu,
                            MatterFabricationProviderUpload.automaticTarget()) == ProviderUploadUtil.UploadOutcome.FAILED,
                    "A detached assembly must never match the automatic category target");
            assembly.setControllerPos(fixture.controller().getBlockPos());
            helper.assertTrue(!fixture.terminal().getLogic().getEncodedPatternInv().isEmpty(),
                    "Rejected automatic uploads must retain their owned encoded pattern");
            // Explicit ordinary-provider selection still follows EAEP's normal display-name rule.
            helper.assertTrue(ProviderUploadUtil.uploadFromEncodingMenuToNamedProvider(fixture.player(), menu,
                            ProviderUploadUtil.getProviderDisplayNameComponent(assembly)) == ProviderUploadUtil.UploadOutcome.SUCCESS,
                    "Manual selection of the renamed provider must remain unchanged");
            System.out.println("MATTER_EAEP_GATES_PASS foreign=reject detached=reject manual-renamed=success");
        });
    }

    private static net.minecraft.world.item.ItemStack encodeSpendingNetworkBlank(GameTestHelper helper,
            MatterFabricationNetworkUploadGameTests.Fixture fixture) {
        var logic = fixture.terminal().getLogic();
        var blank = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        helper.assertTrue(logic.getBlankPatternInv().isEmpty() && logic.getEncodedPatternInv().isEmpty()
                        && fixture.terminal().getInventory().extract(blank, 1, Actionable.MODULATE, fixture.menu().getActionSource()) == 1,
                "The optional named-provider fixture owns exactly one real ME blank before encoding");
        var recipe = MatterRecipeIndex.get(helper.getLevel()).fabrication(MatterFabricationNetworkUploadGameTests.RECIPE);
        var pattern = MatterFabricationPatternEncoding.encode(recipe.value(), MatterFabricationNetworkUploadGameTests.CHOICES);
        logic.getEncodedPatternInv().setItemDirect(0, pattern.copy());
        return pattern;
    }
}
