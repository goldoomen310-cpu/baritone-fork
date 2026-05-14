package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;

public class DeathRecoveryProcess extends BaritoneProcessHelper {

    private BlockPos lastDeathPos;
    private long deathTime;
    private boolean recovering;

    public DeathRecoveryProcess(Baritone baritone) {
        super(baritone);
    }

    public void markDeath(BlockPos pos) {
        this.lastDeathPos = pos;
        this.deathTime = System.currentTimeMillis();
        this.recovering = true;
        logNotification("Death position saved", false);
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }
        if (!recovering || lastDeathPos == null) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - deathTime;
        return elapsed < 300000L;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (lastDeathPos == null) {
            recovering = false;
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        double dist = ctx.player().distanceToSqr(
                lastDeathPos.getX() + 0.5,
                lastDeathPos.getY() + 0.5,
                lastDeathPos.getZ() + 0.5
        );
        if (dist < 16) {
            recovering = false;
            logNotification("Death recovery completed", false);
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        Goal goal = new GoalNear(lastDeathPos, 2);
        return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
    }

    @Override
    public void onLostControl() {
    }

    @Override
    public String displayName0() {
        return "Death Recovery";
    }

    @Override
    public double priority() {
        return 9.0;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
