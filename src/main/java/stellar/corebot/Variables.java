package stellar.corebot;

import mindustry.net.ArcNetProvider;
import mindustry.net.Net;
import net.dv8tion.jda.api.JDA;
import stellar.corebot.command.CommandListener;

public class Variables {
    public static Config config;
    public static JDA jda;
    public static CommandListener commandListener = new CommandListener();
    public static Net.NetProvider netProvider = new ArcNetProvider();
}
