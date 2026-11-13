package net.mandomc.bootstrap;

import java.io.File;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.arrival.ArrivalListener;
import net.mandomc.gameplay.arrival.ArrivalSettings;
import net.mandomc.integrations.regions.NativeArrivalProtection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MandoPlugin extends JavaPlugin implements Listener {
    private net.mandomc.gameplay.ranks.RankService ranks;
    private net.mandomc.integrations.permissions.WorldEditRanks worldEditRanks;
    private net.mandomc.gameplay.jawa.JawaTraders jawas;
    public net.mandomc.gameplay.jawa.JawaTraders jawas() { return jawas; }
    private net.mandomc.gameplay.credits.CreditService credits;
    private net.mandomc.gameplay.hud.PlayerHud hud;
    public net.mandomc.gameplay.ranks.RankService ranks() { return ranks; }
    public net.mandomc.gameplay.credits.CreditService credits() { return credits; }
    public net.mandomc.gameplay.hud.PlayerHud hud() { return hud; }
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
    private net.mandomc.gameplay.events.EventManager events;
    private net.mandomc.gameplay.travel.WarpService warps;
    public net.mandomc.gameplay.events.EventManager events() { return events; }
    public net.mandomc.gameplay.travel.WarpService warps() { return warps; }
    public net.mandomc.gameplay.travel.TeleportService travel() { return travel; }
    public net.mandomc.gameplay.territory.TerritoryService territories() { return territories; }
    private net.mandomc.gameplay.territory.TerritoryService territories;
    private net.mandomc.gameplay.worlds.WorldService worlds;
    private net.mandomc.commands.CommandBindings commandBindings;
    private net.mandomc.gameplay.admin.AdminService admin;
    private net.mandomc.gameplay.travel.TeleportRequests requests;
    private net.mandomc.gameplay.travel.PersonalTravel personal;
    public net.mandomc.gameplay.worlds.WorldService worlds() { return worlds; }
    public net.mandomc.gameplay.admin.AdminService admin() { return admin; }
    public net.mandomc.gameplay.travel.TeleportRequests requests() { return requests; }
    public net.mandomc.gameplay.travel.PersonalTravel personal() { return personal; }
    private net.mandomc.gameplay.vehicles.VehicleService vehicles;
    private net.mandomc.gameplay.explosions.TemporaryExplosions explosions;
    private net.mandomc.gameplay.vehicles.VehicleCannons vehicleCannons;
    private net.mandomc.gameplay.vehicles.VehicleRepairs vehicleRepairs;
    public net.mandomc.gameplay.vehicles.VehicleCannons vehicleCannons() { return vehicleCannons; }
    public net.mandomc.gameplay.vehicles.VehicleRepairs vehicleRepairs() { return vehicleRepairs; }
    public net.mandomc.gameplay.vehicles.VehicleService vehicles() { return vehicles; }
    public net.mandomc.gameplay.explosions.TemporaryExplosions explosions() { return explosions; }
    private ArrivalListener arrival;
    private ArrivalDestinations destinations;
    private boolean failed;
    private net.mandomc.gameplay.boba.BobaEquipment bobaEquipment;
    private net.mandomc.gameplay.boba.JetpackEquipmentMenu bobaEquipmentMenu;
    public net.mandomc.gameplay.boba.BobaEquipment bobaEquipment() { return bobaEquipment; }
    private net.mandomc.gameplay.mobs.RancorSlam rancorSlam;
    private net.mandomc.gameplay.mobs.BosskDioxis bosskDioxis;
    private net.mandomc.integrations.weapons.BosskAim bosskAim;
    public net.mandomc.gameplay.mobs.BosskDioxis bosskDioxis(){return bosskDioxis;}
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
        for (var name : new String[] {"tutorial", "spawn"}) {
            Objects.requireNonNull(getCommand(name)).setExecutor((sender, command, label, arguments) -> {
                sender.sendMessage(Component.text("Travel is unavailable. Please contact a server administrator."));
                return true;
            });
        }
        try {
            net.mandomc.config.ConfigFiles.migrate(this);
            if (getConfig().getInt("schema-version", -1) != 1) {
                throw new IllegalArgumentException("config.yml: unsupported schema-version; expected 1");
            }
            if (!new File(getDataFolder(), "language/messages.yml").isFile()) {
                saveResource("language/messages.yml", false);
            }
            var language = new YamlConfiguration();
            language.load(new File(getDataFolder(), "language/messages.yml"));
            // New message keys fall back to bundled defaults without overwriting operator translations.
            try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(getResource("language/messages.yml")),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                language.setDefaults(YamlConfiguration.loadConfiguration(reader));
            }
            territories = new net.mandomc.gameplay.territory.TerritoryService(this,
                    loadFeature("territory/maps.yml"), loadFeature("language/territory.yml"));
            new net.mandomc.integrations.weapons.TerritoryWeapons(this, territories);
            worlds = new net.mandomc.gameplay.worlds.WorldService(this, loadFeature("worlds/worlds.yml"));
            var root = new net.mandomc.commands.RootCommand(this, sender -> {
                try {
                    if (failed) {
                        throw new IllegalStateException("Arrival setup failed; see the server log and restart after repair.");
                    }
                    if (destinations != null) {
                        destinations.validate();
                        sender.sendMessage(Component.text("MandoMC arrival ready: " + destinations.spawn()
                                + (destinations.hasSurvival() ? " -> " + destinations.survival() : "")));
                    } else {
                        sender.sendMessage(Component.text("MandoMC foundation enabled; arrival is disabled."));
                    }
                } catch (IllegalStateException error) {
                    sender.sendMessage(Component.text(error.getMessage()));
                }
            }, sender -> sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(Objects.requireNonNull(language.getString("items.denied")))));
            ranks = new net.mandomc.gameplay.ranks.RankService(this, loadFeature("ranks/ranks.yml"));
            if (getServer().getPluginManager().isPluginEnabled("WorldEdit"))
                worldEditRanks = new net.mandomc.integrations.permissions.WorldEditRanks(this, ranks);
            new net.mandomc.commands.RankCommands(root, ranks, language.getConfigurationSection("ranks"));
            credits = new net.mandomc.gameplay.credits.CreditService(this, loadFeature("economy/credits.yml"));
            for (var resource : new String[] {"items/catalog.yml", "items/definitions/vehicles.yml", "items/definitions/vehicle-ammo.yml", "items/definitions/legacy.yml", "items/definitions/robes.yml", "items/definitions/materials.yml", "items/definitions/sabers.yml", "items/definitions/tusken.yml", "items/definitions/jabba.yml", "items/definitions/jabba-loot.yml", "items/definitions/dioxis.yml", "items/definitions/boba-equipment.yml", "items/definitions/massiff.yml", "mobs/drops/massiff.yml", "items/definitions/dewback.yml", "mobs/drops/dewback.yml", "combat/sabers.yml"}) {
                if (!new File(getDataFolder(), resource).isFile()) saveResource(resource, false);
            }
            // Add only missing badges and vendor registrations; preserve local overrides.
            var presentation = new YamlConfiguration();
            var presentationFile = new File(getDataFolder(), "items/catalog.yml");
            presentation.load(presentationFile);
            try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(getResource("items/catalog.yml")), java.nio.charset.StandardCharsets.UTF_8)) {
                var defaults = YamlConfiguration.loadConfiguration(reader);
                for (String group : new String[] {"rarities", "categories", "external-items"}) {
                    for (String key : Objects.requireNonNull(defaults.getConfigurationSection(group)).getKeys(false)) {
                        String path = group + "." + key;
                        if (!presentation.contains(path)) presentation.set(path, defaults.get(path));
                    }
                }
                if ("<gold>VEHICLE".equals(presentation.getString("categories.vehicles"))) {
                    presentation.set("categories.vehicles", defaults.get("categories.vehicles"));
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
            var hideConfig = loadFeature("mobs/drops/bantha.yml");
            var recipeConfig = loadFeature("items/recipes.yml");
            craftingRecipes = new net.mandomc.gameplay.items.CraftingRecipes(this, items, recipeConfig);
            recipeGuide = new net.mandomc.gameplay.items.RecipeGuide(this, craftingRecipes, recipeConfig);
            var saberConfig = new YamlConfiguration();
            saberConfig.load(new File(getDataFolder(), "combat/sabers.yml"));
            sabers = new net.mandomc.gameplay.saber.SaberService(this, items,
                    net.mandomc.gameplay.saber.SaberSettings.read(saberConfig), language.getConfigurationSection("sabers"));
            saberProjectiles = new net.mandomc.integrations.weapons.SaberProjectiles(this, sabers);
            new net.mandomc.integrations.packs.SaberPack(this, saberConfig.getConfigurationSection("upstream-pack"));
            getLogger().info("Vader saber enabled: native melee, guard, reflection and bounded throws.");
            var combatConfig = loadFeature("combat/combat.yml");
            combat = new net.mandomc.gameplay.combat.CombatService(this, combatConfig,
                    language.getConfigurationSection("combat"), sabers::finish);
            hud = new net.mandomc.gameplay.hud.PlayerHud(this, loadFeature("interface/hud.yml"), combat, ranks, credits);
            var boundaryProtection = new net.mandomc.integrations.regions.SaberProtection();
            barrier = new net.mandomc.gameplay.combat.CombatBarrier(this, combat,
                    location -> !boundaryProtection.allowed(location), combatConfig.getConfigurationSection("barrier"),
                    language.getString("combat.barrier-denied"));
            rules = new net.mandomc.gameplay.rules.SurvivalRules(this, loadFeature("rules/survival.yml"),
                    language.getConfigurationSection("rules"), sabers::isSaber, items);
            starterKit = new net.mandomc.gameplay.arrival.StarterKit(this, items, loadFeature("arrival/starter-kit.yml"),
                    language.getString("starter-kit.received"));
            getLogger().info("Combat basics ready: shared combat tag, logout death, survival rules and first-join kit.");
            if(getServer().getPluginManager().isPluginEnabled("ModelEngine") && getServer().getPluginManager().isPluginEnabled("MythicMobs"))
                new net.mandomc.integrations.mobs.HostileFacing(this,root);
            var rancorConfig=loadFeature("mobs/rancor.yml");
            if(rancorConfig.getBoolean("enabled",false)) {
                var rancorMobs=new net.mandomc.integrations.mobs.MythicMobAccess();
                rancorSlam=new net.mandomc.gameplay.mobs.RancorSlam(this,
                        net.mandomc.gameplay.mobs.RancorSlamSettings.read(rancorConfig.getConfigurationSection("slam")),
                        rancorMobs::type,boundaryProtection::allowed,root);
            }
            var bosskConfig=loadFeature("mobs/bossk.yml");
            if(bosskConfig.getBoolean("enabled",false)) {
                var bosskMobs=new net.mandomc.integrations.mobs.MythicMobAccess();
                new net.mandomc.integrations.weapons.BosskShotDamage(this,bosskMobs::type,boundaryProtection::allowed,
                        net.mandomc.gameplay.mobs.BosskShotSettings.read(bosskConfig.getConfigurationSection("shooting")));
                var bosskPose=new net.mandomc.integrations.models.BosskMuzzle();
                bosskAim=new net.mandomc.integrations.weapons.BosskAim(this,root,bosskMobs::type,boundaryProtection::allowed,bosskPose);
                bosskDioxis=new net.mandomc.gameplay.mobs.BosskDioxis(this,
                        net.mandomc.gameplay.mobs.DioxisSettings.read(bosskConfig.getConfigurationSection("dioxis")),
                        bosskMobs::type,boundaryProtection::allowed,combat::tag,root,
                        bosskPose::position,items);
                new net.mandomc.integrations.mobs.BosskCombat(this,items,bosskMobs::type,boundaryProtection::allowed);
            }
            bobaEquipment = new net.mandomc.gameplay.boba.BobaEquipment(this, items,
                    net.mandomc.gameplay.boba.BobaEquipmentSettings.read(loadFeature("equipment/boba.yml")),
                    language.getConfigurationSection("boba-equipment"), boundaryProtection::allowed);
            bobaEquipmentMenu = new net.mandomc.gameplay.boba.JetpackEquipmentMenu(this, items, bobaEquipment,
                    language.getConfigurationSection("boba-equipment"));
            bobaEquipmentMenu.register(root);
            var jabbaConfig = loadFeature("mobs/drops/jabba.yml");
            if (jabbaConfig.getBoolean("enabled", false)) {
                new net.mandomc.integrations.mobs.JabbaDrops(this, items,
                        net.mandomc.gameplay.mobs.JabbaDropsSettings.read(jabbaConfig));
                getLogger().info("Jabba generic guard drops ready; dungeon spawning is not enabled.");
            }
            jawas = new net.mandomc.gameplay.jawa.JawaTraders(this, loadFeature("mobs/jawas.yml"), root);
            var mobConfig = loadFeature("mobs/spawning.yml");
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
            var vehicleProtection = new net.mandomc.integrations.regions.VehicleProtection();
            explosions = new net.mandomc.gameplay.explosions.TemporaryExplosions(this,
                    net.mandomc.gameplay.explosions.BlastSettings.read(loadFeature("worlds/explosions.yml")), vehicleProtection::blocks);
            vehicles = new net.mandomc.gameplay.vehicles.VehicleService(this, items, combat,
                    new net.mandomc.integrations.models.ModelEngineVehicles(this), vehicleProtection, explosions,
                    language.getConfigurationSection("vehicles"));
            new net.mandomc.integrations.weapons.VehicleWeapons(this, vehicles, explosions);
            vehicleCannons = new net.mandomc.gameplay.vehicles.VehicleCannons(this, vehicles, items,
                    vehicleProtection, explosions, loadFeature("vehicles/weapons.yml"), language.getConfigurationSection("vehicles"));
            vehicleRepairs = new net.mandomc.gameplay.vehicles.VehicleRepairs(this, vehicles, items,
                    loadFeature("vehicles/repairs.yml"), language.getConfigurationSection("vehicles"));
            getLogger().info("Vehicles ready: speederbike, X-Wing, TIE Fighter and U-Wing; temporary explosions enabled.");
            var utilityConfig = loadFeature("admin/utilities.yml");
            if (utilityConfig.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("admin/utilities.yml: expected schema-version 1");
            var utilityMessages = new net.mandomc.commands.CommandMessages(language);
            travel = new net.mandomc.gameplay.travel.TeleportService(this, loadFeature("travel/teleport.yml"), language, combat);
            getServer().getPluginManager().registerEvents(travel, this);
            personal = new net.mandomc.gameplay.travel.PersonalTravel(this, utilityConfig.getInt("homes-limit"), combat::tagged);
            requests = new net.mandomc.gameplay.travel.TeleportRequests(this, travel, combat, utilityMessages, utilityConfig);
            admin = new net.mandomc.gameplay.admin.AdminService(this, combat, utilityMessages);
            new net.mandomc.commands.TravelCommands(this, travel, personal, requests, utilityMessages);
            new net.mandomc.commands.AdminCommands(this, admin, worlds, utilityMessages);
            commandBindings = new net.mandomc.commands.CommandBindings(this);
            if (!getConfig().getBoolean("arrival.enabled", false)) {
                getLogger().info("Foundation enabled; arrival is disabled.");
                return;
            }
            var settings = new ArrivalSettings(getConfig().getString("arrival.spawn-world"),
                    getConfig().getString("arrival.survival-world"));
            destinations = new ArrivalDestinations(settings, new NativeArrivalProtection(), worlds::spawn);
            destinations.validate();
            arrival = new ArrivalListener(this, settings, destinations, language);
            getServer().getPluginManager().registerEvents(arrival, this);
            net.mandomc.commands.ArrivalCommands.register(this, arrival, destinations, travel);
            warps = new net.mandomc.gameplay.travel.WarpService(this, travel, destinations, loadFeature("travel/warps.yml"));
            events = new net.mandomc.gameplay.events.EventManager(this, warps);
            getLogger().info("Legacy events and public warps ready: KOTH, Black Spire Outpost, Hoth and Tatooine; rewards disabled.");
            getLogger().info("Arrival enabled: protected Batuu spawn; /spawn and temporary /tutorial ready.");
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
        if (hud != null) hud.close();
        if (jawas != null) jawas.close();
        if (credits != null) credits.close();
        if (worldEditRanks != null) worldEditRanks.close();
        if (ranks != null) ranks.close();
        if (vehicleRepairs != null) vehicleRepairs.close();
        if (vehicleCannons != null) vehicleCannons.close();
        if (vehicles != null) vehicles.close();
        if (explosions != null) explosions.close();
        if (bobaEquipmentMenu != null) bobaEquipmentMenu.close();
        if (bobaEquipment != null) bobaEquipment.close();
        if (commandBindings != null) commandBindings.close();
        if (territories != null) territories.close();
        if (requests != null) requests.close();
        if (admin != null) admin.close();
        if(rancorSlam!=null)rancorSlam.close();
        if(bosskDioxis!=null)bosskDioxis.close();
        if(bosskAim!=null)bosskAim.close();
        if (events != null) events.close();
        if (warps != null) warps.close();
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
