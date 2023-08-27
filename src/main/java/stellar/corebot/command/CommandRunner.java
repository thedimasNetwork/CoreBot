package stellar.corebot.command;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

import java.util.function.Consumer;

public interface CommandRunner extends Consumer<SlashCommandInteraction> {
    @Override
    void accept(SlashCommandInteraction interaction);
}
