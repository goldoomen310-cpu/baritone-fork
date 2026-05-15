package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.schematic.ISchematic;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class StarterBaseProcess extends BaritoneProcessHelper {

    private enum State {
        IDLE,
        BUILDING_SHELTER,
        BUILDING_FARM,
        COMPLETE
    }

    private State state = State.IDLE;
    private BlockPos baseOrigin;

    public StarterBaseProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        return ctx.player() != null && ctx.world() != null && state != State.COMPLETE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (baseOrigin == null) {
            baseOrigin = ctx.playerFeet().offset(4, 0, 4);
        }

        if (state == State.IDLE) {
            state = State.BUILDING_SHELTER;
            baritone.getBuilderProcess().build("starter shelter", new ShelterSchematic(), new Vec3i(baseOrigin.getX(), baseOrigin.getY(), baseOrigin.getZ()));
            return new PathingCommand(new GoalBlock(baseOrigin), PathingCommandType.SET_GOAL_AND_PATH);
        }

        if (state == State.BUILDING_SHELTER) {
            if (baritone.getBuilderProcess().isActive()) {
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            state = State.BUILDING_FARM;
            baritone.getBuilderProcess().build("starter farm", new FarmSchematic(), new Vec3i(baseOrigin.getX() + 10, baseOrigin.getY(), baseOrigin.getZ()));
            return new PathingCommand(new GoalBlock(baseOrigin.offset(10, 0, 0)), PathingCommandType.SET_GOAL_AND_PATH);
        }

        if (state == State.BUILDING_FARM) {
            if (baritone.getBuilderProcess().isActive()) {
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            state = State.COMPLETE;
            logNotification("Starter base completed", false);
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    @Override
    public void onLostControl() {
    }

    @Override
    public String displayName0() {
        return switch (state) {
            case BUILDING_SHELTER -> "Building Shelter";
            case BUILDING_FARM -> "Building Farm";
            case COMPLETE -> "Base Complete";
            default -> "Starter Base";
        };
    }

    @Override
    public double priority() {
        return 6.5;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    private static final class ShelterSchematic implements ISchematic {
        @Override
        public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
            if (y == 0) {
                return Blocks.COBBLESTONE.defaultBlockState();
            }
            if (y >= 1 && y <= 2) {
                boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                boolean doorway = x == 2 && z == 0 && y <= 2;
                if (wall && !doorway) {
                    return Blocks.OAK_PLANKS.defaultBlockState();
                }
                if (doorway && y == 1) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
            if (y == 3) {
                return Blocks.OAK_PLANKS.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public int widthX() {
            return 5;
        }

        @Override
        public int heightY() {
            return 4;
        }

        @Override
        public int lengthZ() {
            return 5;
        }
    }

    private static final class FarmSchematic implements ISchematic {
        @Override
        public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
            if (y == 0) {
                if (x == 2 && z == 2) {
                    return Blocks.WATER.defaultBlockState();
                }
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    return Blocks.OAK_FENCE.defaultBlockState();
                }
                return Blocks.DIRT.defaultBlockState();
            }
            if (y == 1 && (x == 0 || x == 4 || z == 0 || z == 4)) {
                return Blocks.OAK_FENCE.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public int widthX() {
            return 5;
        }

        @Override
        public int heightY() {
            return 2;
        }

        @Override
        public int lengthZ() {
            return 5;
        }
    }
}
