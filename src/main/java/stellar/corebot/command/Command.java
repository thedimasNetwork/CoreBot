package stellar.corebot.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

@Getter
@AllArgsConstructor
public class Command {
    private final SlashCommandData data;
    private final CommandRunner runner;

    public void run(SlashCommandInteraction interaction) {
        runner.accept(interaction);
    }
}
