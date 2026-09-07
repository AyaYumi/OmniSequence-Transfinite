package com.atir.molecularmanipulator.verification;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.registry.ModContent;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("molecularmanipulator") @PrefixGameTestTemplate(false)
public final class Legacy139MigrationGameTests {
    private static final int BASE_X=10000+(int)(System.currentTimeMillis()%100000)*160;
    @GameTest(template="matter_pearl_empty",timeoutTicks=300)
    public static void savedCursorsNeverResumeRemovedBlueprints(GameTestHelper helper) {
        var level=helper.getLevel();var origin=new BlockPos(BASE_X,100,6200);force(level,origin);
        level.setBlock(origin,ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState(),3);
        var sequence=(MolecularCenterBlockEntity)level.getBlockEntity(origin);
        var contents=new ItemStack(Items.DIAMOND,7);sequence.getMatterInventory().setItemDirect(0,contents.copy());
        var saved=sequence.saveWithFullMetadata(level.registryAccess());
        saved.putBoolean("molecular_center_building",true);
        saved.putBoolean("molecular_center_structure_updating",false);
        saved.putInt("molecular_center_work_cursor",200);
        saved.remove("molecular_center_work_revision");
        sequence.loadTag(saved,level.registryAccess());
        helper.assertTrue(sequence.isBuilding()&&sequence.getBuildProgress()==0,"Unversioned current build must recheck placements from the start");
        saved.putBoolean("molecular_center_structure_updating",true);
        saved.putString("molecular_center_structure_update_source","DECORATED_FEATHER");
        sequence.loadTag(saved,level.registryAccess());
        helper.assertTrue(!sequence.isBuilding(),"Removed sequence upgrade must not become a current build");
        helper.assertTrue(ItemStack.matches(contents,sequence.getMatterInventory().getStackInSlot(0)),"Stopping a retired sequence upgrade preserves contents");

        var omniPos=origin.east(2);level.setBlock(omniPos,ModContent.OMNI_COMPUTATION_CONTROLLER.get().defaultBlockState(),3);
        var omni=(OmniComputationCoreBlockEntity)level.getBlockEntity(omniPos);
        saved=omni.saveWithFullMetadata(level.registryAccess());saved.putBoolean("omni_upgrade_active",true);
        saved.putString("omni_upgrade_source_layout","PREVIOUS_COMPACT_CROWN");
        omni.loadTag(saved,level.registryAccess());
        helper.assertTrue(!omni.isBuilding(),"Removed omni upgrade must stop on reload");

        var wellPos=origin.east(4);level.setBlock(wellPos,ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState(),3);
        var well=(MatterFabricationBlockEntity)level.getBlockEntity(wellPos);
        saved=well.saveWithFullMetadata(level.registryAccess());saved.putBoolean("fabrication_building",true);
        saved.putString("fabrication_build_target_layout","PREVIOUS_FLOATING_IRIS");
        well.loadTag(saved,level.registryAccess());
        helper.assertTrue(!well.isBuilding(),"Removed well build target must stop on reload");
        System.out.println("BLUEPRINT_SAVED_CURSOR_PASS: current restart and retired sequence/omni/well builds");
        helper.succeed();
    }

    @GameTest(template="matter_pearl_empty",timeoutTicks=1000)
    public static void official139UpgradesKeepContentsAndUseCurrentCoordinates(GameTestHelper helper)throws Exception {
        var level=helper.getLevel();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("6e7f53a8-7ae9-4798-b114-9081143b77c2"),"BlueprintVerifier"));
        player.setGameMode(GameType.CREATIVE);
        var field=PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
        @SuppressWarnings("unchecked") var players=(Map<UUID,ServerPlayer>)field.get(level.getServer().getPlayerList());
        var previous=players.put(player.getUUID(),player);
        var results=new JsonArray();
        try {
            int index=0;
            for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
                var origin=new BlockPos(BASE_X+index++*160,100,4096);
                force(level,origin);
                sequence(helper,level,origin,facing,player);
                omni(helper,level,origin.south(160),facing,player);
                var row=new JsonObject();row.addProperty("facing",facing.getName());row.addProperty("sequence",true);row.addProperty("omni",true);results.add(row);
            }
            var output=Path.of(System.getProperty("blueprint.verification.output"));Files.createDirectories(output);
            Files.writeString(output.resolve("legacy139-migration.json"),results.toString());
            System.out.println("LEGACY_139_MIGRATION_PASS="+results);
            helper.succeed();
        }finally{if(previous==null)players.remove(player.getUUID());else players.put(player.getUUID(),previous);}
    }

    private static void sequence(GameTestHelper h,ServerLevel level,BlockPos origin,Direction facing,ServerPlayer player) {
        var legacy=MolecularCenterStructure.StructureLayout.LEGACY_1_3_9;
        for(var part:MolecularCenterStructure.parts(legacy)) {
            var state=MolecularCenterStructure.isController(part,legacy)?ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,facing):MolecularCenterStructure.partState(part.partType());
            level.setBlock(MolecularCenterStructure.worldPos(origin,facing,part,legacy),state,3);
        }
        var machine=(MolecularCenterBlockEntity)level.getBlockEntity(origin);machine.refreshStructure();
        h.assertTrue(machine.getStructureLayout()==legacy,"Sequence must recognize exact 1.3.9");
        var contents=new ItemStack(Items.DIAMOND,7);
        var pattern=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE),1)),List.of(new GenericStack(AEItemKey.of(Items.STONE),1)));
        machine.getMatterInventory().setItemDirect(0,contents.copy());machine.getLogic().getFullPatternInventory().setItemDirect(0,pattern.copy());
        machine.setDeconstructTarget(12345);
        var target=MolecularCenterStructure.relocatedControllerPos(origin,facing,legacy);
        h.assertTrue(target.equals(origin.below(3).relative(facing.getOpposite(),15)),"Sequence projection anchor must match migration");
        machine.startStructureUpdate(player);
        player.getInventory().clearContent();machine.serverTick();
        var saved=machine.saveWithFullMetadata(level.registryAccess());
        saved.putString("molecular_center_structure_update_source","PALACE");
        level.removeBlockEntity(origin);
        machine=(MolecularCenterBlockEntity)BlockEntity.loadStatic(origin,level.getBlockState(origin),saved,level.registryAccess());
        level.setBlockEntity(machine);
        h.assertTrue(machine.isBuilding(),"Sequence must resume an old-name 1.3.9 upgrade after reload");
        for(int tick=0;tick<1500&&machine.isBuilding();tick++){player.getInventory().clearContent();machine.serverTick();}
        var moved=(MolecularCenterBlockEntity)level.getBlockEntity(target);
        h.assertTrue(moved!=null,"Sequence controller must move to current socket");moved.refreshStructure();
        h.assertTrue(moved.getStructureLayout()==MolecularCenterStructure.StructureLayout.CURRENT&&!moved.isBuilding(),"Sequence upgrade must finish as current");
        h.assertTrue(ItemStack.matches(contents,moved.getMatterInventory().getStackInSlot(0))&&ItemStack.matches(pattern,moved.getLogic().getFullPatternInventory().getStackInSlot(0)),"Sequence inventory and encoded pattern preserved");
        h.assertTrue(moved.getDeconstructTarget()==12345,"Sequence job settings preserved");
    }

    private static void omni(GameTestHelper h,ServerLevel level,BlockPos origin,Direction facing,ServerPlayer player) {
        force(level,origin);var legacy=OmniComputationStructure.StructureLayout.LEGACY_1_3_9;
        for(var part:OmniComputationStructure.parts(legacy)) {
            var state=OmniComputationStructure.block(part.type()).defaultBlockState();
            if(part.type()==OmniComputationStructure.PartType.CONTROLLER)state=state.setValue(HorizontalDirectionalBlock.FACING,facing);
            level.setBlock(OmniComputationStructure.worldPos(origin,facing,part,legacy),state,3);
        }
        var machine=(OmniComputationCoreBlockEntity)level.getBlockEntity(origin);machine.refreshStructureNow();
        h.assertTrue(machine.getInspection().layout()==legacy&&machine.getInspection().formed(),"Omni must recognize exact 1.3.9");
        var quantum=appeng.core.definitions.AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        machine.getQuantumInventory().setItemDirect(0,quantum.copy());
        var target=OmniComputationStructure.worldPos(origin,facing,new OmniComputationStructure.Part(
                OmniComputationStructure.CURRENT_CONTROLLER_X,OmniComputationStructure.CURRENT_CONTROLLER_Y,OmniComputationStructure.CURRENT_CONTROLLER_Z,OmniComputationStructure.PartType.CONTROLLER),legacy);
        machine.startStructureUpdate(player);
        var moved=(OmniComputationCoreBlockEntity)level.getBlockEntity(target);
        h.assertTrue(moved!=null,"Omni controller must move to current socket");
        player.getInventory().clearContent();moved.serverTick();
        var saved=moved.saveWithFullMetadata(level.registryAccess());
        saved.putString("omni_upgrade_source_layout","PREVIOUS_RADIAL");
        level.removeBlockEntity(target);
        moved=(OmniComputationCoreBlockEntity)BlockEntity.loadStatic(target,level.getBlockState(target),saved,level.registryAccess());
        level.setBlockEntity(moved);
        h.assertTrue(moved.isBuilding(),"Omni must resume an old-name 1.3.9 upgrade after reload");
        for(int tick=0;tick<1500&&moved.isBuilding();tick++){player.getInventory().clearContent();moved.serverTick();}
        moved.refreshStructureNow();
        h.assertTrue(moved.getInspection().formed()&&moved.getInspection().layout()==OmniComputationStructure.StructureLayout.CURRENT&&!moved.isBuilding(),"Omni upgrade must finish as current");
        h.assertTrue(ItemStack.matches(quantum,moved.getQuantumInventory().getStackInSlot(0)),"Omni quantum contents preserved");
    }

    private static void force(ServerLevel level,BlockPos pos){for(int x=(pos.getX()-70)>>4;x<=(pos.getX()+70)>>4;x++)for(int z=(pos.getZ()-70)>>4;z<=(pos.getZ()+70)>>4;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}}
}
