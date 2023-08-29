package stellar.corebot;

import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.net.Host;
import mindustry.net.NetworkIO;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import stellar.database.DatabaseAsync;
import stellar.database.gen.Tables;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

        commandListener.register("help", "Справка по командам", interaction -> {
            interaction.deferReply().submit().thenCombineAsync(jda.retrieveCommands().submit(), (hook, commands) -> {
                OptionMapping cmd = interaction.getOption("command");
                if (cmd == null) {
                    StringBuilder builder = new StringBuilder();
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Справка по командам")
                            .setColor(Colors.yellow);
                    commands.forEach(command -> {
                        List<Command.Option> options = command.getOptions();
                        builder.append("</").append(command.getName()).append(":").append(command.getId()).append(">")
                                .append(" - ")
                                .append(command.getDescription())
                                .append("\n");
                        for (Command.Option data : options) {
                            builder.append("└ ")
                                    .append("`").append(data.getName()).append("`")
                                    .append(": ").append(data.getDescription())
                                    .append("\n");
                        }
                        builder.append("\n");
                    });
                    embedBuilder.setDescription(builder);
                    return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                } else {
                    Optional<Command> command = commands.stream()
                            .filter(c -> c.getName().equals(cmd.getAsString()))
                            .findFirst();

                    if (command.isEmpty()) {
                        EmbedBuilder embedBuilder = new EmbedBuilder()
                                .setTitle("Команда " + cmd.getAsString() + " не найдена")
                                .setColor(Colors.red);
                        return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                    }

                    StringBuilder builder = new StringBuilder();
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Справка по команде " + cmd.getAsString())
                            .setColor(Colors.yellow);

                    List<Command.Option> options = command.get().getOptions();
                    builder.append("</").append(command.get().getName()).append(":").append(command.get().getId()).append(">")
                            .append(" - ")
                            .append(command.get().getDescription())
                            .append("\n");
                    for (Command.Option data : options) {
                        builder.append("└ ")
                                .append("`").append(data.getName()).append("`")
                                .append(": ")
                                .append(data.getDescription())
                                .append("\n");
                    }

                    embedBuilder.setDescription(builder);
                    return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                }
            });
        }, new OptionData(OptionType.STRING, "command", "Команда"));

        commandListener.register("status", "Получить статус указанного или всех серверов", interaction -> {
            interaction.deferReply().submit().thenComposeAsync(hook -> {
                OptionMapping server = interaction.getOption("server");
                if (server == null) {
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Статус серверов")
                            .setColor(Colors.blue)
                            .setTimestamp(Instant.now());

                    Const.servers.forEach((name, address) -> {
                        String[] split = address.split(":");
                        String title = String.format("**%s** | *%s*", name, address);
                        String text;

                        try {
                            Host host = pingHost(split[0], Integer.parseInt(split[1]));
                            text = "**Онлайн 🟢**\n" +
                                    "Карта: **" + host.mapname + "**\n" +
                                    "Игроков: **" + host.players + "**\n";
                        } catch (IOException e) {
                            text = "Оффлайн 🔴\n";
                        }
                        embedBuilder.addField(title, text, false);
                    });

                    return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                } else {
                    return hook.sendMessage(String.format("Server chosen: **%s**. Not implemented", server.getAsString())).submit();
                }
            });
        }, new OptionData(OptionType.STRING, "server", "Сервер").addChoices(
               Const.servers.keySet()
                        .stream()
                        .map(s -> new Command.Choice(convertToTitleCase(s), s))
                        .collect(Collectors.toList())
        ));

        commandListener.update();
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

    public static String convertToTitleCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-') {
                result.append(' '); // Add space
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    public static Host pingHost(String address, int port) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        long time = Time.millis();
        socket.send(new DatagramPacket(new byte[] {-2, 1}, 2, InetAddress.getByName(address), port));
        socket.setSoTimeout(2000);

        DatagramPacket packet = new DatagramPacket(new byte[512], 512);
        socket.receive(packet);
        socket.close();

        ByteBuffer buffer = ByteBuffer.wrap(packet.getData());

        return NetworkIO.readServerData((int) Time.timeSinceMillis(time), packet.getAddress().getHostAddress(), buffer);
    }
}
