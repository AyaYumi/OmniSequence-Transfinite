package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.entity.SingularityAssemblyEntity;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularitySuspendedGameTests {
    @GameTest(template="multiblock_dismantle_empty",batch="singularity_suspended",timeoutTicks=1200)
    public static void protectedUpgradeAndMaterialConservation(GameTestHelper h) throws Exception {
        var level=h.getLevel();var players=players(level);int index=0;
        for(var facing:Direction.Plane.HORIZONTAL){
            var anchor=new BlockPos(5632+256*index++,146,5632);
            var chunks=SingularityStructure.chunks(anchor,facing,3);
            for(var c:chunks){level.setChunkForced(c.x,c.z,true);level.getChunk(c.x,c.z);}
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Suspended"+index));players.put(player.getUUID(),player);
            SingularityBlockEntity machine=null;
            try{
                machine=fixture(level,anchor,facing);near(player,anchor);player.setGameMode(GameType.SURVIVAL);
                machine.requestInspection(player);machine.serverTick();h.assertTrue(machine.formed(),"V3 fixture must form");
                var old=new HashMap<BlockPos,SingularityStructure.Part>();SingularityStructure.parts(3).forEach(p->old.put(p.pos(),p));
                var next=new HashMap<BlockPos,SingularityStructure.Part>();SingularityStructure.parts().forEach(p->next.put(p.pos(),p));
                var newPart=SingularityStructure.parts().stream().filter(p->!old.containsKey(p.pos())).findFirst().orElseThrow();
                var foreign=machine.worldPos(newPart);level.setBlock(foreign,Blocks.STONE.defaultBlockState(),2);
                machine.requestSuspendedUpgrade(player);machine.serverTick();
                h.assertTrue(machine.structureVersion()==3 && machine.status()==SingularityBlockEntity.Status.CONFLICT && level.getBlockState(foreign).is(Blocks.STONE),"Foreign blocks must reject upgrade before removals");
                level.setBlock(foreign,Blocks.AIR.defaultBlockState(),2);
                var removed=SingularityStructure.parts(3).stream().filter(p->!p.equals(next.get(p.pos()))).toList();
                var protectedPos=machine.worldPos(removed.get(removed.size()-1));
                Consumer<BlockEvent.BreakEvent> deny=e->{if(e.getPos().equals(protectedPos))e.setCanceled(true);};
                MinecraftForge.EVENT_BUS.addListener(deny);
                try{machine.requestSuspendedUpgrade(player);machine.serverTick();h.assertTrue(machine.status()==SingularityBlockEntity.Status.PROTECTED,"Protection must reject upgrade");}
                finally{MinecraftForge.EVENT_BUS.unregister(deny);}
                for(var p:SingularityStructure.parts(3))h.assertTrue(SingularityStructure.matches(level.getBlockState(machine.worldPos(p)),p,facing),"Rejected upgrade must leave all original blocks intact");
                for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
                machine.getInternalInventory().setItemDirect(0,new ItemStack(Items.DIAMOND,7));
                machine.requestSuspendedUpgrade(player);machine.serverTick();
                h.assertTrue(machine.structureVersion()==4 && machine.operation()==SingularityBlockEntity.Operation.BUILD,"Upgrade must enter resumable construction");
                int[] expected=new int[SingularityStructure.Type.values().length];removed.forEach(p->expected[p.type().ordinal()]++);
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
                    for(var t:SingularityStructure.Type.values())if(player.getInventory().getItem(t.ordinal()).isEmpty()){
                        external[t.ordinal()]+=64;player.getInventory().setItem(t.ordinal(),new ItemStack(SingularityStructure.block(t),64));
                    }
                    advance(level);machine.serverTick();
                    if(tick==35)machine=reload(level,machine);
                }
                h.assertTrue(machine.formed() && machine.operation()==SingularityBlockEntity.Operation.IDLE,"Survival rebuild must complete with supplied deficits: status="+machine.status()+" progress="+machine.progress()+" needed="+machine.neededMaterial()+" problem="+machine.problem());
                for(var t:SingularityStructure.Type.values()){
                    int inventory=player.getInventory().countItem(SingularityStructure.block(t).asItem());
                    var stack=machine.getInternalInventory().getStackInSlot(0);int recovery=stack.is(SingularityStructure.block(t).asItem())?stack.getCount():0;
                    int accounted=SingularityStructure.counts().getOrDefault(t,0)+machine.motion().portableCounts()[t.ordinal()]+inventory+recovery+withdrawn[t.ordinal()];
                    h.assertTrue(SingularityStructure.counts(3).getOrDefault(t,0)+external[t.ordinal()]==accounted,"Every material must be conserved: "+t);
                }
                h.assertTrue(player.getInventory().countItem(Items.DIAMOND)==7,"Rebuild must not consume unrelated inventory");
                h.assertTrue(external[SingularityStructure.Type.CASING.ordinal()]<=SingularityStructure.counts().get(SingularityStructure.Type.CASING)-SingularityStructure.counts(3).get(SingularityStructure.Type.CASING)+64,"Recovered casing must be reused before external supply");
            }finally{
                if(machine!=null)machine.motion().clear();level.setBlock(anchor,Blocks.AIR.defaultBlockState(),3);players.remove(player.getUUID());
                for(var c:chunks)level.setChunkForced(c.x,c.z,false);
            }
        }
        System.out.println("SINGULARITY_SUSPENDED_SURVIVAL_PASS facings=4 foreignBlocks=true protection=true fullInventory=true pauseReload=true materialConservation=true");h.succeed();
    }
    @GameTest(template="multiblock_dismantle_empty",batch="singularity_suspended_motion",timeoutTicks=1200)
    public static void runningClassicUpgradeDocksAndRestarts(GameTestHelper h) throws Exception{
        var level=h.getLevel();var anchor=new BlockPos(7168,146,5632);var chunks=SingularityStructure.chunks(anchor,Direction.NORTH,3);
        for(var c:chunks){level.setChunkForced(c.x,c.z,true);level.getChunk(c.x,c.z);}
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SuspendedMotion"));var players=players(level);players.put(player.getUUID(),player);
        SingularityBlockEntity machine=null;
        try{
            machine=fixture(level,anchor,Direction.NORTH);near(player,anchor);player.setGameMode(GameType.CREATIVE);
            machine.requestInspection(player);machine.serverTick();machine.motion().startForCollection(player);
            for(int i=0;i<80;i++){advance(level);machine.motion().serverTick();}
            h.assertTrue(machine.motion().hasBodies() && machine.formed(),"V3 motion must remain compatible");
            machine.requestSuspendedUpgrade(player);h.assertTrue(machine.embedRequested() && machine.motion().mode()==SingularityMotionState.DOCKING,"Upgrade must wait for docking");
            machine=reload(level,machine);h.assertTrue(machine.embedRequested(),"Queued upgrade survives reload");
            for(int i=0;i<900 && !(machine.structureVersion()==4 && machine.formed());i++){advance(level);machine.serverTick();}
            h.assertTrue(machine.structureVersion()==4 && machine.formed() && !machine.motion().hasBodies(),"Dock and automatic reconstruction must finish: version="+machine.structureVersion()+", formed="+machine.formed()+", bodies="+machine.motion().hasBodies()+", mode="+machine.motion().mode()+", motion="+machine.motion().message()+", operation="+machine.operation()+", status="+machine.status()+", problem="+machine.problem());
            machine.motion().startForCollection(player);
            var bodies=SingularityMotionWorld.bodies(level).stream().filter(b->b.controller().equals(anchor)&&!b.isRemoved()).toList();
            h.assertTrue(bodies.size()==11 && machine.motion().mode()==SingularityMotionState.RUNNING,"New assembly must start eleven components");
            for(var body:bodies){h.assertTrue(body.structureVersion()==4,"Entity must select V4 geometry");var bb=body.getBoundingBox();body.lerpTo(body.getX(),body.getY(),body.getZ(),0,0,3,false);h.assertTrue(bb.equals(body.getBoundingBox()),"Packet update must preserve bounds");}
            System.out.println("SINGULARITY_SUSPENDED_MOTION_PASS classicCompatible=true queueReload=true dockFirst=true autoBuild=true restartBodies=11 bounds=true");h.succeed();
        }finally{if(machine!=null)machine.motion().clear();level.setBlock(anchor,Blocks.AIR.defaultBlockState(),3);players.remove(player.getUUID());for(var c:chunks)level.setChunkForced(c.x,c.z,false);}
    }
    @SuppressWarnings("unchecked") private static Map<UUID,ServerPlayer> players(ServerLevel level)throws Exception{var f=PlayerList.class.getDeclaredField("playersByUUID");f.setAccessible(true);return (Map<UUID,ServerPlayer>)f.get(level.getServer().getPlayerList());}
    private static void near(ServerPlayer p,BlockPos pos){p.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);}
    private static void advance(ServerLevel l){((ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);}
    private static SingularityBlockEntity reload(ServerLevel l,SingularityBlockEntity m){var p=m.getBlockPos();var t=m.saveWithFullMetadata();var s=m.getBlockState();m.motion().discardBodies();l.removeBlockEntity(p);var r=(SingularityBlockEntity)BlockEntity.loadStatic(p,s,t);l.setBlockEntity(r);return r;}
    private static SingularityBlockEntity fixture(ServerLevel l,BlockPos a,Direction f){
        // Tests reuse disposable worlds. Clear the full movement envelope so a
        // previous V4 fixture cannot obstruct the older V3 docking path.
        var low=SingularityStructure.worldPos(a,f,new BlockPos(-52,-4,-52),3);
        var high=SingularityStructure.worldPos(a,f,new BlockPos(52,130,52),3);
        for(var pos:BlockPos.betweenClosed(low,high))
            if(!l.getBlockState(pos).isAir())l.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        for(var p:SingularityStructure.parts())l.setBlock(SingularityStructure.worldPos(a,f,p,3),Blocks.AIR.defaultBlockState(),2);
        l.setBlock(a,Blocks.AIR.defaultBlockState(),3);
        for(var p:SingularityStructure.parts(3))l.setBlock(SingularityStructure.worldPos(a,f,p,3),SingularityStructure.state(p,f),2);
        for(var p:SingularityStructure.requiredAir())l.setBlock(SingularityStructure.worldPos(a,f,p,3),Blocks.AIR.defaultBlockState(),2);
        var m=(SingularityBlockEntity)l.getBlockEntity(a);var t=m.saveWithFullMetadata();t.putInt("singularityLayout",3);t.putInt("singularityVersion",3);m.loadTag(t);return m;
    }
}
