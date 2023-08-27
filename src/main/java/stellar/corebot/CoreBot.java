package stellar.corebot;

import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import stellar.database.DatabaseAsync;
import stellar.database.gen.Tables;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static stellar.corebot.Variables.*;

public class CoreBot {
    public static void main(String[] args) {
        Log.formatter = (text, useColors, arg) -> Log.addColors(Strings.format(text.replace("@", "&fb&lb@&fr"), arg));
        Log.logger = (level1, text) -> {
            if(level1 == Log.LogLevel.err) text = text.replace(ColorCodes.  reset, ColorCodes.lightRed + ColorCodes.bold);
            String result = ColorCodes.bold + ColorCodes.lightBlack + "[" + Const.dateFormatter.format(LocalDateTime.now()) + "] " + ColorCodes.reset + Log.format(Const.tags[level1.ordinal()] + " " + text + "&fr");
            System.out.println(result);
        };

        Config.load();
        DatabaseAsync.load(config.getDatabase().getIp(), config.getDatabase().getPort(), config.getDatabase().getName(), config.getDatabase().getUser(), config.getDatabase().getPassword());

        jda = JDABuilder.createDefault(Variables.config.getBotToken())
                .addEventListeners(new Listener())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build();

        commandListener.register("test", "Test command", interaction -> {
            Log.info("test @ by @", Objects.requireNonNull(interaction.getOption("test")).getAsString(), interaction.getUser().getEffectiveName());
            interaction.reply("Hello").queue();
        }, new OptionData(OptionType.STRING, "test", "Test Parameter", true));

        commandListener.register("playtime", "Playtime of top 10 players or the one with the specified ID", interaction -> {
            CompletableFuture<?> future = interaction.deferReply().submit();
            OptionMapping id = interaction.getOption("id");
            if (id == null) {
                future.thenComposeAsync(ignored ->
                        DatabaseAsync.getContextAsync()
                ).thenComposeAsync(context -> {
                    StringBuilder builder = new StringBuilder();

                    Field<?> sumExpression = Arrays.stream(Tables.playtime.fields())
                            .filter(field -> field.getType() == Long.class)
                            .reduce(DSL.val(0L), Field::add);

                    Record3<Integer, String, ?>[] fetch = context.select(Tables.users.id, Tables.users.name, sumExpression.as("total"))
                            .from(Tables.playtime)
                            .join(Tables.users)
                            .on(Tables.playtime.uuid.eq(Tables.users.uuid))
                            .orderBy(DSL.field("total").desc())
                            .limit(10)
                            .fetchArray();

                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Top playtime")
                            .setColor(Colors.blue);

                    for (int i = 0; i < fetch.length; i++) {
                        builder.append(i).append(". ")
                                .append(Strings.stripColors(fetch[i].value2()))
                                .append(" - ")
                                .append(longToTime((long) fetch[i].value3())).append("\n");
                    }

                    embedBuilder.setDescription(builder);
                    return interaction.getHook().sendMessageEmbeds(embedBuilder.build()).submit();
                }).exceptionally(throwable -> {
                    Log.err(throwable);
                    return null;
                });
            } else {
                // TODO: implement
            }

        }, new OptionData(OptionType.INTEGER, "id", "The ID of the player"));
    }

    public static String longToTime(long seconds) {
        if (seconds < 60 * 60) { // Less than an hour
            return MessageFormat.format(Const.minuteFormat, seconds / 60);
        } else if (seconds < 60 * 60 * 24) { // Less than a day
            return MessageFormat.format(Const.hourFormat, seconds / (60 * 60), (seconds % (60 * 60)) / 60);
        } else { // More or equal to a day
            return MessageFormat.format(Const.dayFormat, seconds / (60 * 60 * 24), (seconds % (60 * 60 * 24)) / (60 * 60), (seconds % (60 * 60)) / 60);
        }
    }
}
