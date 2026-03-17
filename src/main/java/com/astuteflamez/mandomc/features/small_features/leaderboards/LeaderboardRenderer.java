package com.astuteflamez.mandomc.features.small_features.leaderboards;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class LeaderboardRenderer {

    public static void render(Leaderboard board, List<LeaderboardEntry> entries) {

        clearBoard(board);

        Location base = board.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        boolean kills = board.getId().contains("kills");

        Color start = kills ? Color.fromRGB(255,70,70) : Color.fromRGB(90,255,150);
        Color end   = kills ? Color.fromRGB(120,0,0)   : Color.fromRGB(0,120,60);

        // Minecraft-safe icons
        String icon = kills ? "⚔ " : "¤ ";

        Component title = gradient(
                icon + board.getId().replace("-", " ").toUpperCase(),
                start,
                end
        ).decorate(TextDecoration.BOLD);

        spawnLine(board, base.clone().add(0,2.5,0), title, 0.8f);

        spawnLine(board, base.clone().add(0,2.2,0),
                Component.text("TOP TEN PLAYERS")
                        .color(TextColor.color(180,180,180)),
                0.55f);

        int limit = 10;

        for(int i=0;i<limit;i++){

            Component line;

            if(i < entries.size()){

                LeaderboardEntry e = entries.get(i);

                TextColor rankColor;

                if(i==0) rankColor = TextColor.color(255,215,0);
                else if(i==1) rankColor = TextColor.color(200,200,200);
                else if(i==2) rankColor = TextColor.color(205,127,50);
                else rankColor = TextColor.color(255,255,255);

                Component rank = Component.text((i+1)+". ")
                        .color(rankColor)
                        .decorate(TextDecoration.BOLD);

                Component name = Component.text(e.getName())
                        .color(TextColor.color(240,240,240));

                Component dash = Component.text(" - ")
                        .color(TextColor.color(120,120,120));

                Component value = Component.text((int)e.getValue())
                        .color(TextColor.color(
                                start.getRed(),
                                start.getGreen(),
                                start.getBlue()))
                        .decorate(TextDecoration.BOLD);

                line = rank.append(name).append(dash).append(value);

            }else{

                line = Component.text((i+1)+".")
                        .color(TextColor.color(90,90,90));
            }

            spawnLine(board, base.clone().add(0,1.9 - i*0.25,0), line, 0.6f);
        }
    }

        private static void spawnLine(Leaderboard board, Location loc, Component text, float scale){

                TextDisplay d = loc.getWorld().spawn(loc, TextDisplay.class);

                // 🔥 TAGGING (CRITICAL)
                d.addScoreboardTag("mandomc_lb");
                d.addScoreboardTag("lb_" + board.getId());

                d.text(text);
                d.setShadowed(true);
                d.setSeeThrough(true);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(20,0,0,0));

                var t = d.getTransformation();
                t.getScale().set(scale, scale, scale);
                d.setTransformation(t);

                board.getDisplays().add(d);
        }

    private static void clearBoard(Leaderboard board){

        board.getDisplays().forEach(TextDisplay::remove);
        board.getDisplays().clear();
    }

    private static Component gradient(String text, Color start, Color end){

        Component comp = Component.empty();

        for(int i=0;i<text.length();i++){

            float ratio = (float)i/(text.length()-1);

            int r = (int)(start.getRed() + ratio*(end.getRed()-start.getRed()));
            int g = (int)(start.getGreen()+ ratio*(end.getGreen()-start.getGreen()));
            int b = (int)(start.getBlue() + ratio*(end.getBlue()-start.getBlue()));

            comp = comp.append(
                    Component.text(text.charAt(i))
                            .color(TextColor.color(r,g,b))
            );
        }

        return comp;
    }
}