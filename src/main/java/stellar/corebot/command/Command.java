package stellar.corebot.command;

import arc.util.Log;
import arc.util.Strings;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import stellar.corebot.Colors;
import stellar.corebot.CoreBot;
import stellar.corebot.Util;
import stellar.corebot.Variables;

import static stellar.corebot.Variables.config;
import static stellar.corebot.Variables.jda;

@Getter
@AllArgsConstructor
public class Command {
    private final SlashCommandData data;
    private final CommandRunner runner;

    public void run(SlashCommandInteraction interaction) {
        runner.apply(interaction)
                .exceptionally(th -> {
                    Log.err("Unable to run command @", interaction.getCommandString());
                    Log.err(th);
                    interaction.replyEmbeds(Util.embedBuilder("Произошла неизвестная ошибка", Colors.red)).submit();
                    jda.getTextChannelById(config.getLogChannel()).sendMessageEmbeds(Util.embedBuilder("Error has just occurred", String.format("""
                                    ### Message
                                    Unable to run command </%s:%s>
                                    ### Traceback
                                    ```java
                                    %s
                                    ```
                                    """, interaction.getCommandString(), interaction.getCommandId(), Strings.getStackTrace(th)),
                            Colors.red)).submit();
                });
    }
}
