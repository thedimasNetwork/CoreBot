package stellar.corebot.command;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public interface CommandRunner extends Function<SlashCommandInteraction, CompletableFuture<?>> {
    @Override
    CompletableFuture<?> apply(SlashCommandInteraction interaction);
}
