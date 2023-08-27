package stellar.corebot;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;

public class Const {
    public static final String homeFolder = "bot/";
    public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
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
}
