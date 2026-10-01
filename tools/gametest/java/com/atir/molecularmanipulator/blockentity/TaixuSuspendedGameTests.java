package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.entity.TaixuAssemblyEntity;
import com.atir.molecularmanipulator.world.TaixuMotionWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class TaixuSuspendedGameTests {
    @GameTest(template="multiblock_dismantle_empty",batch="taixu_suspended",timeoutTicks=1200)
    public static void protectedUpgradeAndMaterialConservation(GameTestHelper h) throws Exception {
        var level=h.getLevel();var players=players(level);int index=0;
        for(var facing:Direction.Plane.HORIZONTAL){
            var anchor=new BlockPos(5632+256*index++,146,5632);
            var chunks=TaixuStructure.chunks(anchor,facing,3);
            for(var c:chunks){level.setChunkForced(c.x,c.z,true);level.getChunk(c.x,c.z);}
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Suspended"+index));players.put(player.getUUID(),player);
            TaixuBlockEntity machine=null;
            try{
                machine=fixture(level,anchor,facing);near(player,anchor);player.setGameMode(GameType.SURVIVAL);
                machine.requestInspection(player);machine.serverTick();h.assertTrue(machine.formed(),"V3 fixture must form");
                var old=new HashMap<BlockPos,TaixuStructure.Part>();TaixuStructure.parts(3).forEach(p->old.put(p.pos(),p));
                var next=new HashMap<BlockPos,TaixuStructure.Part>();TaixuStructure.parts().forEach(p->next.put(p.pos(),p));
                var newPart=TaixuStructure.parts().stream().filter(p->!old.containsKey(p.pos())).findFirst().orElseThrow();
                var foreign=machine.worldPos(newPart);level.setBlock(foreign,Blocks.STONE.defaultBlockState(),2);
                machine.requestSuspendedUpgrade(player);machine.serverTick();
                h.assertTrue(machine.structureVersion()==3 && machine.status()==TaixuBlockEntity.Status.CONFLICT && level.getBlockState(foreign).is(Blocks.STONE),"Foreign blocks must reject upgrade before removals");
                level.setBlock(foreign,Blocks.AIR.defaultBlockState(),2);
                var removed=TaixuStructure.parts(3).stream().filter(p->!p.equals(next.get(p.pos()))).toList();
                var protectedPos=machine.worldPos(removed.get(removed.size()-1));
                Consumer<BlockEvent.BreakEvent> deny=e->{if(e.getPos().equals(protectedPos))e.setCanceled(true);};
                NeoForge.EVENT_BUS.addListener(deny);
                try{machine.requestSuspendedUpgrade(player);machine.serverTick();h.assertTrue(machine.status()==TaixuBlockEntity.Status.PROTECTED,"Protection must reject upgrade");}
                finally{NeoForge.EVENT_BUS.unregister(deny);}
                for(var p:TaixuStructure.parts(3))h.assertTrue(TaixuStructure.matches(level.getBlockState(machine.worldPos(p)),p,facing),"Rejected upgrade must leave all original blocks intact");
                for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
                machine.getInternalInventory().setItemDirect(0,new ItemStack(Items.DIAMOND,7));
                machine.requestSuspendedUpgrade(player);machine.serverTick();
                h.assertTrue(machine.structureVersion()==4 && machine.operation()==TaixuBlockEntity.Operation.BUILD,"Upgrade must enter resumable construction");
                int[] expected=new int[TaixuStructure.Type.values().length];removed.forEach(p->expected[p.type().ordinal()]++);
                h.assertTrue(Arrays.equals(expected,machine.motion().portableCounts()),"Full inventory must retain exactly every removed material");
                machine.togglePause(player);machine=reload(level,machine);
                h.assertTrue(machine.paused() && Arrays.equals(expected,machine.motion().portableCounts()) && machine.getInternalInventory().getStackInSlot(0).getCount()==7,"Paused rebuild, refunds and old recovery survive reload");
                player.getInventory().setItem(0,ItemStack.EMPTY);machine.serverTick();
                h.assertTrue(player.getInventory().countItem(Items.DIAMOND)==7,"Existing recovery returns exactly once");
                player.getInventory().clearContent();player.getInventory().setItem(35,new ItemStack(Items.DIAMOND,7));
                int[] external=new int[expected.length],withdrawn=new int[expected.length];
                machine.togglePause(player);
                for(int tick=0;tick<1800 && !machine.formed();tick++){
                    // Keep all material types stocked, as a connected supply would.
                    // One-at-a-time scripted delivery spends a retry interval on every layer/type transition.
                    for(var t:TaixuStructure.Type.values())if(player.getInventory().getItem(t.ordinal()).isEmpty()){
                        external[t.ordinal()]+=64;player.getInventory().setItem(t.ordinal(),new ItemStack(TaixuStructure.block(t),64));
                    }
                    advance(level);machine.serverTick();
                    if(tick==35)machine=reload(level,machine);
                }
                h.assertTrue(machine.formed() && machine.operation()==TaixuBlockEntity.Operation.IDLE,"Survival rebuild must complete with supplied deficits: status="+machine.status()+" progress="+machine.progress()+" needed="+machine.neededMaterial()+" problem="+machine.problem());
                for(var t:TaixuStructure.Type.values()){
                    int inventory=player.getInventory().countItem(TaixuStructure.block(t).asItem());
                    var stack=machine.getInternalInventory().getStackInSlot(0);int recovery=stack.is(TaixuStructure.block(t).asItem())?stack.getCount():0;
                    int accounted=TaixuStructure.counts().getOrDefault(t,0)+machine.motion().portableCounts()[t.ordinal()]+inventory+recovery+withdrawn[t.ordinal()];
                    h.assertTrue(TaixuStructure.counts(3).getOrDefault(t,0)+external[t.ordinal()]==accounted,"Every material must be conserved: "+t);
                }
                h.assertTrue(player.getInventory().countItem(Items.DIAMOND)==7,"Rebuild must not consume unrelated inventory");
                h.assertTrue(external[TaixuStructure.Type.CASING.ordinal()]<=TaixuStructure.counts().get(TaixuStructure.Type.CASING)-TaixuStructure.counts(3).get(TaixuStructure.Type.CASING)+64,"Recovered casing must be reused before external supply");
            }finally{
                if(machine!=null)machine.motion().clear();level.setBlock(anchor,Blocks.AIR.defaultBlockState(),3);players.remove(player.getUUID());
                for(var c:chunks)level.setChunkForced(c.x,c.z,false);
            }
        }
        System.out.println("TAIXU_SUSPENDED_SURVIVAL_PASS facings=4 foreignBlocks=true protection=true fullInventory=true pauseReload=true materialConservation=true");h.succeed();
    }
    @GameTest(template="multiblock_dismantle_empty",batch="taixu_suspended_motion",timeoutTicks=1200)
    public static void runningClassicUpgradeDocksAndRestarts(GameTestHelper h) throws Exception{
        var level=h.getLevel();var anchor=new BlockPos(7168,146,5632);var chunks=TaixuStructure.chunks(anchor,Direction.NORTH,3);
        for(var c:chunks){level.setChunkForced(c.x,c.z,true);level.getChunk(c.x,c.z);}
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SuspendedMotion"));var players=players(level);players.put(player.getUUID(),player);
        TaixuBlockEntity machine=null;
        try{
            machine=fixture(level,anchor,Direction.NORTH);near(player,anchor);player.setGameMode(GameType.CREATIVE);
            machine.requestInspection(player);machine.serverTick();machine.motion().toggle(player);
            for(int i=0;i<80;i++){advance(level);machine.serverTick();}
            h.assertTrue(machine.motion().hasBodies() && machine.formed(),"V3 motion must remain compatible");
            machine.requestSuspendedUpgrade(player);h.assertTrue(machine.embedRequested() && machine.motion().mode()==TaixuMotionState.DOCKING,"Upgrade must wait for docking");
            machine=reload(level,machine);h.assertTrue(machine.embedRequested(),"Queued upgrade survives reload");
            for(int i=0;i<900 && !(machine.structureVersion()==4 && machine.formed());i++){advance(level);machine.serverTick();}
            h.assertTrue(machine.structureVersion()==4 && machine.formed() && !machine.motion().hasBodies(),"Dock and automatic reconstruction must finish");
            machine.motion().toggle(player);
            var bodies=TaixuMotionWorld.bodies(level).stream().filter(b->b.controller().equals(anchor)&&!b.isRemoved()).toList();
            h.assertTrue(bodies.size()==11 && machine.motion().mode()==TaixuMotionState.RUNNING,"New assembly must start eleven components");
            for(var body:bodies){h.assertTrue(body.structureVersion()==4,"Entity must select V4 geometry");var bb=body.getBoundingBox();body.lerpTo(body.getX(),body.getY(),body.getZ(),0,0,3);h.assertTrue(bb.equals(body.getBoundingBox()),"Packet update must preserve bounds");}
            System.out.println("TAIXU_SUSPENDED_MOTION_PASS classicCompatible=true queueReload=true dockFirst=true autoBuild=true restartBodies=11 bounds=true");h.succeed();
        }finally{if(machine!=null)machine.motion().clear();level.setBlock(anchor,Blocks.AIR.defaultBlockState(),3);players.remove(player.getUUID());for(var c:chunks)level.setChunkForced(c.x,c.z,false);}
    }
    @SuppressWarnings("unchecked") private static Map<UUID,ServerPlayer> players(ServerLevel level)throws Exception{var f=PlayerList.class.getDeclaredField("playersByUUID");f.setAccessible(true);return (Map<UUID,ServerPlayer>)f.get(level.getServer().getPlayerList());}
    private static void near(ServerPlayer p,BlockPos pos){p.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);}
    private static void advance(ServerLevel l){((ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);}
    private static TaixuBlockEntity reload(ServerLevel l,TaixuBlockEntity m){var p=m.getBlockPos();var t=m.saveWithFullMetadata(l.registryAccess());var s=m.getBlockState();m.motion().discardBodies();l.removeBlockEntity(p);var r=(TaixuBlockEntity)BlockEntity.loadStatic(p,s,t,l.registryAccess());l.setBlockEntity(r);return r;}
    private static TaixuBlockEntity fixture(ServerLevel l,BlockPos a,Direction f){
        for(var p:TaixuStructure.parts())l.setBlock(TaixuStructure.worldPos(a,f,p,3),Blocks.AIR.defaultBlockState(),2);
        l.setBlock(a,Blocks.AIR.defaultBlockState(),3);
        for(var p:TaixuStructure.parts(3))l.setBlock(TaixuStructure.worldPos(a,f,p,3),TaixuStructure.state(p,f),2);
        for(var p:TaixuStructure.requiredAir())l.setBlock(TaixuStructure.worldPos(a,f,p,3),Blocks.AIR.defaultBlockState(),2);
        var m=(TaixuBlockEntity)l.getBlockEntity(a);var t=m.saveWithFullMetadata(l.registryAccess());t.putInt("taixuLayout",3);t.putInt("taixuVersion",3);m.loadTag(t,l.registryAccess());return m;
    }
}
