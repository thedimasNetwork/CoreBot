package stellar.corebot;

import arc.files.Fi;
import arc.util.Log;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Config {
    private String botToken;

    private long mapsChannel;
    private long schematicsChannel;
    private long suggestionsChannel;
    private long botChannel;

    private long statusChannel;
    private long statusMessage;
    private int statusUpdatePeriod;


    public static void load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        if (!new Fi(Const.homeFolder).exists()) {
            new Fi(Const.homeFolder).mkdirs();
            // copy important resources if necessary
        }

        if (!new Fi(Const.homeFolder + "bot.yaml").exists()) {
            try (InputStream is = Config.class.getClassLoader().getResourceAsStream("bot.yaml")) {
                Files.copy(Objects.requireNonNull(is), Path.of(Const.homeFolder + "bot.yaml"));
            } catch (IOException e) {
                Log.err(e);
            }
        }
        try {
            Variables.config = mapper.readValue(new File(Const.homeFolder + "bot.yaml"), Config.class);
        } catch (IOException e) {
            Log.err(e);
        }
    }
}
