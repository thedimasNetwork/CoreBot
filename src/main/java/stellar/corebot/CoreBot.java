package stellar.corebot;

import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.time.LocalDateTime;
import java.util.Objects;

import static stellar.corebot.Variables.jda;
import static stellar.corebot.Variables.commandListener;

public class CoreBot {
    public static void main(String[] args) {
        Log.formatter = (text, useColors, arg) -> Log.addColors(Strings.format(text.replace("@", "&fb&lb@&fr"), arg));
        Log.logger = (level1, text) -> {
            if(level1 == Log.LogLevel.err) text = text.replace(ColorCodes.  reset, ColorCodes.lightRed + ColorCodes.bold);
            String result = ColorCodes.bold + ColorCodes.lightBlack + "[" + Const.dateTime.format(LocalDateTime.now()) + "] " + ColorCodes.reset + Log.format(Const.tags[level1.ordinal()] + " " + text + "&fr");
            System.out.println(result);
        };

        Config.load();
        jda = JDABuilder.createDefault(Variables.config.getBotToken())
                .addEventListeners(new Listener())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build();
        commandListener.register("test", "Test command", interaction -> {
            Log.info("test @ by @", interaction.getOption("test").getAsString(), interaction.getUser().getEffectiveName());
            interaction.reply("Hello").queue();
        }, new OptionData(OptionType.STRING, "test", "Test Parameter", true));
    }
}
