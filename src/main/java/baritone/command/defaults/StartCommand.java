package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.process.SurvivalModeState;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class StartCommand extends Command {

    public StartCommand(IBaritone baritone) {
        super(baritone, "start");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        String mode = args.getString().toLowerCase(Locale.US);

        switch (mode) {
            case "beatsurvival", "beat", "survival" -> {
                SurvivalModeState.setMode(SurvivalModeState.Mode.BEAT_SURVIVAL);
                logDirect("Autonomous survival mode enabled");
            }
            case "resources", "grind", "op" -> {
                SurvivalModeState.setMode(SurvivalModeState.Mode.RESOURCES);
                logDirect("Resource grinding mode enabled");
            }
            default -> throw new CommandException("Unknown mode. Use BeatSurvival or resources") {};
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return new TabCompleteHelper()
                .append("BeatSurvival", "resources")
                .filterPrefix(args.getString())
                .stream();
    }

    @Override
    public String getShortDesc() {
        return "Start autonomous survival modes";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Starts the autonomous survival planner.",
                "",
                "Usage:",
                "> start BeatSurvival - Attempt full survival progression",
                "> start resources - Focus on grinding strong gear/resources"
        );
    }
}
