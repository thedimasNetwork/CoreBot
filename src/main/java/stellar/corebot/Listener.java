package stellar.corebot;

import arc.util.Log;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class Listener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Log.info("@: @", event.getAuthor().getEffectiveName(), event.getMessage().getContentDisplay());
    }
}
