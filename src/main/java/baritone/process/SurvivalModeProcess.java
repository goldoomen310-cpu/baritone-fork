package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SurvivalModeProcess extends BaritoneProcessHelper {

    public enum Mode {
        BEAT_SURVIVAL,
        RESOURCES
    }

    private Mode mode = Mode.BEAT_SURVIVAL;
    private BlockPos activeTarget;

    public SurvivalModeProcess(Baritone baritone) {
        super(baritone);
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.BEAT_SURVIVAL : mode;
        this.activeTarget = null;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public boolean isActive() {
        return ctx.player() != null && ctx.world() != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (mode == Mode.RESOURCES) {
            return handleResourcesMode();
        }
        return handleBeatSurvivalMode();
    }

    private PathingCommand handleBeatSurvivalMode() {
        if (baritone.getWorldStateTracker() != null && baritone.getWorldStateTracker().isNighttime()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (baritone.getStarterBaseProcess() != null && !baritone.getStarterBaseProcess().isTemporary()) {
            // no-op, placeholder for future planner state
        }

        if (baritone.getBuilderProcess().isActive()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (baritone.getFoodAcquisitionProcess().isActive()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        BlockPos bed = findNearbyBed();
        if (bed != null && ctx.player().getFoodData().getFoodLevel() < 14) {
            return new PathingCommand(new GoalBlock(bed), PathingCommandType.SET_GOAL_AND_PATH);
        }

        BlockPos wood = findNearbyBlock(Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG);
        if (wood != null) {
            activeTarget = wood;
            return new PathingCommand(new GoalBlock(wood), PathingCommandType.SET_GOAL_AND_PATH);
        }

        BlockPos animal = findNearbyFoodAnimal();
        if (animal != null) {
            activeTarget = animal;
            return new PathingCommand(new GoalBlock(animal), PathingCommandType.SET_GOAL_AND_PATH);
        }

        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private PathingCommand handleResourcesMode() {
        if (baritone.getWorldStateTracker() != null && baritone.getWorldStateTracker().isLowFood()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        BlockPos richTarget = findResourceTarget();
        if (richTarget != null) {
            activeTarget = richTarget;
            return new PathingCommand(new GoalNear(richTarget, 1), PathingCommandType.SET_GOAL_AND_PATH);
        }

        BlockPos wood = findNearbyBlock(Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG);
        if (wood != null) {
            activeTarget = wood;
            return new PathingCommand(new GoalBlock(wood), PathingCommandType.SET_GOAL_AND_PATH);
        }

        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private BlockPos findNearbyBlock(net.minecraft.world.level.block.Block... blocks) {
        if (ctx.world() == null || ctx.player() == null) {
            return null;
        }
        BlockPos center = ctx.playerFeet();
        int radius = 48;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = ctx.world().getBlockState(pos);
                    for (var block : blocks) {
                        if (state.is(block)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findResourceTarget() {
        if (ctx.world() == null || ctx.player() == null) {
            return null;
        }

        BlockPos center = ctx.playerFeet();
        int radius = 64;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = ctx.world().getBlockState(pos);
                    if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                            || state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)
                            || state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)
                            || state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)
                            || state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                            || state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
                            || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
                        double dist = pos.distSqr(center);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findNearbyBed() {
        BlockPos center = ctx.playerFeet();
        int radius = 24;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (ctx.world().getBlockState(pos).is(Blocks.RED_BED) || ctx.world().getBlockState(pos).is(Blocks.BLUE_BED)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findNearbyFoodAnimal() {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : ctx.entitiesStream().toList()) {
            if (!(entity instanceof Animal animal)) {
                continue;
            }
            if (!(animal instanceof Cow || animal instanceof Pig || animal instanceof Chicken || animal instanceof Sheep)) {
                continue;
            }
            double dist = entity.distanceToSqr(ctx.player());
            if (dist < bestDist) {
                bestDist = dist;
                best = entity.blockPosition();
            }
        }
        return best;
    }

    @Override
    public void onLostControl() {
        activeTarget = null;
    }

    @Override
    public String displayName0() {
        return mode == Mode.RESOURCES ? "Resources Mode" : "Beat Survival Mode";
    }

    @Override
    public double priority() {
        return 11.0;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
