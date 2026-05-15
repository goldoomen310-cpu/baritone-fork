package baritone.process;

public final class SurvivalModeState {

    private SurvivalModeState() {}

    public enum Mode {
        BEAT_SURVIVAL,
        RESOURCES
    }

    private static volatile Mode mode = Mode.BEAT_SURVIVAL;

    public static Mode getMode() {
        return mode;
    }

    public static void setMode(Mode newMode) {
        mode = newMode == null ? Mode.BEAT_SURVIVAL : newMode;
    }
}
