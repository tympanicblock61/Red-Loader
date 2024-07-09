package net.tympanic;

import com.google.gson.*;
import net.tympanic.api.RedLoaderMod;
import net.tympanic.utils.BasicConfigParser;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class RedLoader {
    static File CONFIG_FILE = new File("Cosmic Reach.cfg");;
    static File MODS = new File("mods");
    static File COSMIC_REACH_JAR = null;
    static String MainClass = null;
    public static BasicConfigParser config = null;
    public static ClassLoader classLoader;

    public static void main(String[] args) {
        try {
            System.out.println(System.getProperty("file.separator"));
            System.out.println("help");
            locateJar();

            if (MainClass == null) {
                JarFile jar = new JarFile(COSMIC_REACH_JAR);
                Manifest manifest = jar.getManifest();
                MainClass = manifest.getMainAttributes().getValue("Main-Class");
                if (MainClass == null) {
                    System.out.println("Main class name not provided and could not be found in manifest.");
                    System.exit(1);
                }
            }

            load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void load() throws Exception {
        List<URL> urls = new ArrayList<>();
        List<JsonElement> clients = new ArrayList<>();
        urls.add(COSMIC_REACH_JAR.toURI().toURL());

        if (MODS.exists() && MODS.isDirectory()) {
            File[] files = MODS.listFiles();
            if (files != null) {
                List<JarFile> mods = Arrays.stream(files)
                        .filter(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"))
                        .map(file -> {
                            try {
                                return new JarFile(file);
                            } catch (IOException e) {
                                return null;
                            }
                        }).filter(Objects::nonNull).toList();

                for (JarFile mod : mods) {
                    ZipEntry modJsonEntry = mod.getEntry("red.mod.json");
                    if (modJsonEntry == null) continue;
                    JsonObject ModJson = (JsonObject) JsonParser.parseReader(new InputStreamReader(mod.getInputStream(modJsonEntry), StandardCharsets.UTF_8));
                    JsonObject entrypoints = ModJson.getAsJsonObject("entrypoints");
                    if (entrypoints == null) continue;
                    JsonArray clientsJson = entrypoints.getAsJsonArray("client");
                    if (clientsJson == null) continue;
                    urls.add(new URL("jar:file:" + mod.getName() + "!/"));
                    clients.addAll(clientsJson.asList());
                }
            }
        }

        classLoader = new URLClassLoader(urls.toArray(new URL[0]));

        Thread loadThread = new Thread(() -> {
            try {
                Class<?> mainClass = classLoader.loadClass(MainClass);
                Method mainMethod = mainClass.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) new String[]{});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        loadThread.start();

        for (JsonElement client : clients) {
            Class<?> clazz = classLoader.loadClass(client.getAsString());
            if (RedLoaderMod.class.isAssignableFrom(clazz)) {
                RedLoaderMod modInstance = (RedLoaderMod) clazz.getDeclaredConstructor().newInstance();
                modInstance.onInitialize();
            }
        }
    }

    private static void locateJar() throws IOException, NoSuchFileException {
        if (CONFIG_FILE.exists() && CONFIG_FILE.isFile()) {
            config = new BasicConfigParser(CONFIG_FILE);
            config.parse();
            String classpath = config.getString("Application.app.classpath");
            String mainclass = config.getString("Application.app.mainclass");
            if (classpath != null) COSMIC_REACH_JAR = new File(classpath.split("\\\\", 2)[1]);
            if (mainclass != null) MainClass = mainclass;
        }

        if (COSMIC_REACH_JAR == null) {
            List<File> files = Files.find(Path.of("./"), 1, (path, attributes) -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.startsWith("cosmic reach") && name.endsWith(".jar");
            }).map(Path::toFile).toList();

            if (!files.isEmpty()) COSMIC_REACH_JAR = files.get(0);
        }

        if (COSMIC_REACH_JAR == null) {
            throw new IllegalStateException("Cosmic Reach jar file cannot be found");
        }
    }

    private static boolean isDevEnvironment() {
        String env = System.getenv("ENV");
        String property = System.getProperty("env");
        URL devResource = RedLoader.class.getClassLoader().getResource("development.marker");
        boolean isJar = RedLoader.class.getProtectionDomain().getCodeSource().getLocation().getFile().endsWith(".jar");
        return !isJar || devResource != null || "development".equalsIgnoreCase(property) || "development".equalsIgnoreCase(env);
    }
}