package ru.aegis.core;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Keeps the resource pack folder (plugins/AegisCore/resourcepack), zips it into pack.zip,
 * hashes it, optionally serves it over HTTP and sends it to players.
 */
public final class PackManager {

    public static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("aegis-resourcepack".getBytes(StandardCharsets.UTF_8));
    private static final long FIXED_TIME = 946684800000L; // keeps the zip hash stable

    private final AegisCore plugin;
    private final File packDir;
    private final File packZip;

    private volatile byte[] zipBytes;
    private volatile byte[] sha1;
    private volatile String url = "";

    private HttpServer http;
    private ExecutorService executor;

    public PackManager(AegisCore plugin) {
        this.plugin = plugin;
        this.packDir = new File(plugin.getDataFolder(), "resourcepack");
        this.packZip = new File(plugin.getDataFolder(), "pack.zip");
    }

    public void load() {
        if (!plugin.getConfig().getBoolean("resourcepack.enabled", true)) return;
        if (!packDir.exists()) {
            extractDefaults(false);
        }
        rebuild();
        startHttp();
    }

    public void reload() {
        stop();
        load();
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Copies the built-in pack into plugins/AegisCore/resourcepack. */
    public int extractDefaults(boolean overwrite) {
        int count = 0;
        try (InputStream in = plugin.getResource("defaultpack.zip")) {
            if (in == null) {
                plugin.getLogger().severe("defaultpack.zip is missing inside the plugin jar.");
                return 0;
            }
            Path root = packDir.toPath().toAbsolutePath().normalize();
            Files.createDirectories(root);
            try (ZipInputStream zin = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    Path target = root.resolve(entry.getName()).normalize();
                    if (!target.startsWith(root)) continue; // zip-slip guard
                    if (Files.exists(target) && !overwrite) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(zin, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not extract the default resource pack: " + ex.getMessage());
        }
        return count;
    }

    /** Zips the folder into pack.zip and recalculates the SHA-1 and URL. */
    public synchronized void rebuild() {
        Path root = packDir.toPath();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            plugin.getDataFolder().mkdirs();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(packZip))) {
                for (Path f : files) {
                    String name = root.relativize(f).toString().replace(File.separatorChar, '/');
                    String lower = name.toLowerCase();
                    if (name.startsWith(".") || lower.endsWith(".ds_store") || lower.endsWith("thumbs.db")) continue;
                    ZipEntry ze = new ZipEntry(name);
                    ze.setTime(FIXED_TIME);
                    zos.putNextEntry(ze);
                    Files.copy(f, zos);
                    zos.closeEntry();
                }
            }
            byte[] data = Files.readAllBytes(packZip.toPath());
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(data);
            zipBytes = data;
            sha1 = hash;
            url = computeUrl(HexFormat.of().formatHex(hash));
            plugin.getLogger().info("Resource pack built: " + data.length + " bytes, sha1=" + HexFormat.of().formatHex(hash));
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not build pack.zip: " + ex.getMessage());
        }
    }

    private String computeUrl(String hex) {
        FileConfiguration c = plugin.getConfig();
        if ("external".equalsIgnoreCase(c.getString("resourcepack.mode", "embedded"))) {
            return c.getString("resourcepack.external-url", "");
        }
        String host = c.getString("resourcepack.public-host", "localhost");
        int port = c.getInt("resourcepack.port", 8123);
        return "http://" + host + ":" + port + "/pack.zip?v=" + hex.substring(0, 10);
    }

    private void startHttp() {
        FileConfiguration c = plugin.getConfig();
        if (!"embedded".equalsIgnoreCase(c.getString("resourcepack.mode", "embedded"))) return;
        int port = c.getInt("resourcepack.port", 8123);
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/pack.zip", ex -> {
                byte[] data = zipBytes;
                if (data == null) {
                    ex.sendResponseHeaders(503, -1);
                    ex.close();
                    return;
                }
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, data.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(data);
                }
            });
            executor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "aegis-pack-http");
                t.setDaemon(true);
                return t;
            });
            http.setExecutor(executor);
            http.start();
            plugin.getLogger().info("Pack HTTP server listening on port " + port + " -> " + url);
        } catch (IOException ex) {
            http = null;
            plugin.getLogger().severe("Could not open pack HTTP port " + port + ": " + ex.getMessage()
                    + " (change resourcepack.port or use mode: external)");
        }
    }

    public boolean isReady() {
        return zipBytes != null && sha1 != null && url != null && !url.isEmpty();
    }

    public void send(Player p) {
        if (!isReady()) return;
        Component prompt = AegisCore.MM.deserialize(plugin.getConfig().getString("resourcepack.prompt", ""));
        boolean force = plugin.getConfig().getBoolean("resourcepack.required", true);
        p.setResourcePack(PACK_ID, url, sha1, prompt, force);
    }

    public String url() { return url; }

    public String sha1Hex() {
        return sha1 == null ? "-" : HexFormat.of().formatHex(sha1);
    }

    public File zipFile() { return packZip; }

    public File folder() { return packDir; }
}
