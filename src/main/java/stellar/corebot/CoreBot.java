package stellar.corebot;

import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.game.Schematic;
import mindustry.net.Host;
import mindustry.net.NetworkIO;
import mindustry.type.ItemStack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import stellar.database.Database;
import stellar.database.DatabaseAsync;
import stellar.database.gen.Tables;
import stellar.database.gen.tables.records.BansRecord;
import stellar.database.gen.tables.records.UsersRecord;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
        ContentHandler.load();

        jda = JDABuilder.createDefault(Variables.config.getBotToken())
                .addEventListeners(new Listener())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build();

        // region commands
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
                            .setColor(Colors.blue)
                            .setTimestamp(Instant.now());

                    for (int i = 0; i < fetch.length; i++) {
                        builder.append(i).append(". ")
                                .append(Strings.stripColors(fetch[i].value2()))
                                .append(" - ")
                                .append(longToTime((long) fetch[i].value3())).append("\n");
                    }

                    embedBuilder.setDescription(builder);
                    return interaction.getHook().sendMessageEmbeds(embedBuilder.build()).submit();
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
                });
            }
            return future;
        }, new OptionData(OptionType.INTEGER, "id", "Айди игрока"));

        commandListener.register("stats", "Статистика игры для указанного игрока", interaction -> {
            return interaction.deferReply().submit().thenComposeAsync(ignored ->
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
                    EmbedBuilder gameStats = new EmbedBuilder(),
                            hexStats = new EmbedBuilder();
                    gameStats.setTitle("Статистика игры " + response[0])
                            .setDescription(response[1])
                            .setColor(Colors.blue)
                            .setTimestamp(Instant.now());

                    hexStats.setTitle("Статистика хексов")
                            .setDescription(response[2])
                            .setColor(Colors.blue)
                            .setTimestamp(Instant.now());

                    return interaction.getHook().sendMessageEmbeds(gameStats.build(), hexStats.build()).submit();
                }

            });
        }, new OptionData(OptionType.INTEGER, "id", "Айди игрока", true));

        commandListener.register("help", "Справка по командам", interaction -> {
            return interaction.deferReply().submit().thenCombineAsync(jda.retrieveCommands().submit(), (hook, commands) -> {
                OptionMapping cmd = interaction.getOption("command");
                if (cmd == null) {
                    StringBuilder builder = new StringBuilder();
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Справка по командам")
                            .setColor(Colors.yellow);
                    commands.forEach(command -> {
                        List<Command.Option> options = command.getOptions();
                        builder.append(String.format("</%s:%s> - %s\n", command.getName(), command.getId(), command.getDescription()));
                        for (Command.Option data : options) {
                            builder.append(String.format("└ `%s`: %s\n", data.getName(), data.getDescription()));
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
            return interaction.deferReply().submit().thenComposeAsync(hook -> {
                OptionMapping server = interaction.getOption("server");
                if (server == null) {
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Статус серверов")
                            .setColor(Colors.blue)
                            .setTimestamp(Instant.now());

                    Const.servers.forEach((name, address) -> {
                        embedBuilder.addField(serverStatus(name, address));
                    });

                    return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                } else {
                    String name = convertToTitleCase(server.getAsString());
                    String address = Const.servers.get(server.getAsString());
                    if (address == null) {
                        EmbedBuilder embedBuilder = new EmbedBuilder()
                                .setTitle("Сервер " + name + " не найден")
                                .setColor(Colors.red);
                        return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                    }

                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Статус сервера " + name)
                            .setColor(Colors.blue)
                            .setTimestamp(Instant.now());

                    embedBuilder.addField(serverStatus(name, address, true));
                    return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                }
            });
        }, new OptionData(OptionType.STRING, "server", "Сервер").addChoices(
               Const.getServers()
                       .stream()
                       .map(s -> new Command.Choice(convertToTitleCase(s), s))
                       .collect(Collectors.toList())
        ));

        commandListener.register("map", "Отправить карту", interaction -> {
            return interaction.deferReply().submit().thenComposeAsync(hook -> {
                try {
                    Message.Attachment attachment = interaction.getOption("map").getAsAttachment();
                    ContentHandler.Map map = ContentHandler.readMap(ContentHandler.download(attachment.getUrl()));
                    ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
                    ImageIO.write(map.image, "png", previewStream);

                    User author = interaction.getUser();
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setColor(Colors.purple)
                            .setImage("attachment://preview.png")
                            .setAuthor(author.getName(), author.getAvatarUrl(), author.getAvatarUrl())
                            .setTitle(map.name == null ? attachment.getFileName().replace(".msav", "") : map.name)
                            .setFooter(map.description)
                            .setTimestamp(Instant.now());

                    return jda.getTextChannelById(config.getMapsChannel())
                            .sendMessageEmbeds(embedBuilder.build())
                            .addFiles(FileUpload.fromData(ContentHandler.download(attachment.getUrl()), attachment.getFileName()))
                            .addFiles(FileUpload.fromData(previewStream.toByteArray(), "preview.png"))
                            .submit()
                            .thenComposeAsync(message -> {
                                message.addReaction(Emoji.fromUnicode("👍")).queue();
                                message.addReaction(Emoji.fromUnicode("👎")).queue();
                                EmbedBuilder successBuilder = new EmbedBuilder()
                                        .setTitle("Карта отправлена")
                                        .setColor(Colors.green);
                                return hook.sendMessageEmbeds(successBuilder.build()).submit();
                            });
                } catch (Throwable t) {
                    Log.err(t);
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Не удалось отправить/обработать карту")
                            .setDescription(t.toString())
                            .setColor(Colors.red);

                    return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                }
            });
        }, new OptionData(OptionType.ATTACHMENT, "map", "Карта", true));

        commandListener.register("schem", "Отправить схему", interaction -> {
            return interaction.deferReply().submit().thenComposeAsync(hook -> {
                try {
                    Message.Attachment attachment = interaction.getOption("schem").getAsAttachment();
                    Schematic schematic = ContentHandler.parseSchematicURL(attachment.getUrl());
                    BufferedImage previewSchematic = ContentHandler.previewSchematic(schematic);
                    ByteArrayOutputStream previewStream = new ByteArrayOutputStream();
                    ImageIO.write(previewSchematic, "png", previewStream);

                    User author = interaction.getUser();
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setColor(Colors.purple)
                            .setImage("attachment://preview.png")
                            .setAuthor(author.getName(), author.getAvatarUrl(), author.getAvatarUrl())
                            .setTitle(schematic.name() == null ? attachment.getFileName().replace(".msch", "") : schematic.name())
                            .setFooter(schematic.description())
                            .setTimestamp(Instant.now());

                    StringBuilder cost = new StringBuilder();
                    for (ItemStack stack : schematic.requirements()) {
                        List<RichCustomEmoji> emotes = interaction.getGuild().getEmojisByName(stack.item.name.replace("-", ""), true);
                        RichCustomEmoji result = emotes.isEmpty() ? interaction.getGuild().getEmojisByName("ohno", true).get(0) : emotes.get(0);
                        cost.append(result.getAsMention()).append(stack.amount).append("  ");
                    }
                    embedBuilder.addField("Цена", cost.toString(), false);
                    embedBuilder.addField("Потребление энергии", Integer.toString((int) schematic.powerConsumption()), false);
                    embedBuilder.addField("Производство энергии", Integer.toString((int) schematic.powerProduction()), false);


                    return jda.getTextChannelById(config.getSchematicsChannel())
                            .sendMessageEmbeds(embedBuilder.build())
                            .addFiles(FileUpload.fromData(ContentHandler.download(attachment.getUrl()), attachment.getFileName()))
                            .addFiles(FileUpload.fromData(previewStream.toByteArray(), "preview.png"))
                            .submit()
                            .thenComposeAsync(message -> {
                                message.addReaction(Emoji.fromUnicode("👍")).queue();
                                message.addReaction(Emoji.fromUnicode("👎")).queue();
                                EmbedBuilder successBuilder = new EmbedBuilder()
                                        .setTitle("Схема отправлена")
                                        .setColor(Colors.green);
                                return hook.sendMessageEmbeds(successBuilder.build()).submit();
                            });
                } catch (Throwable t) {
                    Log.err(t);
                    EmbedBuilder embedBuilder = new EmbedBuilder()
                            .setTitle("Не удалось отправить/обработать схему")
                            .setDescription(t.toString())
                            .setColor(Colors.red);

                    return hook.sendMessageEmbeds(embedBuilder.build()).submit();
                }
            });
        }, new OptionData(OptionType.ATTACHMENT, "schem", "Схема", true));

        commandListener.register("suggest", "Отправить предложение", interaction -> {
            return interaction.deferReply().submit().thenComposeAsync(hook -> {
                String suggestion = interaction.getOption("suggestion").getAsString();
                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setDescription(suggestion)
                        .setColor(Colors.purple)
                        .setAuthor(interaction.getUser().getName(), interaction.getUser().getAvatarUrl(), interaction.getUser().getAvatarUrl());

                jda.getTextChannelById(config.getSuggestionsChannel())
                        .sendMessageEmbeds(embedBuilder.build())
                        .queue();

                return hook.sendMessageEmbeds(Util.embedBuilder("Предложение отправлено", Colors.green)).submit();
            });
        }, new OptionData(OptionType.STRING, "suggestion", "Предложение", true));

        commandListener.register("find", "Найти игрока", interaction -> {
            return interaction.deferReply(true).submit().thenComposeAsync(hook -> {
                if (!Util.isMindustryAdmin(interaction.getMember())) {
                    return hook.sendMessageEmbeds(Util.embedBuilder("В доступе отказано", Colors.red))
                            .submit();
                }

                String type = interaction.getOption("type").getAsString();
                String query = interaction.getOption("query").getAsString();

                List<UsersRecord> records = new ArrayList<>();
                int count = 0;
                switch (type) {
                    case "uuid" -> {
                        UsersRecord record = Database.getPlayer(query);
                        records = record != null ? List.of(record) : new ArrayList<UsersRecord>();
                        count = records.size();
                    }
                    case "name" -> {
                        StringBuilder builder = new StringBuilder();
                        for (int i = 0; i < query.length(); i++) {
                            builder.append("%").append(query.charAt(i));
                        }
                        builder.append("%");
                        records = List.of(Database.getContext()
                                .selectFrom(Tables.users)
                                .where(Tables.users.name.likeIgnoreCase(builder.toString()))
                                .limit(15)
                                .fetchArray());
                        Record1<Integer> record1 = Database.getContext()
                                .selectCount()
                                .from(Tables.users)
                                .where(Tables.users.name.likeIgnoreCase(builder.toString()))
                                .limit(15)
                                .fetchOne();
                        count = record1 == null ? 0 : record1.value1();
                    }
                    case "id" -> {
                        if (!Strings.canParseInt(query)) {
                            return hook.sendMessageEmbeds(Util.embedBuilder("Невалидный айди", Colors.red))
                                    .submit();
                        }
                        UsersRecord record = Database.getPlayer(Strings.parseInt(query));
                        records = record != null ? List.of(record) : new ArrayList<>();
                        count = records.size();
                    }
                    case "ip" -> {
                        records = List.of(Database.getContext()
                                .selectFrom(Tables.users)
                                .where(Tables.users.ip.contains(query))
                                .limit(15)
                                .fetchArray());
                        Record1<Integer> record1 = Database.getContext()
                                .selectCount()
                                .from(Tables.users)
                                .where(Tables.users.ip.contains(query))
                                .limit(15)
                                .fetchOne();
                        count = record1 == null ? 0 : record1.value1();
                    }
                }

                if (count == 0) {
                    MessageEmbed embed = Util.embedBuilder("Ничего не найдено", Colors.red);
                    return hook.sendMessageEmbeds(embed)
                            .submit();
                }

                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle(String.format("Найдено %s записей", count))
                        .setFooter(count > 15 ? "Показано только 15 записей. Задайте запрос конкретнее" : null) // TODO: pages
                        .setColor(Colors.blue);

                boolean admin = interaction.getMember().hasPermission(Permission.ADMINISTRATOR);
                records.forEach(record -> {
                    String banned = Util.fancyBool(Database.isBanned(record.getUuid()));
                    String uuid = admin ? record.getUuid() : Util.obfuscate(record.getUuid(), 5, false);
                    String ip = admin ? record.getIp() : Util.obfuscate(record.getIp(), true);
                    String status = Const.statusNames.get(record.getStatus());
                    String usedNames = admin ?  Util.fancyArray(Database.getNames(record.getUuid())) : "[*null*]";
                    String usedIps = admin ? Util.fancyArray(Database.getIps(record.getUuid())) : "[*null*]";

                    String message = String.format("""
                            UUID: `%s`
                            Имя: %s
                            Айди: %s
                            Последний айпи: `%s`
                            Статус: %s
                            Забанен: %s
                            Все имена: %s
                            Все айпи: %s
                            """, uuid, record.getName(), record.getId(), ip, status, banned, usedNames, usedIps);
                    embedBuilder.addField(Strings.stripColors("**" + record.getName()) + "**", message, false);
                });
                return hook.sendMessageEmbeds(embedBuilder.build())
                                .submit();
            });
        }, new OptionData(OptionType.STRING, "type", "Тип информации по которой искать", true).addChoices(
                new Command.Choice("Имя", "name"),
                new Command.Choice("ID", "id"),
                new Command.Choice("UUID", "uuid"),
                new Command.Choice("IP", "ip")
        ), new OptionData(OptionType.STRING, "query", "Запрос", true));

        commandListener.register("ban-trace", "Трассировака бана/-ов для указанного пользователя", interaction -> {
            return interaction.deferReply(true).submit().thenComposeAsync(hook -> {
                UsersRecord record = Database.getPlayer(interaction.getOption("id").getAsInt());
                var userIpsQuery = DSL.selectDistinct(Tables.logins.ip)
                        .from(Tables.logins)
                        .where(Tables.logins.uuid.eq(record.getUuid()));

                var bannedIpsQuery = DSL.selectDistinct(Tables.logins.ip, Tables.bans.asterisk())
                        .from(Tables.logins)
                        .join(Tables.users).on(Tables.logins.uuid.eq(Tables.users.uuid))
                        .join(Tables.bans).on(Tables.bans.target.eq(Tables.users.uuid));

                Integer[] banIds = Database.getContext()
                        .select(bannedIpsQuery.field("id"))
                        .from(Tables.users)
                        .join(userIpsQuery).on(Tables.users.uuid.eq(record.getUuid()))
                        .leftJoin(bannedIpsQuery).on(userIpsQuery.field("ip", String.class).eq(bannedIpsQuery.field("ip", String.class)))
                        .where(bannedIpsQuery.field("ip").isNotNull())
                        .orderBy(bannedIpsQuery.field("id").desc())
                        .fetchArray(0, Integer.class);

                BansRecord[] bans = Database.getContext()
                        .selectFrom(Tables.bans)
                        .where(Tables.bans.id.in(banIds))
                        .orderBy(Tables.bans.id.desc())
                        .fetchArray(); // I know that I could've used Database.getBans, but it returns only active bans

                if (bans.length == 0) {
                    return hook.sendMessageEmbeds(Util.embedBuilder("Игрок не забанен", Colors.yellow)).submit();
                }

                Set<String> uuids = Set.of(Arrays.stream(bans).map(BansRecord::getTarget).toArray(String[]::new));
                Map<String, UsersRecord> users = Database.getContext()
                        .selectFrom(Tables.users)
                        .where(Tables.users.uuid.in(uuids))
                        .fetch()
                        .intoMap(Tables.users.uuid);

                MessageCreateBuilder messageBuilder = new MessageCreateBuilder()
                        .setContent("Трассировка банов для **" + Strings.stripColors(record.getName()) + "** / `" + record.getId() + "`:");
                for (int i = 0; i < Math.min(bans.length, 10); i++) {
                    BansRecord ban = bans[i];
                    UsersRecord target = users.get(ban.getTarget());
                    List<String> userIps, bannedIps, commonIps = new ArrayList<>();
                    if (!ban.getTarget().equals(record.getUuid())) {
                        userIps = new ArrayList<>(List.of(Database.getIps(record.getUuid())));
                        bannedIps = List.of(Database.getIps(ban.getTarget()));
                        commonIps = userIps;
                        commonIps.retainAll(bannedIps);
                    }

                    String title = (ban.getTarget().equals(record.getUuid()) ? "Оригинальный бан" : "Рекурсивный бан") + " #" + ban.getId();
                    StringBuilder content = new StringBuilder(String.format("""
                                    **Админ**: %s
                                    **Нарушитель**: %s
                                    **Причина**: %s
                                    **Дата**: %s
                                    **Срок**: %s
                                    **Активен**: %s
                                    """,
                            ban.getAdmin(),
                            String.format("%s (%d)", Strings.stripColors(target.getName()), target.getId()),
                            ban.getReason(),
                            String.format("<t:%d:f>", ban.getCreated().toEpochSecond()),
                            ban.getUntil() != null ? String.format("<t:%d:f>", ban.getUntil().toEpochSecond()) : "Перманентный",
                            Util.fancyBool(ban.isActive())));

                    if (!commonIps.isEmpty()) content.append("\n").append("**Совпадения IP**:");
                    for (String ip : commonIps) {
                        content.append("\n").append(String.format("* **%s**: B-%d/T-%d", ip, Util.ipUsed(ban.getTarget(), ip), Util.ipUsed(record.getUuid(), ip)));
                    }

                    messageBuilder.addEmbeds(Util.embedBuilder(title, content.toString().strip(), ban.getTarget().equals(record.getUuid()) ? Colors.red : Colors.blue));
                }
                return hook.sendMessage(messageBuilder.build()).submit();
            }).exceptionally(e -> {
                Log.err(e);
                return null;
            });
        }, new OptionData(OptionType.INTEGER, "id", "ID игрока", true));

        commandListener.update();
        // endregion

        // region scheduled tasks
        scheduler.scheduleAtFixedRate(() -> {
            EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setTitle("Статус серверов")
                    .setDescription("Обновляется каждые " + config.getStatusUpdatePeriod() + " секунд")
                    .setColor(Colors.purple);

            Const.servers.forEach((name, address) -> {
                embedBuilder.addField(serverStatus(name, address));
            });

            embedBuilder.setTimestamp(Instant.now());
            jda.getTextChannelById(config.getStatusChannel())
                    .editMessageEmbedsById(config.getStatusMessage(), embedBuilder.build())
                    .queue();
        }, 0, config.getStatusUpdatePeriod(), TimeUnit.SECONDS);
        // endregion
    }

    public static MessageEmbed.Field serverStatus(String name, String address) {
        return serverStatus(name, address, false);
    }

    public static MessageEmbed.Field serverStatus(String name, String address, boolean description) {
        String[] split = address.split(":");
        String title = String.format("**%s** | *%s*", name, address);
        String text;

        try {
            Host host = pingHost(split[0], Integer.parseInt(split[1]));
            text = "**Онлайн 🟢**\n" +
                    "Карта: **" + Strings.stripColors(host.mapname).trim() + "**\n" +
                    "Игроков: **" + host.players + "**\n" +
                    (description ? ("Описание: **" + Strings.stripColors(host.description)) + "**": "");
        } catch (IOException e) {
            text = "Оффлайн 🔴\n";
        }
        return new MessageEmbed.Field(title, text, false);
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
