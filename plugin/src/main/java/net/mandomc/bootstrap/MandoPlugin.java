package net.mandomc.bootstrap;

import java.io.File;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.arrival.ArrivalListener;
import net.mandomc.gameplay.arrival.ArrivalSettings;
import net.mandomc.integrations.regions.WorldGuardArrivalProtection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MandoPlugin extends JavaPlugin implements Listener {
    private net.mandomc.gameplay.combat.CombatService combat;
    private net.mandomc.gameplay.combat.CombatBarrier barrier;
    public net.mandomc.gameplay.combat.CombatBarrier barrier() { return barrier; }
    private net.mandomc.gameplay.arrival.StarterKit starterKit;
    private net.mandomc.gameplay.rules.SurvivalRules rules;
    public net.mandomc.gameplay.combat.CombatService combat() { return combat; }
    public net.mandomc.gameplay.arrival.StarterKit starterKit() { return starterKit; }
    public net.mandomc.gameplay.rules.SurvivalRules rules() { return rules; }
    private net.mandomc.gameplay.mobs.SandySpawns mobs;
    public net.mandomc.gameplay.mobs.SandySpawns mobs() { return mobs; }
    private ArrivalListener arrival;
    private ArrivalDestinations destinations;
    private boolean failed;
    private net.mandomc.gameplay.saber.SaberService sabers;
    private net.mandomc.integrations.weapons.SaberProjectiles saberProjectiles;
    public net.mandomc.integrations.weapons.SaberProjectiles saberProjectiles() { return saberProjectiles; }
    public net.mandomc.gameplay.saber.SaberService sabers() { return sabers; }
    private net.mandomc.gameplay.items.ItemCatalog items;
    private net.mandomc.commands.ItemCommand itemCommands;
    private net.mandomc.gameplay.items.CraftingRecipes craftingRecipes;
    private net.mandomc.gameplay.items.RecipeGuide recipeGuide;

    public net.mandomc.gameplay.items.ItemCatalog items() {
        return items;
    }
    private net.mandomc.gameplay.travel.TeleportService travel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        for (var name : new String[] {"tutorial", "survival", "spawn"}) {
            Objects.requireNonNull(getCommand(name)).setExecutor((sender, command, label, arguments) -> {
                sender.sendMessage(Component.text("Travel is unavailable. Please contact a server administrator."));
                return true;
            });
        }
        try {
            if (getConfig().getInt("schema-version", -1) != 1) {
                throw new IllegalArgumentException("config.yml: unsupported schema-version; expected 1");
            }
            if (!new File(getDataFolder(), "messages.yml").isFile()) {
                saveResource("messages.yml", false);
            }
            var language = new YamlConfiguration();
            language.load(new File(getDataFolder(), "messages.yml"));
            // New message keys fall back to bundled defaults without overwriting operator translations.
            try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(getResource("messages.yml")),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                language.setDefaults(YamlConfiguration.loadConfiguration(reader));
            }
            var root = new net.mandomc.commands.RootCommand(this, sender -> {
                try {
                    if (failed) {
                        throw new IllegalStateException("Arrival setup failed; see the server log and restart after repair.");
                    }
                    if (destinations != null) {
                        destinations.validate();
                        sender.sendMessage(Component.text("MandoMC arrival ready: " + destinations.spawn()
                                + " -> " + destinations.survival()));
                    } else {
                        sender.sendMessage(Component.text("MandoMC foundation enabled; arrival is disabled."));
                    }
                } catch (IllegalStateException error) {
                    sender.sendMessage(Component.text(error.getMessage()));
                }
            }, sender -> sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(Objects.requireNonNull(language.getString("items.denied")))));
            for (var resource : new String[] {"items.yml", "items/robes.yml", "items/materials.yml", "items/sabers.yml", "items/tusken.yml", "items/massiff.yml", "massiff-drops.yml", "items/dewback.yml", "dewback-drops.yml", "sabers.yml"}) {
                if (!new File(getDataFolder(), resource).isFile()) saveResource(resource, false);
            }
            // Add only missing presentation badges when new bundled item families arrive.
            var presentation = new YamlConfiguration();
            var presentationFile = new File(getDataFolder(), "items.yml");
            presentation.load(presentationFile);
            try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(getResource("items.yml")), java.nio.charset.StandardCharsets.UTF_8)) {
                var defaults = YamlConfiguration.loadConfiguration(reader);
                for (String group : new String[] {"rarities", "categories"}) {
                    for (String key : Objects.requireNonNull(defaults.getConfigurationSection(group)).getKeys(false)) {
                        String path = group + "." + key;
                        if (!presentation.contains(path)) presentation.set(path, defaults.getString(path));
                    }
                }
            }
            presentation.save(presentationFile);
            items = new net.mandomc.gameplay.items.ItemCatalog(getDataFolder().toPath(),
                    new net.mandomc.integrations.weapons.WeaponMechanicsItems()::generate);
            itemCommands = new net.mandomc.commands.ItemCommand(this, items, language, root);
            try {
                items.reload();
            new net.mandomc.gameplay.items.ItemPresentationRefresh(this, items);
                getLogger().info("Loaded " + items.ids().size() + " custom items.");
            } catch (Exception error) {
                getLogger().severe("Custom items unavailable; repair config and /mandomc items reload: " + error.getMessage());
            }
            var hideConfig = loadFeature("bantha-hide.yml");
            var recipeConfig = loadFeature("recipes.yml");
            craftingRecipes = new net.mandomc.gameplay.items.CraftingRecipes(this, items, recipeConfig);
            recipeGuide = new net.mandomc.gameplay.items.RecipeGuide(this, craftingRecipes, recipeConfig);
            var saberConfig = new YamlConfiguration();
            saberConfig.load(new File(getDataFolder(), "sabers.yml"));
            sabers = new net.mandomc.gameplay.saber.SaberService(this, items,
                    net.mandomc.gameplay.saber.SaberSettings.read(saberConfig), language.getConfigurationSection("sabers"));
            saberProjectiles = new net.mandomc.integrations.weapons.SaberProjectiles(this, sabers);
            new net.mandomc.integrations.packs.SaberPack(this, saberConfig.getConfigurationSection("upstream-pack"));
            getLogger().info("Vader saber enabled: native melee, guard, reflection and bounded throws.");
            var combatConfig = loadFeature("combat.yml");
            combat = new net.mandomc.gameplay.combat.CombatService(this, combatConfig,
                    language.getConfigurationSection("combat"), sabers::finish);
            var boundaryProtection = new net.mandomc.integrations.regions.SaberProtection();
            barrier = new net.mandomc.gameplay.combat.CombatBarrier(this, combat,
                    location -> !boundaryProtection.allowed(location), combatConfig.getConfigurationSection("barrier"),
                    language.getString("combat.barrier-denied"));
            rules = new net.mandomc.gameplay.rules.SurvivalRules(this, loadFeature("survival-rules.yml"),
                    language.getConfigurationSection("rules"), sabers::isSaber, items);
            starterKit = new net.mandomc.gameplay.arrival.StarterKit(this, items, loadFeature("starter-kit.yml"),
                    language.getString("starter-kit.received"));
            getLogger().info("Combat basics ready: shared combat tag, logout death, survival rules and first-join kit.");
            var mobConfig = loadFeature("mobs.yml");
            if (mobConfig.getBoolean("enabled", false)) {
                var settings = net.mandomc.gameplay.mobs.SandySettings.read(mobConfig);
                var mythic = new net.mandomc.integrations.mobs.MythicMobAccess();
                new net.mandomc.integrations.mobs.MassiffDrops(this, items);
                new net.mandomc.integrations.mobs.DewbackDrops(this, items);
                new net.mandomc.integrations.weapons.MobWeaponDamage(this, mythic, settings);
                new net.mandomc.integrations.mobs.TuskenDrops(this, items,
                        mobConfig.getDouble("weapon-drops.chance", 0.10));
                new net.mandomc.integrations.mobs.BanthaDrops(this, items, hideConfig.getConfigurationSection("drops"));
                mobs = new net.mandomc.gameplay.mobs.SandySpawns(this, settings, mythic);
                getLogger().info("Sandy mobs ready: five types, levelled encounters and shared population limits.");
            }
            if (!getConfig().getBoolean("arrival.enabled", false)) {
                getLogger().info("Foundation enabled; arrival is disabled.");
                return;
            }
            if (!getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                throw new IllegalStateException("arrival.enabled requires WorldGuard");
            }
            var settings = new ArrivalSettings(getConfig().getString("arrival.spawn-world"),
                    getConfig().getString("arrival.survival-world"));
            destinations = new ArrivalDestinations(settings, new WorldGuardArrivalProtection());
            destinations.validate();
            arrival = new ArrivalListener(this, settings, destinations, language);
            getServer().getPluginManager().registerEvents(arrival, this);
            if (!new File(getDataFolder(), "teleport.yml").isFile()) saveResource("teleport.yml", false);
            var teleportConfig = new YamlConfiguration();
            teleportConfig.load(new File(getDataFolder(), "teleport.yml"));
            travel = new net.mandomc.gameplay.travel.TeleportService(this, teleportConfig, language, combat);
            getServer().getPluginManager().registerEvents(travel, this);
            net.mandomc.commands.ArrivalCommands.register(this, arrival, destinations, travel);
            getLogger().info("Arrival enabled: protected SpawnWorld -> survival; /spawn, /survival and temporary /tutorial ready.");
        } catch (Exception | LinkageError error) {
            failed = true;
            getLogger().severe("Arrival unavailable; player logins blocked until repaired: " + error.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockUnconfiguredArrival(PlayerLoginEvent event) {
        if (failed) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    Component.text("Arrival is unavailable. Please contact a server administrator."));
        }
    }

    private YamlConfiguration loadFeature(String name) throws Exception {
        if (!new File(getDataFolder(), name).isFile()) saveResource(name, false);
        var config = new YamlConfiguration();
        config.load(new File(getDataFolder(), name));
        return config;
    }

    @Override
    public void onDisable() {
        if (recipeGuide != null) recipeGuide.close();
        if (craftingRecipes != null) craftingRecipes.close();
        if (barrier != null) barrier.close();
        if (mobs != null) mobs.close();
        if (combat != null) combat.close();
        if (sabers != null) sabers.close();
        if (travel != null) travel.close();
        if (itemCommands != null) itemCommands.close();
    }
}
