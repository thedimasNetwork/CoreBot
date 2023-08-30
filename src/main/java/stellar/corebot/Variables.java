package stellar.corebot;

import arc.files.Fi;
import mindustry.net.ArcNetProvider;
import mindustry.net.Net;
import net.dv8tion.jda.api.JDA;
import stellar.corebot.command.CommandListener;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Variables {
    public static Config config;
    public static JDA jda;
    public static CommandListener commandListener = new CommandListener();
    public static Net.NetProvider netProvider = new ArcNetProvider();
    public static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
}
