package com.astuteflamez.mandomc.core;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import com.astuteflamez.mandomc.content.lightsabers.listeners.*;
import com.astuteflamez.mandomc.core.commands.ReloadCommand;
import com.astuteflamez.mandomc.core.commands.TestCommand;
import com.astuteflamez.mandomc.core.guis.*;
import com.astuteflamez.mandomc.mechanics.fuel.listeners.*;
import com.astuteflamez.mandomc.mechanics.warps.*;
import com.astuteflamez.mandomc.system.events.*;
import com.astuteflamez.mandomc.system.events.commands.EventCommand;
import com.astuteflamez.mandomc.system.events.listeners.EventMenuListener;
import com.astuteflamez.mandomc.system.events.types.jabba_dungeon.*;
import com.astuteflamez.mandomc.system.events.types.koth.*;
import com.astuteflamez.mandomc.system.events.types.mining.BeskarMiningListener;
import com.astuteflamez.mandomc.system.items.*;
import com.astuteflamez.mandomc.system.items.commands.*;
import com.astuteflamez.mandomc.system.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.system.items.listeners.*;
import com.astuteflamez.mandomc.system.planets.ilum.ParkourTimerDisplay;
import com.astuteflamez.mandomc.system.planets.ilum.commands.ParkourFinishCommand;
import com.astuteflamez.mandomc.system.planets.ilum.configs.ParkourConfig;
import com.astuteflamez.mandomc.system.planets.ilum.listeners.ParkourCheckpointListener;
import com.astuteflamez.mandomc.system.planets.ilum.listeners.ParkourDisconnectListener;
import com.astuteflamez.mandomc.system.planets.ilum.listeners.ParkourItemListener;
import com.astuteflamez.mandomc.system.planets.ilum.listeners.ParkourProtectionListener;
import com.astuteflamez.mandomc.system.planets.ilum.listeners.ParkourRespawnListener;
import com.astuteflamez.mandomc.system.planets.ilum.listeners.ParkourWorldListener;
import com.astuteflamez.mandomc.system.planets.ilum.managers.CheckpointManager;
import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourLeaderboardManager;
import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourManager;
import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourTimeManager;
import com.astuteflamez.mandomc.system.planets.tatooine.TatooinePotListener;
import com.astuteflamez.mandomc.system.vehicles.Vehicle;
import com.astuteflamez.mandomc.system.vehicles.listeners.*;
import com.astuteflamez.mandomc.system.vehicles.movement.*;
import com.ticxo.modelengine.api.ModelEngineAPI;

public final class MandoMC extends JavaPlugin {

    public static MandoMC instance;
    public static final HashMap<UUID, Vehicle> activeVehicles = new HashMap<>();

    private NamespacedKey vehicleKey;

    private GUIManager guiManager;

    private ParkourManager parkourManager;
    private ParkourTimeManager timeManager;
    private ParkourLeaderboardManager parkourLeaderboardManager;
    private ParkourTimerDisplay timerDisplay;

    private EventManager eventManager;
    private EventScheduler eventScheduler;

    private TatooinePotListener potListener;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("§a[MandoMC] Starting up...");

        setupConfigs();
        setupCoreSystems();

        setupGUI();
        setupWarpSystem();
        setupParkourSystem();
        setupVehicleSystem();
        setupItemsSystem();
        setupEventSystem();

        registerCommands();
        registerListeners();
        registerModelEngineControllers();

        setupTatooinePots();

        getLogger().info("§a[MandoMC] Enabled successfully!");
    }

    @Override
    public void onDisable() {

        shutdownEventSystem();

        if (parkourLeaderboardManager != null) {
            parkourLeaderboardManager.removeAllDisplays();
        }

        if (potListener != null) {
            potListener.disable();
        }

        getLogger().info("§c[MandoMC] Disabled.");
    }

    /*
     * --------------------------------
     * SETUP
     * --------------------------------
     */

    private void setupConfigs() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        WarpConfig.setup();
        WarpConfig.get().options().copyDefaults(true);
        WarpConfig.save();

        ParkourConfig.setup();
        ParkourConfig.get().options().copyDefaults(true);
        ParkourConfig.save();

        ItemsConfig.setup();
        ItemsConfig.reload();
    }

    private void setupCoreSystems() {
        vehicleKey = new NamespacedKey(this, "vehicle_id");
    }

    private void setupGUI() {
        guiManager = new GUIManager();
    }

    private void setupWarpSystem() {}

    private void setupParkourSystem() {
        timeManager = new ParkourTimeManager(this);
        parkourLeaderboardManager = new ParkourLeaderboardManager(this, timeManager);
        parkourManager = new ParkourManager(timeManager, parkourLeaderboardManager);

        parkourLeaderboardManager.updateLeaderboards();
        parkourLeaderboardManager.startAutoUpdate();

        timerDisplay = new ParkourTimerDisplay(this, parkourManager, timeManager);
        timerDisplay.start();
    }

    private void setupVehicleSystem() {}

    private void setupItemsSystem() {
        ItemLoader.loadItems();
    }

    private void setupEventSystem() {
        eventManager = new EventManager(this);
        eventManager.load();

        eventScheduler = new EventScheduler(this, eventManager);
        eventScheduler.start();
    }

    private void setupTatooinePots() {
        potListener = new TatooinePotListener();
        Bukkit.getPluginManager().registerEvents(potListener, this);
        potListener.enable();
    }

    private void shutdownEventSystem() {
        if (eventScheduler != null) {
            eventScheduler.stop();
        }
        KothEvent.clearRewardChest();
    }

    /*
     * --------------------------------
     * COMMANDS
     * --------------------------------
     */

    private void registerCommands() {
        safeCommand("warps", new WarpCommand(guiManager));
        safeCommand("test", new TestCommand());
        safeCommand("mmcreload", new ReloadCommand(this));

        GetCommand get = new GetCommand();
        GiveCommand give = new GiveCommand();
        DropCommand drop = new DropCommand();
        RecipeCommand recipe = new RecipeCommand();

        safeCommand("get", get, get);
        safeCommand("give", give, give);
        safeCommand("drop", drop, drop);
        safeCommand("recipes", recipe, recipe);

        safeCommand("parkourfinish", new ParkourFinishCommand(parkourManager));

        EventCommand eventCmd = new EventCommand(eventManager);
        safeCommand("event", eventCmd, eventCmd);

        KeyCommand key = new KeyCommand(this);
        safeCommand("key", key, key);
    }

    private void safeCommand(String name, org.bukkit.command.CommandExecutor exec) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(exec);
        }
    }

    private void safeCommand(String name,
                             org.bukkit.command.CommandExecutor exec,
                             org.bukkit.command.TabCompleter tab) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(exec);
            getCommand(name).setTabCompleter(tab);
        }
    }

    /*
     * --------------------------------
     * LISTENERS
     * --------------------------------
     */

    private void registerListeners() {

        // GUI
        Bukkit.getPluginManager().registerEvents(new GUIListener(guiManager), this);

        // Items
        Bukkit.getPluginManager().registerEvents(new ItemBrowserListener(), this);
        Bukkit.getPluginManager().registerEvents(new RecipeListener(), this);

        // Lightsabers
        Bukkit.getPluginManager().registerEvents(new SaberHitListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberThrowListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberToggleListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberDeflectListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberWeaponMechanicsDeflectListener(), this);

        // Vehicles
        Bukkit.getPluginManager().registerEvents(new SpawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new MountListener(), this);
        Bukkit.getPluginManager().registerEvents(new PickupListener(), this);
        Bukkit.getPluginManager().registerEvents(new VehicleCanisterInteractListener(), this);
        Bukkit.getPluginManager().registerEvents(new DamageListener(), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new RepairListener(), this);
        Bukkit.getPluginManager().registerEvents(new ShootListener(), this);

        // Fuel
        Bukkit.getPluginManager().registerEvents(new BarrelPlaceListener(), this);
        Bukkit.getPluginManager().registerEvents(new BarrelPickupListener(), this);
        Bukkit.getPluginManager().registerEvents(new BarrelCanisterInteractListener(), this);
        Bukkit.getPluginManager().registerEvents(new CanisterModeSwitchListener(), this);

        // Parkour
        CheckpointManager checkpointManager = new CheckpointManager(this);
        checkpointManager.loadCheckpoints();

        Bukkit.getPluginManager().registerEvents(new ParkourWorldListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourItemListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourCheckpointListener(parkourManager, checkpointManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourProtectionListener(), this);
        Bukkit.getPluginManager().registerEvents(new ParkourDisconnectListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourRespawnListener(parkourManager), this);

        // Events
        Bukkit.getPluginManager().registerEvents(new EventMenuListener(eventManager), this);
        Bukkit.getPluginManager().registerEvents(new KothChestListener(), this);
        Bukkit.getPluginManager().registerEvents(new BeskarMiningListener(eventManager), this);
        Bukkit.getPluginManager().registerEvents(new JabbaChestListener(eventManager), this);

        // 🔥 JABBA DUNGEON (FIXED)
        Bukkit.getPluginManager().registerEvents(
                new DoorListener(this, eventManager),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new JabbaDungeonMobListener(eventManager),
                this
        );
    }

    private void registerModelEngineControllers() {
        ModelEngineAPI.getMountControllerTypeRegistry().register("aerial_controller", AerialMountController.AERIAL);
        ModelEngineAPI.getMountControllerTypeRegistry().register("surface_controller", SurfaceMountController.SURFACE);
    }

    /*
     * --------------------------------
     * GETTERS
     * --------------------------------
     */

    public static MandoMC getInstance() { return instance; }
    public NamespacedKey getVehicleKey() { return vehicleKey; }
    public ParkourManager getParkourManager() { return parkourManager; }
    public GUIManager getGuiManager() { return guiManager; }
    public EventManager getEventManager() { return eventManager; }
}