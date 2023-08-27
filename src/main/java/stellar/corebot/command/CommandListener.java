package stellar.corebot.command;

import arc.util.Log;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import stellar.corebot.Variables;

import java.util.LinkedHashMap;
import java.util.Map;

import static stellar.corebot.Variables.jda;

/**
 * Created to be used mostly with Discord slash commands
 */
public class CommandListener {
    private Map<String, Command> commands = new LinkedHashMap<>();

    public Command register(SlashCommandData data, CommandRunner runner) {
        jda.updateCommands().addCommands(data).queue();
        commands.put(data.getName(), new Command(data, runner));
        return commands.get(data.getName());
    }

    public Command register(String name, String description, CommandRunner runner, OptionData... options) {
        SlashCommandData data = Commands.slash(name, description).addOptions(options);
        jda.updateCommands().addCommands(data).queue();
        commands.put(name, new Command(data, runner));
        return commands.get(name);
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

    public enum ResponseType {
        noCommand, valid
    }
}
