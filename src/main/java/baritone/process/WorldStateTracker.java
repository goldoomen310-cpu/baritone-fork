package baritone.process;

import baritone.Baritone;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;

public class WorldStateTracker extends BaritoneProcessHelper {

    private int cachedFoodCount;
    private int hostileCount;
    private boolean nighttime;
    private boolean lowHealth;
    private boolean lowFood;
    private BlockPos playerPos;

    public WorldStateTracker(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return ctx.player() != null && ctx.world() != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        updateState();
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private void updateState() {
        playerPos = ctx.playerFeet();

        cachedFoodCount = 0;
        for (ItemStack stack : ctx.player().getInventory().items) {
            if (!stack.isEmpty() && stack.isEdible()) {
                cachedFoodCount += stack.getCount();
            }
        }

        hostileCount = 0;
        for (Entity entity : ctx.entitiesStream().toList()) {
            if (entity instanceof Enemy) {
                hostileCount++;
            } else if (entity instanceof Mob mob) {
                if (mob.getTarget() == ctx.player()) {
                    hostileCount++;
                }
            }
        }

        long time = ctx.world().getDayTime() % 24000L;
        nighttime = time > 12500L && time < 23500L;

        lowHealth = ctx.player().getHealth() < 8.0F;
        lowFood = ctx.player().getFoodData().getFoodLevel() < 10;
    }

    public int getCachedFoodCount() {
        return cachedFoodCount;
    }

    public int getHostileCount() {
        return hostileCount;
    }

    public boolean isNighttime() {
        return nighttime;
    }

    public boolean isLowHealth() {
        return lowHealth;
    }

    public boolean isLowFood() {
        return lowFood;
    }

    public BlockPos getPlayerPos() {
        return playerPos;
    }

    @Override
    public void onLostControl() {
    }

    @Override
    public String displayName0() {
        return "World State Tracker";
    }

    @Override
    public double priority() {
        return -1;
    }
}
