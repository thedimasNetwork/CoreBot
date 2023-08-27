package stellar.corebot;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;

public class Const {
    public static final String homeFolder = "bot/";
    public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
    public static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    public static final String minuteFormat = "{0}m",
            hourFormat = "{0}h {1}m",
            dayFormat = "{0}d {1}h {2}m";
}
