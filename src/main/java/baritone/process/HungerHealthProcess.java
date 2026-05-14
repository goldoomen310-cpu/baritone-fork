package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.stream.Collectors;

public class HungerHealthProcess extends BaritoneProcessHelper {

    private static final int HUNGER_THRESHOLD = 10;
    private static final int HEALTH_FLEE_THRESHOLD = 6;
    private static final int THREAT_RANGE = 16;
    private static final int FLEE_DISTANCE = 50;
    private static final int EAT_TIMEOUT_TICKS = 150;

    private State state = State.IDLE;
    private int eatTicks = 0;
    private int lastFoodLevel = 20;

    public HungerHealthProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }

        if (state == State.FLEEING) {
            return true;
        }

        if (ctx.player().getHealth() < HEALTH_FLEE_THRESHOLD && hasNearbyThreat()) {
            return true;
        }

        if (ctx.player().getFoodData().getFoodLevel() < HUNGER_THRESHOLD) {
            return true;
        }

        if (state == State.EATING && ctx.player().isUsingItem()) {
            return true;
        }

        state = State.IDLE;
        return false;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        float health = ctx.player().getHealth();
        int foodLevel = ctx.player().getFoodData().getFoodLevel();

        if (health < HEALTH_FLEE_THRESHOLD && hasNearbyThreat()) {
            state = State.FLEEING;
            Goal fleeGoal = findFleeGoal();
            if (fleeGoal != null) {
                logNotification("Fleeing! Health: " + String.format("%.1f", health), false);
                return new PathingCommand(fleeGoal, PathingCommandType.SET_GOAL_AND_PATH);
            }
        }

        if (state == State.FLEEING) {
            if (!hasNearbyThreat() || ctx.player().getHealth() >= HEALTH_FLEE_THRESHOLD + 4) {
                state = State.IDLE;
            } else {
                Goal fleeGoal = findFleeGoal();
                if (fleeGoal != null) {
                    return new PathingCommand(fleeGoal, PathingCommandType.SET_GOAL_AND_PATH);
                }
            }
        }

        if (foodLevel < HUNGER_THRESHOLD || state == State.EATING) {
            return handleEating();
        }

        state = State.IDLE;
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private PathingCommand handleEating() {
        if (!ctx.player().isUsingItem()) {
            if (state != State.EATING) {
                if (!selectFood()) {
                    state = State.IDLE;
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
                state = State.EATING;
                eatTicks = 0;
                lastFoodLevel = ctx.player().getFoodData().getFoodLevel();
            }
            baritone.getLookBehavior().updateTarget(new Rotation(0, -90), false);
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            eatTicks++;
        } else {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            eatTicks++;
        }

        int currentFood = ctx.player().getFoodData().getFoodLevel();

        if (currentFood >= HUNGER_THRESHOLD + 5) {
            stopEating();
            state = State.IDLE;
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (eatTicks > EAT_TIMEOUT_TICKS) {
            stopEating();
            state = State.IDLE;
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (currentFood != lastFoodLevel) {
            lastFoodLevel = currentFood;
            eatTicks = 0;
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private void stopEating() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
    }

    private boolean selectFood() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = ctx.player().getInventory().getNonEquipmentItems().get(i);
            if (isFood(stack)) {
                ctx.player().getInventory().setSelectedSlot(i);
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = ctx.player().getInventory().getNonEquipmentItems().get(i);
            if (isFood(stack)) {
                ctx.playerController().windowClick(
                        ctx.player().inventoryMenu.containerId, i, 7,
                        net.minecraft.world.inventory.ClickType.SWAP, ctx.player()
                );
                ctx.player().getInventory().setSelectedSlot(7);
                return true;
            }
        }
        return false;
    }

    private boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.isEdible();
    }

    private boolean hasNearbyThreat() {
        final double rSq = (double) THREAT_RANGE * THREAT_RANGE;
        return ctx.entitiesStream()
                .anyMatch(e -> e instanceof Mob && e.isAlive()
                        && e.distanceToSqr(ctx.player()) < rSq
                        && isHostile((Mob) e));
    }

    private boolean isHostile(Mob mob) {
        if (mob instanceof Enemy) {
            return true;
        }
        return mob.getLastHurtByMob() == ctx.player() || mob.getTarget() == ctx.player();
    }

    private Goal findFleeGoal() {
        final double rSq = (double) THREAT_RANGE * THREAT_RANGE;

        List<Entity> threats = ctx.entitiesStream()
                .filter(e -> e instanceof Mob && e.isAlive()
                        && e.distanceToSqr(ctx.player()) < rSq
                        && isHostile((Mob) e))
                .collect(Collectors.toList());

        if (threats.isEmpty()) {
            return null;
        }

        Entity closest = threats.get(0);
        double minDistSq = closest.distanceToSqr(ctx.player());
        for (int i = 1; i < threats.size(); i++) {
            double d = threats.get(i).distanceToSqr(ctx.player());
            if (d < minDistSq) {
                minDistSq = d;
                closest = threats.get(i);
            }
        }

        Vec3 away = ctx.player().position().subtract(closest.position()).normalize().scale(FLEE_DISTANCE);
        BlockPos fleePos = new BlockPos(
                (int) (ctx.player().position().x + away.x),
                (int) ctx.player().position().y,
                (int) (ctx.player().position().z + away.z)
        );

        return new GoalNear(fleePos, 5);
    }

    @Override
    public void onLostControl() {
        stopEating();
        state = State.IDLE;
        eatTicks = 0;
    }

    @Override
    public String displayName0() {
        return "Hunger/Health";
    }

    @Override
    public double priority() {
        return 10.0;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    private enum State {
        IDLE, EATING, FLEEING
    }
}
