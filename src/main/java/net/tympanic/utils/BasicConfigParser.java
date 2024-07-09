package net.tympanic.utils;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class BasicConfigParser {
    Object2ObjectOpenHashMap<String,String> strings = new Object2ObjectOpenHashMap<>();
    File configFile;

    public BasicConfigParser(File config) {
        configFile = config;
    }

    public void parse() throws IOException {
        if (configFile.exists() && configFile.isFile()) {
            List<String> lines = Files.readAllLines(configFile.toPath()).stream().filter(l-> !l.isEmpty()).toList();
            String currentGroup = "";
            for (String line : lines) {
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentGroup = line.substring(1, line.length() - 1);
                } else if (!line.isEmpty()) {
                    String[] parts = line.split("=", 2);
                    strings.put(currentGroup+"."+parts[0], parts[1]);
                }
            }
        }
    }

    public String getString(String name) {
        return strings.get(name);
    }

    public Boolean getBoolean(String name) {
        return Boolean.parseBoolean(strings.get(name));
    }

    public Integer getInt(String name, Integer radix) {
        return Integer.parseInt(strings.get(name), radix);
    }

    public Integer getInt(String name) {
        return getInt(name, 10);
    }

    public Float getFloat(String name) {
        return Float.parseFloat(strings.get(name));
    }
}