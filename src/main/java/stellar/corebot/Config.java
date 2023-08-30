package stellar.corebot;

import arc.util.Log;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import stellar.database.Database;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
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

    private Database database;


    public static void load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        if (!new File(Const.homeFolder).exists()) {
            new File(Const.homeFolder).mkdirs();
            unpackResources();
        }

        if (!new File(Const.homeFolder + "bot.yaml").exists()) {
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

    public static void unpackResources() {
        try {
            ClassLoader classLoader = Config.class.getClassLoader();

            Path targetPath = Paths.get("bot");
            Files.createDirectories(targetPath);

            InputStream inputStream;
            OutputStream outputStream;

            Enumeration<URL> resources = classLoader.getResources("/");
            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                String resourcePath = resourceUrl.getPath();
                String resourceName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1); // Get just the filename
                try {
                    inputStream = classLoader.getResourceAsStream(resourcePath);

                    Path targetFilePath = targetPath.resolve(resourceName);

                    outputStream = Files.newOutputStream(targetFilePath);

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }

                    inputStream.close();
                    outputStream.close();
                } catch (IOException e) {
                    Log.err("Unable to unpack " + resourceName, e);
                }
            }
        } catch (IOException e) {
            Log.err(e);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Database {
        private String ip;
        private int port;
        private String name;
        private String user;
        private String password;
    }
}
