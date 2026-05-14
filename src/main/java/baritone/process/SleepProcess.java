package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

public class SleepProcess extends BaritoneProcessHelper {

    private State state = State.IDLE;
    private BlockPos bedPos;
    private int sleepTicks;

    public SleepProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }
        if (state == State.SLEEPING || state == State.GOING_TO_BED) {
            return true;
        }
        if (!shouldSleep()) {
            return false;
        }
        BlockPos existing = findNearbyBed();
        if (existing != null) {
            bedPos = existing;
            state = State.GOING_TO_BED;
            return true;
        }
        return false;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        switch (state) {
            case GOING_TO_BED -> {
                if (bedPos == null) {
                    state = State.IDLE;
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
                double dist = ctx.player().distanceToSqr(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5);
                if (dist < 9) {
                    state = State.SLEEPING;
                    sleepTicks = 0;
                    break;
                }
                Goal goal = new GoalBlock(bedPos);
                return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
            }
            case SLEEPING -> {
                if (!shouldSleep() && !ctx.player().isSleeping()) {
                    stopSleeping();
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
                if (!ctx.player().isSleeping()) {
                    if (bedPos == null || !isStillBed(bedPos)) {
                        BlockPos nearby = findNearbyBed();
                        if (nearby != null) {
                            bedPos = nearby;
                        } else {
                            stopSleeping();
                            return new PathingCommand(null, PathingCommandType.DEFER);
                        }
                    }
                    if (sleepTicks < 2) {
                        Vec3 bedCenter = new Vec3(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5);
                        Vec3 eyes = ctx.player().getEyePosition(1.0F);
                        Vec3 diff = bedCenter.subtract(eyes);
                        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
                        float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
                        baritone.getLookBehavior().updateTarget(new Rotation(yaw, pitch), true);
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                    }
                    sleepTicks++;
                    if (sleepTicks >= 5) {
                        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
                        sleepTicks = 0;
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                } else {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
                    if (ctx.player().getSleepTimer() >= 100) {
                        stopSleeping();
                        return new PathingCommand(null, PathingCommandType.DEFER);
                    }
                }
                sleepTicks++;
                if (sleepTicks > 300) {
                    stopSleeping();
                }
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            default -> {
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private boolean shouldSleep() {
        return !ctx.player().isSleeping() && isNighttime();
    }

    private boolean isNighttime() {
        long time = ctx.world().getDayTime() % 24000L;
        return time > 12500L && time < 23500L;
    }

    private boolean isStillBed(BlockPos pos) {
        BlockState state = ctx.world().getBlockState(pos);
        return state.getBlock() instanceof BedBlock;
    }

    private BlockPos findNearbyBed() {
        int radius = 16;
        BlockPos center = ctx.playerFeet();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = ctx.world().getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void stopSleeping() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        state = State.IDLE;
        bedPos = null;
        sleepTicks = 0;
    }

    @Override
    public void onLostControl() {
        stopSleeping();
    }

    @Override
    public String displayName0() {
        return "Sleep";
    }

    @Override
    public double priority() {
        return 8.0;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    private enum State {
        IDLE, GOING_TO_BED, SLEEPING
    }
}
