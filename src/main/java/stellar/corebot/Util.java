package stellar.corebot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import stellar.database.Database;
import stellar.database.gen.Tables;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Util {
    public static MessageEmbed embedBuilder(String text, Color color) {
        return new EmbedBuilder()
                .setDescription(text)
                .setColor(color)
                .build();
    }

    public static MessageEmbed embedBuilder(String title, String description, Color color) {
        return new EmbedBuilder()
                .addField(title, description, false)
                .setColor(color)
                .build();
    }

    public static MessageEmbed embedBuilder(String title, String description, Color color, LocalDateTime time) {
        return new EmbedBuilder()
                .addField(title, description, false)
                .setColor(color)
                .setTimestamp(time.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC")))
                .build();
    }

    public static MessageEmbed embedBuilder(String title, String description, Color color, LocalDateTime time, String footer) {
        return new EmbedBuilder()
                .addField(title, description, false)
                .setColor(color)
                .setTimestamp(time.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC")))
                .setFooter(footer)
                .build();
    }

    public static boolean isMindustryAdmin(Member member) { // TODO: more advanced checking
        return member.hasPermission(Permission.ADMINISTRATOR);
    }

    public static String fancyBool(boolean bool) {
        return bool ? "✔" : "✘";
    }

    public static <T> String fancyArray(T[] array) {
        return array.length > 0 ? ("[`" + Arrays.stream(array)
                .map(T::toString)
                .collect(Collectors.joining("`, `")) + "`]") : "[]";
    }

    public static String obfuscate(String string, boolean escape) {
        return obfuscate(string, 2, escape);
    }

    public static String obfuscate(String string, int chars, boolean escape) {
        return string.substring(0, chars) + (escape ? "\\*" : "*").repeat(string.length() - chars * 2) + string.substring(string.length() - chars);
    }

    public static int ipUsed(String uuid, String ip) {
        return Database.getContext()
                .selectCount()
                .from(Tables.logins)
                .where(Tables.logins.uuid.eq(uuid))
                .and(Tables.logins.ip.eq(ip))
                .fetchOne()
                .value1();
    }

    public static boolean isBase64(String string) {
        return string.matches("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$") && string.length() % 4 == 0;
    }
}