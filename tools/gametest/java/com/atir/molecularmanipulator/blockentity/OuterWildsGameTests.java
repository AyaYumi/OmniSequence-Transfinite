package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import com.atir.molecularmanipulator.block.GravityCrystalBlock;
import com.atir.molecularmanipulator.block.GhostMatterBlock;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.world.gravity.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class OuterWildsGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 120)
    public static void gravityPlacementMotionRedstoneWallsAndPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 12, 2));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GravityTester"));
        player.setGameMode(GameType.CREATIVE);
        var chunks = loadArea(helper, pos);
        var items = new ArrayList<ItemEntity>();
        try {
            for (var direction : Direction.values()) {
                var axis = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                var support = pos.relative(direction.getOpposite());
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.GRAVITY_CRYSTAL.get()));
                var hit = new BlockHitResult(pos.getCenter().subtract(axis.scale(.5)), direction, support, false);
                var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                helper.assertTrue(((BlockItem) ModContent.GRAVITY_CRYSTAL.get()).place(context).consumesAction(),
                        "Real crystal placement must work for " + direction);
                var state = level.getBlockState(pos);
                helper.assertTrue(state.getValue(DirectionalBlock.FACING) == direction, "Clicked face must orient the field");
                var crystal = (GravityCrystalBlockEntity) level.getBlockEntity(pos);
                var item = item(helper, pos.getCenter().add(axis.scale(2)), items);
                item.fallDistance = 10;
                crystal.serverTick();
                helper.assertTrue(item.getDeltaMovement().dot(axis) < -.11 && item.fallDistance == 0,
                        "Default attraction must point toward its mounting surface and reset falls: " + direction);

                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                state.useWithoutItem(level, player, new BlockHitResult(pos.getCenter(), direction, pos, false));
                helper.assertTrue(level.getBlockState(pos).getValue(GravityCrystalBlock.REPEL),
                        "Actual empty-hand interaction must switch to repulsion");
                item.setDeltaMovement(Vec3.ZERO);
                for (int i = 0; i < 40; i++) crystal.serverTick();
                helper.assertTrue(Math.abs(item.getDeltaMovement().dot(axis) - GravityCrystalBlockEntity.MAX_SPEED) < .00001,
                        "Repulsion must reach its bounded terminal speed for " + direction);
                var savedState = level.getBlockState(pos);
                var restored = (GravityCrystalBlockEntity) BlockEntity.loadStatic(pos, savedState,
                        crystal.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                helper.assertTrue(restored != null && restored.getBlockState().getValue(GravityCrystalBlock.REPEL)
                        && restored.getBlockState().getValue(DirectionalBlock.FACING) == direction,
                        "World block state and block entity must survive reload");

                var wire = pos.relative(direction.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST);
                level.setBlock(wire, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                helper.assertTrue(level.getBlockState(pos).getValue(GravityCrystalBlock.POWERED), "Real redstone must pause the field");
                item.setDeltaMovement(Vec3.ZERO);
                crystal.serverTick();
                helper.assertTrue(item.getDeltaMovement().lengthSqr() == 0, "Powered crystal must impart no force");
                level.setBlock(wire, Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(!level.getBlockState(pos).getValue(GravityCrystalBlock.POWERED), "Removing redstone must resume");
                var wall = pos.relative(direction, 2);
                level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                crystal.serverTick();
                helper.assertTrue(item.getDeltaMovement().lengthSqr() == 0, "Solid walls must block a crystal field");
                level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
                item.discard();
                var outside = item(helper, pos.getCenter().add(axis.scale(3)), items);
                crystal.serverTick();
                helper.assertTrue(outside.getDeltaMovement().lengthSqr() == 0, "Entities outside the centered five-block cube must be unaffected");
                outside.discard();
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(support, Blocks.AIR.defaultBlockState(), 3);
            }
            // A standing living entity must be able to ride a floor-mounted lift.
            level.setBlock(pos, ModContent.GRAVITY_CRYSTAL_BLOCK.get().defaultBlockState().setValue(GravityCrystalBlock.REPEL, true), 3);
            var sheep = EntityType.SHEEP.create(level);
            sheep.setPos(pos.getX() + .5, pos.getY() + 2, pos.getZ() + .5);
            sheep.setNoAi(true);
            level.addFreshEntity(sheep);
            ((GravityCrystalBlockEntity) level.getBlockEntity(pos)).serverTick();
            helper.assertTrue(sheep.getDeltaMovement().y > .11, "Lift must affect living entities as well as drops");
            sheep.discard();
            // Exercise both lateral signs and the exact center cutoff, including overlapping entity bounds.
            level.setBlock(pos, ModContent.GRAVITY_CRYSTAL_BLOCK.get().defaultBlockState(), 3);
            var field = (GravityCrystalBlockEntity) level.getBlockEntity(pos);
            for (double side : new double[]{-1, 1}) {
                var inside = item(helper, pos.getCenter().add(side * 2.49, 2, side * 2.49), items);
                var outsideX = item(helper, pos.getCenter().add(side * 2.51, 2, 0), items);
                var outsideY = item(helper, pos.getCenter().add(0, 2.51, 0), items);
                var outsideZ = item(helper, pos.getCenter().add(0, 2, side * 2.51), items);
                field.serverTick();
                helper.assertTrue(inside.getDeltaMovement().y < -.11, "Centered field must include lateral cube corners");
                helper.assertTrue(outsideX.getDeltaMovement().lengthSqr() == 0
                        && outsideY.getDeltaMovement().lengthSqr() == 0 && outsideZ.getDeltaMovement().lengthSqr() == 0,
                        "Every axis must stop at 2.5 blocks from the center");
                inside.discard(); outsideX.discard(); outsideY.discard(); outsideZ.discard();
            }
            System.out.println("OUTER_WILDS_GRAVITY_PASS sixFaces=true realInteraction=true redstone=true walls=true bounded=true reload=true living=true");
            helper.succeed();
        } finally {
            items.forEach(ItemEntity::discard);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            // Keep shared test chunks ticking until this disposable GameTest server exits.
        }
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "ghost_matter", timeoutTicks = 120)
    public static void ghostMatterDamageWaterWallsAndItemSafety(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 8, 2));
        var chunks = loadArea(helper, pos);
        var sheep = EntityType.SHEEP.create(level);
        sheep.setNoAi(true);
        sheep.setNoGravity(true);
        sheep.setPos(pos.getX() + 1.25, pos.getY(), pos.getZ() + .5);
        var items = new ArrayList<ItemEntity>();
        helper.startSequence().thenIdle(20)
                .thenExecute(() -> chunks.addAll(loadArea(helper, pos)))
                .thenIdle(2)
                .thenWaitUntil(() -> helper.assertTrue(level.getGameTime() % 10 == 0, "Waiting for hazard tick"))
                .thenExecute(() -> {
            try {
                // Other tests' template initialization can clear nearby entities; spawn after that phase.
                chunks.addAll(loadArea(helper, pos));
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos, ModContent.GHOST_MATTER_BLOCK.get().defaultBlockState(), 3);
                var ghost = (GhostMatterBlockEntity) level.getBlockEntity(pos);
                level.addFreshEntity(sheep);
                var drop = item(helper, pos.getCenter(), items);
                sheep.invulnerableTime = 0;
                sheep.setHealth(sheep.getMaxHealth());
                sheep.setPos(pos.getX() + 1.25, pos.getY(), pos.getZ() + .5);
                sheep.setDeltaMovement(Vec3.ZERO);
                ghost.serverTick();
                helper.assertTrue(sheep.getHealth() == sheep.getMaxHealth() - 1,
                        "Exposed living entities must take one continuous ghost-matter damage: health=" + sheep.getHealth()
                                + " time=" + level.getGameTime() + " pos=" + sheep.position() + " removed=" + sheep.isRemoved()
                                + " near=" + level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                                    new AABB(pos).inflate(3)).size() + " water=" + sheep.isInWaterOrBubble()
                                + " state=" + ghost.getBlockState() + " bbox=" + sheep.getBoundingBox());
                helper.assertTrue(sheep.getDeltaMovement().equals(Vec3.ZERO), "Exposure must impart no knockback");
                level.setBlock(pos.north().below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos.north(), ModContent.GHOST_MATTER_BLOCK.get().defaultBlockState(), 3);
                ((GhostMatterBlockEntity) level.getBlockEntity(pos.north())).serverTick();
                helper.assertTrue(sheep.getHealth() == sheep.getMaxHealth() - 1,
                        "Overlapping real deposits must not multiply exposure damage");
                level.setBlock(pos.north(), Blocks.AIR.defaultBlockState(), 3);
                var source = new net.minecraft.world.damagesource.DamageSource(level.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(GhostMatterBlockEntity.DAMAGE_TYPE));
                helper.assertTrue(source.is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK)
                        && source.is(net.minecraft.tags.DamageTypeTags.NO_IMPACT)
                        && source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)
                        && source.is(net.neoforged.neoforge.common.Tags.DamageTypes.NO_FLINCH),
                        "Registered exposure tags must suppress impact/flinch and keep damage continuous");
                helper.assertTrue(sheep.hurt(source, 1F), "Ghost damage must continue during ordinary hurt immunity");
                helper.assertTrue(drop.isAlive() && drop.getItem().is(Items.DIAMOND), "Hazard must preserve dropped items");
                level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.WATERLOGGED, true), 3);
                sheep.invulnerableTime = 0;
                float health = sheep.getHealth();
                ghost.serverTick();
                helper.assertTrue(sheep.getHealth() == health && level.getFluidState(pos).isSource(),
                        "Waterlogged matter must remain present but completely harmless");
                level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.WATERLOGGED, false), 3);
                level.setBlock(pos.east(), Blocks.WATER.defaultBlockState(), 3);
                sheep.baseTick();
                helper.assertTrue(sheep.isInWaterOrBubble(), "Water protection fixture must immerse the actual entity");
                ghost.serverTick();
                helper.assertTrue(sheep.getHealth() == health, "Immersed creatures must be protected from dry nearby matter");
                level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
                sheep.baseTick();
                sheep.setPos(pos.getX() + 2.25, pos.getY(), pos.getZ() + .5);
                ghost.serverTick();
                helper.assertTrue(sheep.getHealth() == health, "Matter has a bounded radius");
                sheep.setPos(pos.getX() + 1.25, pos.getY(), pos.getZ() + .5);
                level.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), 3);
                ghost.serverTick();
                helper.assertTrue(sheep.getHealth() == health, "Solid walls must shield nearby living entities");
                level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
                sheep.getPersistentData().remove("omnisequenceGhostExposure");
                ghost.serverTick();
                helper.assertTrue(sheep.getHealth() == health - 1, "Drained matter must resume its hazard");
                System.out.println("OUTER_WILDS_GHOST_PASS damage=true waterlogged=true immersion=true walls=true radius=true items=preserved");
                helper.succeed();
            } finally {
                sheep.discard();
                items.forEach(ItemEntity::discard);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
                // Keep shared test chunks ticking until this disposable GameTest server exits.
            }
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "ghost_matter_flow", timeoutTicks = 400)
    public static void ghostMatterNaturalFlowAndDrainPreserveCrystals(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 8, 2));
        loadArea(helper, pos);
        var deposits = new ArrayList<BlockPos>();
        var source = pos.offset(-2, 0, 0);
        helper.startSequence().thenIdle(22).thenExecute(() -> {
            loadArea(helper, pos);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                level.setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos.offset(x, 0, z), (Math.abs(x) == 3 || Math.abs(z) == 3
                        ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var at = pos.offset(x, 0, z);
                level.setBlock(at, ModContent.GHOST_MATTER_BLOCK.get().defaultBlockState(), 3);
                deposits.add(at);
            }
            level.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
        }).thenIdle(90).thenExecute(() -> {
            for (var at : deposits) {
                helper.assertTrue(level.getBlockEntity(at) instanceof GhostMatterBlockEntity,
                        "Natural water must retain the real crystal deposit at " + at);
                helper.assertTrue(level.getFluidState(at).getType().isSame(net.minecraft.world.level.material.Fluids.WATER)
                        && !level.getFluidState(at).isSource(), "Flow must cover all 3x3 deposits without generating sources: " + at
                                + " state=" + level.getBlockState(at) + " source=" + level.getFluidState(source));
                helper.assertTrue(!level.getBlockState(at).getValue(GhostMatterBlock.DISPERSED), "Water must not break supported shards");
            }
            level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        }).thenIdle(190).thenExecute(() -> {
            for (var at : deposits) {
                helper.assertTrue(level.getBlockEntity(at) instanceof GhostMatterBlockEntity && level.getFluidState(at).isEmpty(),
                        "Natural drainage must dry the deposit and retain its crystals at " + at);
            }
            System.out.println("GHOST_MATTER_FLOW_PASS natural3x3=true flowingLevels=true noNewSources=true drain=true crystalsRetained=true");
        }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "ghost_matter_shatter", timeoutTicks = 100)
    public static void ghostMatterSupportShatterPersistenceAndExpiry(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 8, 2));
        loadArea(helper, pos);
        helper.startSequence().thenIdle(22).thenExecute(() -> {
            loadArea(helper, pos);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, ModContent.GHOST_MATTER_BLOCK.get().defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(pos).getDestroySpeed(level, pos) < 0, "Crystal deposit must be unmineable");
            level.destroyBlock(pos.below(), true);
            var state = level.getBlockState(pos);
            helper.assertTrue(state.getValue(GhostMatterBlock.DISPERSED), "Removing support must shatter the crystals into mist");
            helper.assertTrue(state.getShape(level, pos).isEmpty(), "Dispersed mist must not retain selectable crystals");
            var ghost = (GhostMatterBlockEntity) level.getBlockEntity(pos);
            ghost.serverTick();
            helper.assertTrue(ghost.expiresAt() - level.getGameTime() == 1200, "Mist must last exactly one minute");
            var tag = ghost.saveWithFullMetadata(level.registryAccess());
            var restored = (GhostMatterBlockEntity) BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
            helper.assertTrue(restored != null && restored.expiresAt() == ghost.expiresAt(), "Reload must preserve the deadline");
            var probe = EntityType.SHEEP.create(level);
            probe.setNoGravity(true);
            probe.setPos(pos.getX() + 1.9, pos.getY(), pos.getZ() + 1.9);
            helper.assertTrue(ghost.affects(probe), "Diagonal corner inside 3x3 mist must remain dangerous");
            probe.setPos(pos.getX() + 2.01, pos.getY(), pos.getZ() + .5);
            helper.assertTrue(!ghost.affects(probe), "Mist must stop at the 3x3 boundary");
            helper.assertTrue(state.getDrops(new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, pos.getCenter())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)).isEmpty(),
                    "Shattered mist must never drop Ghost Matter items");
            // Exercise the real expiry path with a shortened saved deadline, without advancing global test time.
            tag.putLong("MistExpiresAt", level.getGameTime() + 10);
            ghost.loadWithComponents(tag, level.registryAccess());
            ghost.serverTick();
            helper.assertTrue(level.getBlockEntity(pos) == ghost, "Mist must persist before the deadline");
        }).thenIdle(15).thenExecute(() -> {
            helper.assertTrue(level.getBlockState(pos).isAir(), "Expired mist must disappear without drops");
            System.out.println("GHOST_MATTER_SHATTER_PASS unmineable=true supportLoss=true emptyModel=true threeByThree=true minute1200=true persistedDeadline=true noDrops=true expires=true");
        }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 120)
    public static void playerWallCeilingWalkingJumpRangeAndRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 18, 2));
        var chunks = loadArea(helper, pos);
        var placed = new ArrayList<BlockPos>();
        try {
            for (var down : Direction.values()) {
                var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "WallWalker"));
                player.setGameMode(GameType.SURVIVAL);
                player.setNoGravity(false);
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                    var offset = GravityFrame.toWorld(down, new Vec3(x, -1, z));
                    var support = pos.offset((int) offset.x, (int) offset.y, (int) offset.z);
                    level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
                    placed.add(support);
                }
                level.setBlock(pos, ModContent.GRAVITY_CRYSTAL_BLOCK.get().defaultBlockState()
                        .setValue(DirectionalBlock.FACING, down.getOpposite()), 3);
                var center = pos.getCenter().add(GravityFrame.toWorld(down, new Vec3(0, 1.3, 1.2)));
                player.setPos(center.subtract(0, player.getBbHeight() / 2, 0));
                player.setDeltaMovement(Vec3.ZERO);
                GravityController.tick(player);
                helper.assertTrue(GravityController.direction(player) == down, "Crystal must choose actual player gravity: " + down);
                var box = player.getBoundingBox();
                double height = down.getAxis() == Direction.Axis.X ? box.getXsize()
                        : down.getAxis() == Direction.Axis.Y ? box.getYsize() : box.getZsize();
                helper.assertTrue(Math.abs(height - player.getBbHeight()) < 1.0E-6, "Collision body must rotate: " + down);
                helper.assertTrue(player.getEyePosition().subtract(player.position()).distanceTo(
                        GravityFrame.up(down).scale(player.getEyeHeight())) < 1.0E-6, "Eyes must rotate with the body: " + down);
                for (int tick = 0; tick < 35; tick++) player.travel(Vec3.ZERO);
                helper.assertTrue(player.onGround(), "Player must land on the actual floor/wall/ceiling: " + down
                        + " feet=" + player.position() + " velocity=" + player.getDeltaMovement());
                var grounded = player.position();
                player.setYRot(0);
                player.setXRot(0);
                for (int tick = 0; tick < 4; tick++) player.travel(new Vec3(0, 0, 1));
                var walked = GravityFrame.toLocal(down, player.position().subtract(grounded));
                helper.assertTrue(walked.z > .05 && Math.abs(walked.y) < 1.0E-5,
                        "Forward input must walk along the supporting surface: " + down + " " + walked);
                player.setDeltaMovement(Vec3.ZERO);
                player.jumpFromGround();
                helper.assertTrue(player.getDeltaMovement().dot(GravityFrame.up(down)) > .3,
                        "Jump must point away from the supporting surface: " + down);
                player.setYRot(20);
                player.setXRot(30);
                player.setKnownMovement(Vec3.ZERO);
                var arrow = new net.minecraft.world.entity.projectile.Arrow(level, player, new ItemStack(Items.ARROW), null);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1, 0);
                helper.assertTrue(arrow.position().distanceTo(player.getEyePosition().subtract(GravityFrame.up(down).scale(.1))) < 1.0E-5
                        && arrow.getDeltaMovement().normalize().dot(player.getLookAngle().normalize()) > .99999,
                        "Arrow origin and actual aiming must follow rotated eyes: " + down + " origin=" + arrow.position()
                                + " expected=" + player.getEyePosition().subtract(GravityFrame.up(down).scale(.1))
                                + " motion=" + arrow.getDeltaMovement() + " look=" + player.getLookAngle()
                                + " inherited=" + player.getKnownMovement());
                var dropped = player.drop(new ItemStack(Items.DIAMOND), false, false);
                helper.assertTrue(dropped != null && dropped.position().distanceTo(
                        player.getEyePosition().subtract(GravityFrame.up(down).scale(.3))) < 1.0E-5
                        && dropped.getDeltaMovement().dot(player.getLookAngle()) > .2,
                        "Dropped items must originate at the rotated hand/eye side: " + down);
                if (dropped != null) dropped.discard();
                arrow.discard();
                var serialized = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                helper.assertTrue(serialized.getByte("OmniGravityDirection") == down.get3DDataValue()
                        && serialized.getLong("OmniGravitySource") == pos.asLong(), "Player frame/source must persist");
                var data = ((GravityPlayerAccess) player).omnisequence$gravityData();
                helper.assertTrue(((Byte) data.value()).intValue() == down.get3DDataValue(), "Synced metadata must match physics");
                var restored = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ReloadWalker"));
                restored.load(serialized);
                GravityController.tick(restored);
                helper.assertTrue(GravityController.direction(restored) == down
                        && restored.position().distanceTo(player.position()) < 1.0E-6,
                        "Player reload must restore the complete gravity frame and position");
                var nearOffset = GravityFrame.toWorld(down, new Vec3(0, 0, 2));
                var nearer = pos.offset((int) nearOffset.x, (int) nearOffset.y, (int) nearOffset.z);
                level.setBlock(nearer, level.getBlockState(pos), 3);
                GravityController.tick(player);
                GravityController.tick(player);
                helper.assertTrue(nearer.equals(((GravityPlayerAccess) player).omnisequence$gravitySource()),
                        "Same-surface fields must hand off to the nearer local crystal");
                level.setBlock(nearer, Blocks.AIR.defaultBlockState(), 3);
                GravityController.tick(player);
                level.setBlock(pos, level.getBlockState(pos).setValue(GravityCrystalBlock.POWERED, true), 3);
                GravityController.tick(player);
                helper.assertTrue(GravityController.direction(player) == Direction.DOWN, "Redstone pause must restore normal gravity");
                level.setBlock(pos, level.getBlockState(pos).setValue(GravityCrystalBlock.POWERED, false), 3);
                GravityController.tick(player);
                player.setPos(pos.getCenter().add(4, 4, 4));
                GravityController.tick(player);
                helper.assertTrue(GravityController.direction(player) == Direction.DOWN, "Leaving centered range must restore gravity");
                player.setPos(center.subtract(0, player.getBbHeight() / 2, 0));
                GravityController.tick(player);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                GravityController.tick(player);
                helper.assertTrue(GravityController.direction(player) == Direction.DOWN, "Removing source must restore gravity");
                placed.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 3));
                placed.clear();
            }
            System.out.println("OUTER_WILDS_WALL_WALK_PASS sixGravityDirections=true rotatedBounds=true rotatedEyes=true walking=true jumping=true aiming=true drops=true metadata=true reload=true nearest=true redstone=true range=true removal=true");
            helper.succeed();
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            placed.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 3));
            // Keep shared test chunks ticking until this disposable GameTest server exits.
        }
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "gravity_selection", timeoutTicks = 100)
    public static void mountingSurfaceSelectionCornerStabilityAndDirectHandoff(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(2, 30, 2));
        var chunks = loadArea(helper, base);
        var placed = new ArrayList<BlockPos>();
        var floor = base;
        var wall = base.offset(0, 1, 1);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CornerWalker"));
        player.setGameMode(GameType.SURVIVAL);
        try {
            for (int a = -2; a <= 5; a++) for (int b = -2; b <= 5; b++) {
                for (var support : new BlockPos[]{base.offset(a, -1, b), base.offset(-1, a, b)}) {
                    level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
                    placed.add(support);
                }
            }
            level.setBlock(floor, ModContent.GRAVITY_CRYSTAL_BLOCK.get().defaultBlockState(), 3);
            level.setBlock(wall, ModContent.GRAVITY_CRYSTAL_BLOCK.get().defaultBlockState()
                    .setValue(DirectionalBlock.FACING, Direction.EAST), 3);
            placed.add(floor); placed.add(wall);
            centerPlayer(player, Vec3.atLowerCornerOf(base).add(1.8, .9, 1.5));
            GravityController.tick(player);
            helper.assertTrue(floor.equals(((GravityPlayerAccess) player).omnisequence$gravitySource()),
                    "Initial gravity must prefer the nearer mounting plane");
            for (int i = 0; i < 20; i++) {
                centerPlayer(player, Vec3.atLowerCornerOf(base).add(1.1 + (i % 2) * .1, 1.15, 1.5));
                GravityController.tick(player);
                helper.assertTrue(GravityController.direction(player) == Direction.DOWN,
                        "Small alternating differences at a corner must retain the current surface");
            }
            centerPlayer(player, Vec3.atLowerCornerOf(base).add(.9, 1.8, 1.5));
            for (int i = 1; i < GravityController.SWITCH_CONFIRM_TICKS; i++) {
                GravityController.tick(player);
                helper.assertTrue(GravityController.direction(player) == Direction.DOWN,
                        "A new mounting direction must be confirmed across player ticks");
            }
            var look = player.getLookAngle().normalize();
            GravityController.tick(player);
            helper.assertTrue(GravityController.direction(player) == Direction.WEST
                    && wall.equals(((GravityPlayerAccess) player).omnisequence$gravitySource()),
                    "Walking decisively toward the wall must hand off to wall gravity");
            helper.assertTrue(player.getLookAngle().normalize().dot(look) > .99999,
                    "Physical frame changes must preserve the world look direction");
            centerPlayer(player, Vec3.atLowerCornerOf(base).add(1.8, .9, 1.5));
            GravityController.tick(player); // Start, then interrupt, the reverse candidate.
            centerPlayer(player, Vec3.atLowerCornerOf(base).add(.9, 1.8, 1.5));
            GravityController.tick(player);
            helper.assertTrue(((GravityPlayerAccess) player).omnisequence$gravityCandidate() == null,
                    "An interrupted crossing must clear its confirmation history");
            centerPlayer(player, Vec3.atLowerCornerOf(base).add(1.8, .9, 1.5));
            for (int i = 0; i < GravityController.SWITCH_CONFIRM_TICKS; i++) GravityController.tick(player);
            helper.assertTrue(GravityController.direction(player) == Direction.DOWN,
                    "A confirmed return to the floor must restore floor gravity");
            level.setBlock(floor, level.getBlockState(floor).setValue(GravityCrystalBlock.POWERED, true), 3);
            GravityController.tick(player);
            helper.assertTrue(GravityController.direction(player) == Direction.WEST,
                    "Disabling the current source must hand off directly to another valid surface");
            // An exposed crystal must never claim the rear side of its installation plane, even without a wall.
            level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
            placed.stream().filter(p -> p.getX() == base.getX() - 1).forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 3));
            centerPlayer(player, Vec3.atLowerCornerOf(base).add(-.7, 1.3, 1.5));
            GravityController.tick(player);
            helper.assertTrue(GravityController.direction(player) == Direction.DOWN
                    && ((GravityPlayerAccess) player).omnisequence$gravitySource() == null,
                    "The rear side of a mounting plane must not attract players");
            System.out.println("OUTER_WILDS_GRAVITY_SELECTION_PASS mountingPlanes=true cornerHysteresis=true confirmation=true interruptedCrossing=true directHandoff=true exposedSide=true");
            helper.succeed();
        } finally {
            placed.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 3));
            // Keep shared test chunks ticking until this disposable GameTest server exits.
        }
    }

    private static void centerPlayer(net.minecraft.world.entity.player.Player player, Vec3 center) {
        player.setPos(center.subtract(GravityFrame.up(GravityController.direction(player)).scale(player.getBbHeight() / 2)));
        player.setDeltaMovement(Vec3.ZERO);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void loadedRecipesResearchAndCatalystEncoding(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var gravity = (MatterFabricationRecipe) recipes.byKey(id("gravity_crystal")).orElseThrow().value();
        var ghost = (MatterFabricationRecipe) recipes.byKey(id("ghost_matter")).orElseThrow().value();
        var research = (MatterResearchRecipe) recipes.byKey(id("research/nomai_materials")).orElseThrow().value();
        helper.assertTrue(gravity.catalysts().size() == 1
                && gravity.catalysts().getFirst().ingredient().test(ModContent.GHOST_MATTER.get().getDefaultInstance())
                && gravity.catalysts().getFirst().count() == 1, "Real recipe must retain one Ghost Matter catalyst");
        var encoded = MatterFabricationPatternEncoding.encode(gravity,
                gravity.ingredients().stream().map(ingredient -> ingredient.ingredient().getItems()[0]).toList());
        var details = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        helper.assertTrue(details != null && details.getInputs().length == 3, "Encoding must include exactly three consumables");
        for (var input : details.getInputs()) for (var stack : input.getPossibleInputs()) {
            helper.assertTrue(!stack.what().equals(AEItemKey.of(ModContent.GHOST_MATTER.get())),
                    "Ghost Matter must never be included in encoded pattern inputs");
        }
        helper.assertTrue(ghost.results().getFirst().is(ModContent.GHOST_MATTER.get())
                && ghost.results().getFirst().getCount() == 4
                && gravity.results().getFirst().is(ModContent.GRAVITY_CRYSTAL.get()), "Loaded recipes must produce registered items");
        helper.assertTrue(research.prerequisites().equals(List.of(id("research/ae_foundation")))
                && research.unlocks().containsAll(List.of(id("ghost_matter"), id("gravity_crystal")))
                && gravity.requiresResearch() && ghost.requiresResearch(), "Research must provide an acyclic reachable unlock path");
        System.out.println("OUTER_WILDS_RECIPES_PASS ghost=4 gravity=1 catalyst=retained encodedInputs=3 research=reachable");
        helper.succeed();
    }

    private static ItemEntity item(GameTestHelper helper, Vec3 center, List<ItemEntity> items) {
        var item = new ItemEntity(helper.getLevel(), center.x, center.y - .125, center.z, new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        items.add(item);
        return item;
    }

    private static Set<net.minecraft.world.level.ChunkPos> loadArea(GameTestHelper helper, BlockPos pos) {
        var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
        for (int x = -12; x <= 12; x += 12) for (int z = -12; z <= 12; z += 12) {
            var chunk = new net.minecraft.world.level.ChunkPos(pos.offset(x, 0, z));
            if (chunks.add(chunk)) {
                helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                helper.getLevel().getChunkAt(pos.offset(x, 0, z));
            }
        }
        return chunks;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", path);
    }
}
