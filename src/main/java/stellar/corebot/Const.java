package stellar.corebot;

import stellar.database.enums.PlayerStatus;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class Const {
    public static final String homeFolder = "bot/";

    public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};

    public static final HashMap<String, String> servers = new LinkedHashMap<>() {{
            put("hub", "play.thedimas.pp.ua:6567");
            put("survival", "play.thedimas.pp.ua:6501");
            put("attack", "play.thedimas.pp.ua:6502");
            put("sandbox", "play.thedimas.pp.ua:6503");
            put("pvp", "play.thedimas.pp.ua:6504");
            put("erekir_hexed", "play.thedimas.pp.ua:6505");
            put("anarchy", "play.thedimas.pp.ua:6506");
            put("campaign_maps", "play.thedimas.pp.ua:6507");
            put("ms_go", "play.thedimas.pp.ua:6508");
            put("hex_pvp", "play.thedimas.pp.ua:6509");
            put("castle_wars", "play.thedimas.pp.ua:6510");
            put("crawler_arena", "play.thedimas.pp.ua:6511");
            put("zone_capture", "play.thedimas.pp.ua:6512");
    }};

    public static final HashMap<PlayerStatus, String> statusNames = new LinkedHashMap<>() {{
            put(PlayerStatus.basic, "Игрок :bust_in_silhouette:");
            put(PlayerStatus.admin, "Админ :hammer:");
            put(PlayerStatus.console, "Консоль :wrench:");
            put(PlayerStatus.owner, "Владелец :crown:");
    }};


    public static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    public static final String minuteFormat = "{0}м",
            hourFormat = "{0}ч {1}м",
            dayFormat = "{0}д {1}ч {2}м";

    public static final String statsFormat = """
            Блоков построено: {0}
            Блоков сломано: {1}
            
            Атак захвачено: {2}
            Выживаний пройдено: {3}
            Волн прожито: {4}
            
            Заходов: {5}
            Сообщений: {6}
            Смертей: {7}
            """;
    public static final String hexStatsFormat = """
            Захвачено: {0}
            Потеряно: {1}
            Уничтожено: {2}
            
            Побед: {3}
            Поражений: {4}
            """;

    public static List<String> getServers()  {
        return new ArrayList<>(Const.servers.keySet());
    }
}
