package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FoodAcquisitionProcess extends BaritoneProcessHelper {

    private static final int FOOD_MINIMUM = 6;
    private static final int SEARCH_RADIUS = 32;

    private State state = State.IDLE;
    private BlockPos cropTarget;
    private Entity animalTarget;

    public FoodAcquisitionProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }

        if (foodCount() >= FOOD_MINIMUM && ctx.player().getFoodData().getFoodLevel() > 14) {
            state = State.IDLE;
            return false;
        }

        return true;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {

        cropTarget = findNearbyCrop();
        if (cropTarget != null) {
            state = State.HARVESTING;
            return new PathingCommand(new GoalBlock(cropTarget), PathingCommandType.SET_GOAL_AND_PATH);
        }

        animalTarget = findNearbyAnimal();
        if (animalTarget != null) {
            state = State.HUNTING;
            return new PathingCommand(new GoalBlock(animalTarget.blockPosition()), PathingCommandType.SET_GOAL_AND_PATH);
        }

        state = State.EXPLORING;
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private int foodCount() {
        int total = 0;
        for (ItemStack stack : ctx.player().getInventory().items) {
            if (!stack.isEmpty() && stack.isEdible()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private BlockPos findNearbyCrop() {
        BlockPos center = ctx.playerFeet();

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = ctx.world().getBlockState(pos);

                    if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                        return pos;
                    }

                    if (state.is(BlockTags.CROPS)) {
                        return pos;
                    }

                    if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.SWEET_BERRY_BUSH)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private Entity findNearbyAnimal() {
        double closest = Double.MAX_VALUE;
        Entity best = null;

        for (Entity entity : ctx.entitiesStream().toList()) {
            if (!(entity instanceof Animal)) {
                continue;
            }

            if (!(entity instanceof Cow || entity instanceof Pig || entity instanceof Chicken || entity instanceof Sheep)) {
                continue;
            }

            double dist = entity.distanceToSqr(ctx.player());
            if (dist < closest && dist < SEARCH_RADIUS * SEARCH_RADIUS) {
                closest = dist;
                best = entity;
            }
        }

        return best;
    }

    @Override
    public void onLostControl() {
        state = State.IDLE;
        cropTarget = null;
        animalTarget = null;
    }

    @Override
    public String displayName0() {
        return switch (state) {
            case HARVESTING -> "Harvesting Food";
            case HUNTING -> "Hunting Food";
            case EXPLORING -> "Searching Food";
            default -> "Food Acquisition";
        };
    }

    @Override
    public double priority() {
        return 8.5;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    private enum State {
        IDLE,
        HARVESTING,
        HUNTING,
        EXPLORING
    }
}
