package com.astuteflamez.mandomc;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;

public class LangConfig {

    private static File file;
    private static FileConfiguration customFile;

    //Finds or generates the custom config file
    public static void setup() {
        file = new File(Bukkit.getServer().getPluginManager().getPlugin("MandoMC").getDataFolder(), "lang.yml");

        if (!file.exists()) {
            try (InputStream inputStream = MandoMC.class.getResourceAsStream("/lang.yml");
                 OutputStream outputStream = new FileOutputStream(file)) {

                if (inputStream != null) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        customFile = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get(){
        return customFile;
    }

    public static void save(){
        try{
            customFile.save(file);
        }catch (IOException e){
            System.out.println("Couldn't save file");
        }
    }

    public static void reload(){
        customFile = YamlConfiguration.loadConfiguration(file);
    }

}

