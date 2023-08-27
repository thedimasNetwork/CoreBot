package stellar.corebot.command;

import arc.util.Log;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.*;

import static stellar.corebot.Variables.jda;

/**
 * Created to be used mostly with Discord slash commands
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class CommandListener {
    private final HashMap<String, Command> commands = new LinkedHashMap<>();
    private final Set<SlashCommandData> queuedCommands = new HashSet<>();

    public Command register(SlashCommandData data, CommandRunner runner) {
        queuedCommands.add(data);
        commands.put(data.getName(), new Command(data, runner));
        return commands.get(data.getName());
    }

    public Command register(String name, String description, CommandRunner runner, OptionData... options) {
        SlashCommandData data = Commands.slash(name, description).addOptions(options);
        queuedCommands.add(data);
        commands.put(name, new Command(data, runner));
        return commands.get(name);
    }

    /**
     * Sends all commands to the Discord server
     */
    public void update() {
        jda.updateCommands().addCommands(queuedCommands).submit().thenRun(queuedCommands::clear);
    }

    public ResponseType handle(SlashCommandInteraction interaction) {
        Log.debug(interaction);
        Command command = commands.get(interaction.getName());
        if (command == null) {
            return ResponseType.noCommand;
        }

        command.run(interaction);
        return ResponseType.valid;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Command> getCommands() {
        return (Map<String, Command>) commands.clone();
    }

    public enum ResponseType {
        noCommand, valid
    }
}
