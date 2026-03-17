package com.astuteflamez.mandomc;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import com.astuteflamez.mandomc.commands.ReloadCommand;
import com.astuteflamez.mandomc.commands.TestCommand;
import com.astuteflamez.mandomc.features.events.*;
import com.astuteflamez.mandomc.features.events.commands.EventCommand;
import com.astuteflamez.mandomc.features.events.listeners.EventMenuListener;
import com.astuteflamez.mandomc.features.events.types.jabba_dungeon.*;
import com.astuteflamez.mandomc.features.events.types.koth.*;
import com.astuteflamez.mandomc.features.events.types.mining.BeskarMiningListener;
import com.astuteflamez.mandomc.features.items.*;
import com.astuteflamez.mandomc.features.items.commands.*;
import com.astuteflamez.mandomc.features.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.features.items.listeners.*;
import com.astuteflamez.mandomc.features.notes.*;
import com.astuteflamez.mandomc.features.parkour.*;
import com.astuteflamez.mandomc.features.parkour.commands.ParkourFinishCommand;
import com.astuteflamez.mandomc.features.parkour.configs.ParkourConfig;
import com.astuteflamez.mandomc.features.parkour.listeners.*;
import com.astuteflamez.mandomc.features.parkour.managers.*;
import com.astuteflamez.mandomc.features.small_features.fuel.listeners.*;
import com.astuteflamez.mandomc.features.small_features.leaderboards.LeaderboardManager;
import com.astuteflamez.mandomc.features.small_features.lightsabers.listeners.*;
import com.astuteflamez.mandomc.features.small_features.teleportation.TeleportWarmupListener;
import com.astuteflamez.mandomc.features.small_features.warps.*;
import com.astuteflamez.mandomc.features.vehicles.Vehicle;
import com.astuteflamez.mandomc.features.vehicles.listeners.*;
import com.astuteflamez.mandomc.features.vehicles.movement.*;
import com.astuteflamez.mandomc.guis.*;

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

    // 🔥 FIX: store leaderboard manager
    private LeaderboardManager leaderboardManager;

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

        // 🔥 IMPORTANT: initialize AFTER everything else
        setupLeaderboards();

        getLogger().info("§a[MandoMC] Enabled successfully!");
    }

    @Override
    public void onDisable() {

        shutdownEventSystem();

        // 🔥 FIX: properly clean leaderboards
        if (leaderboardManager != null) {
            leaderboardManager.removeAllDisplays();
        }

        if (parkourLeaderboardManager != null) {
            parkourLeaderboardManager.removeAllDisplays();
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

    private void setupLeaderboards() {
        // 🔥 FIX: assign it
        leaderboardManager = new LeaderboardManager(this);
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
        safeCommand("test", new TestCommand(this));
        safeCommand("mmcreload", new ReloadCommand(this));

        NoteCommand note = new NoteCommand(this);
        safeCommand("note", note, note);

        GetCommand get = new GetCommand();
        GiveCommand give = new GiveCommand();
        RecipeCommand recipe = new RecipeCommand();

        safeCommand("get", get, get);
        safeCommand("give", give, give);
        safeCommand("recipes", recipe, recipe);

        safeCommand("parkourfinish", new ParkourFinishCommand(parkourManager));

        EventCommand eventCmd = new EventCommand(eventManager);
        safeCommand("event", eventCmd, eventCmd);

        KeyCommand key = new KeyCommand(this);
        safeCommand("key", key, key);
    }

    // 🔥 cleaner + null safe command registration
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

        Bukkit.getPluginManager().registerEvents(new GUIListener(guiManager), this);

        Bukkit.getPluginManager().registerEvents(new ItemBrowserListener(), this);
        Bukkit.getPluginManager().registerEvents(new RecipeListener(), this);
        Bukkit.getPluginManager().registerEvents(new NoteListener(this), this);

        Bukkit.getPluginManager().registerEvents(new SaberHitListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberThrowListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberToggleListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberDeflectListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberWeaponMechanicsDeflectListener(), this);

        Bukkit.getPluginManager().registerEvents(new SpawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new MountListener(), this);
        Bukkit.getPluginManager().registerEvents(new PickupListener(), this);
        Bukkit.getPluginManager().registerEvents(new VehicleCanisterInteractListener(), this);
        Bukkit.getPluginManager().registerEvents(new DamageListener(), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new RepairListener(), this);
        Bukkit.getPluginManager().registerEvents(new ShootListener(), this);

        Bukkit.getPluginManager().registerEvents(new BarrelPlaceListener(), this);
        Bukkit.getPluginManager().registerEvents(new BarrelPickupListener(), this);
        Bukkit.getPluginManager().registerEvents(new BarrelCanisterInteractListener(), this);
        Bukkit.getPluginManager().registerEvents(new CanisterModeSwitchListener(), this);

        CheckpointManager checkpointManager = new CheckpointManager(this);
        checkpointManager.loadCheckpoints();

        Bukkit.getPluginManager().registerEvents(new ParkourWorldListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourItemListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourCheckpointListener(parkourManager, checkpointManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourProtectionListener(), this);
        Bukkit.getPluginManager().registerEvents(new ParkourDisconnectListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourRespawnListener(parkourManager), this);

        Bukkit.getPluginManager().registerEvents(new EventMenuListener(eventManager), this);
        Bukkit.getPluginManager().registerEvents(new KothChestListener(), this);
        Bukkit.getPluginManager().registerEvents(new DoorListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BeskarMiningListener(eventManager), this);
        Bukkit.getPluginManager().registerEvents(new JabbaChestListener(), this);

        Bukkit.getPluginManager().registerEvents(new TeleportWarmupListener(), this);
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