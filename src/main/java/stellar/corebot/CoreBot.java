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

        commandListener.register("playtime", "Время игры указанного или 10 лучших игроков", interaction -> {
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
                            .setTitle("Топ времени игры")
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
                future.thenComposeAsync(ignored ->
                        DatabaseAsync.getPlayerAsync(id.getAsInt())
                ).thenComposeAsync(player -> {
                    if (player == null) {
                        return CompletableFuture.supplyAsync(() -> new String[] {});
                    } else {
                        return DatabaseAsync.getContextAsync().thenApplyAsync(context ->
                                context.selectFrom(Tables.playtime)
                                        .where(Tables.playtime.uuid.eq(player.getUuid()))
                                        .fetchOne()
                        ).thenApplyAsync(playtime -> {
                            StringBuilder builder = new StringBuilder();
                            Arrays.stream(Tables.playtime.fields())
                                    .filter(field -> field.getType() == Long.class)
                                    .forEach(field -> builder.append(field.getName())
                                            .append(": ")
                                            .append(longToTime((long) field.get(playtime)))
                                            .append("\n")
                                    );
                            return new String[] {Strings.stripColors(player.getName()), builder.toString()};
                        });
                    }
                }).thenComposeAsync(response -> {
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    if (response.length == 0) {
                        embedBuilder.setTitle("Игрок не найден")
                                .setColor(Colors.red);
                    } else {
                        embedBuilder.setTitle("Время игры для " + response[0])
                                .setDescription(response[1])
                                .setColor(Colors.blue);
                    }

                    return interaction.getHook().sendMessageEmbeds(embedBuilder.build()).submit();
                }).exceptionally(throwable -> {
                    Log.err(throwable);
                    return null;
                });
            }

        }, new OptionData(OptionType.INTEGER, "id", "Айди игрока"));

        commandListener.register("stats", "Статистика игры для указанного игрока", interaction -> {
            interaction.deferReply().submit().thenComposeAsync(ignored ->
                    DatabaseAsync.getPlayerAsync(Objects.requireNonNull(interaction.getOption("id")).getAsInt())
            ).thenComposeAsync(player -> {
                if (player == null) {
                    return CompletableFuture.supplyAsync(() -> new String[] {});
                } else {
                    return DatabaseAsync.getStatsAsync(
                            player.getUuid()
                    ).thenApplyAsync(stats -> {
                        String statsMessage = MessageFormat.format(Const.statsFormat,
                                stats.getBuilt(), stats.getBroken(),
                                stats.getAttacks(), stats.getSurvivals(), stats.getWaves(),
                                stats.getLogins(), stats.getMessages(), stats.getDeaths());
                        String hexesMessage = MessageFormat.format(Const.hexStatsFormat,
                                stats.getHexesCaptured(), stats.getHexesLost(), stats.getHexesDestroyed(),
                                stats.getHexWins(), stats.getHexLosses());
                        return new String[] {Strings.stripColors(player.getName()), statsMessage, hexesMessage};
                    });
                }
            }).thenComposeAsync(response -> {
                if (response.length == 0) {
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("Игрок не найден")
                            .setColor(Colors.red);
                    return interaction.getHook().sendMessageEmbeds(embedBuilder.build()).submit();
                } else {
                    EmbedBuilder gameStats = new EmbedBuilder(), hexStats = new EmbedBuilder();
                    gameStats.setTitle("Статистика игры " + response[0])
                            .setDescription(response[1])
                            .setColor(Colors.blue);
                    hexStats.setTitle("Статистика хексов")
                            .setDescription(response[2])
                            .setColor(Colors.blue);
                    return interaction.getHook().sendMessageEmbeds(gameStats.build(), hexStats.build()).submit();
                }

            }).exceptionally(throwable -> {
                Log.err(throwable);
                return null;
            });

        }, new OptionData(OptionType.INTEGER, "id", "Айди игрока", true));
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
