package stellar.corebot;

import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.g2d.TextureAtlas;
import arc.struct.ObjectMap;
import arc.struct.StringMap;
import arc.util.Http;
import arc.util.Log;
import arc.util.io.CounterInputStream;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.core.Version;
import mindustry.core.World;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.game.Team;
import mindustry.io.MapIO;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.world.Block;
import mindustry.world.CachedTile;
import mindustry.world.Tile;
import mindustry.world.WorldContext;
import mindustry.world.blocks.environment.OreBlock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.zip.InflaterInputStream;

public class ContentHandler {
    static Color co = new Color();

    public static void load() {
        Version.enabled = false;
        Vars.content = new ContentLoader();
        Vars.content.createBaseContent();
        Vars.world = new World();
        Vars.state = new GameState();

        for (ContentType type : ContentType.all) {
            for (Content content : Vars.content.getBy(type)) {
                try {
                    content.init();
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            BufferedImage image = ImageIO.read(ContentHandler.class.getClassLoader().getResource("block_colors.png"));
            for (Block block : Vars.content.blocks()) {
                block.mapColor.argb8888(image.getRGB(block.id, 0));
                if (block instanceof OreBlock) {
                    block.mapColor.set(block.itemDrop.color);
                }
            }
        } catch (IOException e) {
            Log.err(e);
        }
    }

    public static InputStream download(String url) throws IOException {
        URL requestUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
        connection.setRequestMethod("GET");
        return connection.getInputStream();
    }

    public static Map readMap(InputStream is) throws IOException {
        try (InputStream ifs = new InflaterInputStream(is); CounterInputStream counter = new CounterInputStream(ifs); DataInputStream stream = new DataInputStream(counter)) {
            Map out = new Map();

            SaveIO.readHeader(stream);
            int version = stream.readInt();
            SaveVersion ver = SaveIO.getSaveWriter(version);
            StringMap[] metaOut = {null};
            ver.region("meta", stream, counter, in -> metaOut[0] = ver.readStringMap(in));

            StringMap meta = metaOut[0];

            out.name = meta.get("name", "Unknown");
            out.author = meta.get("author");
            out.description = meta.get("description");
            out.tags = meta;

            int width = meta.getInt("width"), height = meta.getInt("height");

            var floors = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var walls = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var fgraphics = floors.createGraphics();
            var jcolor = new java.awt.Color(0, 0, 0, 64);
            int black = 255;
            CachedTile tile = new CachedTile() {
                @Override
                public void setBlock(Block type) {
                    super.setBlock(type);

                    int c = MapIO.colorFor(block(), Blocks.air, Blocks.air, team());
                    if (c != black && c != 0) {
                        walls.setRGB(x, floors.getHeight() - 1 - y, conv(c));
                        fgraphics.setColor(jcolor);
                        fgraphics.drawRect(x, floors.getHeight() - 1 - y + 1, 1, 1);
                    }
                }
            };

            ver.region("content", stream, counter, ver::readContentHeader);
            ver.region("preview_map", stream, counter, in -> ver.readMap(in, new WorldContext() {
                @Override
                public void resize(int width, int height) {
                }

                @Override
                public boolean isGenerating() {
                    return false;
                }

                @Override
                public void begin() {
                    Vars.world.setGenerating(true);
                }

                @Override
                public void end() {
                    Vars.world.setGenerating(false);
                }

                @Override
                public void onReadBuilding() {
                    //read team colors
                    if (tile.build != null) {
                        int c = tile.build.team.color.argb8888();
                        int size = tile.block().size;
                        int offsetx = -(size - 1) / 2;
                        int offsety = -(size - 1) / 2;
                        for (int dx = 0; dx < size; dx++) {
                            for (int dy = 0; dy < size; dy++) {
                                int drawx = tile.x + dx + offsetx, drawy = tile.y + dy + offsety;
                                walls.setRGB(drawx, floors.getHeight() - 1 - drawy, c);
                            }
                        }
                    }
                }

                @Override
                public Tile tile(int index) {
                    tile.x = (short) (index % width);
                    tile.y = (short) (index / width);
                    return tile;
                }

                @Override
                public Tile create(int x, int y, int floorID, int overlayID, int wallID) {
                    if (overlayID != 0) {
                        floors.setRGB(x, floors.getHeight() - 1 - y, conv(MapIO.colorFor(Blocks.air, Blocks.air, Vars.content.block(overlayID), Team.derelict)));
                    } else {
                        floors.setRGB(x, floors.getHeight() - 1 - y, conv(MapIO.colorFor(Blocks.air, Vars.content.block(floorID), Blocks.air, Team.derelict)));
                    }
                    return tile;
                }
            }));

            fgraphics.drawImage(walls, 0, 0, null);
            fgraphics.dispose();

            out.image = floors;

            return out;

        } finally {
            Vars.content.setTemporaryMapper(null);
        }
    }

    static int conv(int rgba) {
        return co.set(rgba).argb8888();
    }

    public static class Map {
        public String name, author, description;
        public ObjectMap<String, String> tags = new ObjectMap<>();
        public BufferedImage image;
    }
}
