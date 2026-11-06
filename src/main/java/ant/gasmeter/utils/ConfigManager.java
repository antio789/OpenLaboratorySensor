package ant.gasmeter.utils;

import ant.gasmeter.ApplicationState.TabData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

//saves and loads experiment configurations as .properties files in ~/GasMeter/configs/,
//so a setup can be reused for future experiments
public class ConfigManager {
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), Ref.Name, "configs");

    public static void saveConfig(String name, TabData data) throws IOException {
        if (!Files.exists(CONFIG_DIR)) {
            Files.createDirectories(CONFIG_DIR);
        }
        Properties props = new Properties();
        props.setProperty("name", name);
        props.setProperty("macAddress", data.macAddress());
        props.setProperty("sensorCount", String.valueOf(data.resolutions().size()));
        for (int i = 0; i < data.resolutions().size(); i++) {
            props.setProperty("sensor" + (i + 1) + ".lowerPin", data.lowerPins().get(i));
            props.setProperty("sensor" + (i + 1) + ".upperPin", data.upperPins().get(i));
            props.setProperty("sensor" + (i + 1) + ".resolution", String.valueOf(data.resolutions().get(i)));
        }
        Path file = CONFIG_DIR.resolve(sanitize(name) + ".properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Gas Meter experiment configuration");
        }
    }

    public static TabData loadConfig(String configName) throws IOException {
        Path file = CONFIG_DIR.resolve(configName + ".properties");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        int sensorCount = Integer.parseInt(props.getProperty("sensorCount", "0"));
        List<String> lowerPins = new ArrayList<>();
        List<String> upperPins = new ArrayList<>();
        List<Double> resolutions = new ArrayList<>();
        for (int i = 1; i <= sensorCount; i++) {
            lowerPins.add(props.getProperty("sensor" + i + ".lowerPin", ""));
            upperPins.add(props.getProperty("sensor" + i + ".upperPin", ""));
            resolutions.add(Double.parseDouble(props.getProperty("sensor" + i + ".resolution", "0")));
        }
        return new TabData(props.getProperty("macAddress", ""), lowerPins, upperPins, resolutions);
    }

    public static List<String> listConfigs(){
        List<String> names = new ArrayList<>();
        if (!Files.exists(CONFIG_DIR)) return names;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CONFIG_DIR, "*.properties")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                names.add(fileName.substring(0, fileName.length() - ".properties".length()));
            }
        } catch (IOException e) {
            System.err.println("Failed to list configs: " + e.getMessage());
        }
        return names;
    }

    //keeps the filename safe: letters, digits, dashes and underscores only
    private static String sanitize(String name){
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
