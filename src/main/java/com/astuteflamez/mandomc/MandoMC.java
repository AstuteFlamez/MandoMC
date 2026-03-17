package com.astuteflamez.mandomc;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import com.astuteflamez.mandomc.commands.ReloadCommand;
import com.astuteflamez.mandomc.commands.TestCommand;
import com.astuteflamez.mandomc.features.events.EventManager;
import com.astuteflamez.mandomc.features.events.EventScheduler;
import com.astuteflamez.mandomc.features.events.commands.EventCommand;
import com.astuteflamez.mandomc.features.events.listeners.EventMenuListener;
import com.astuteflamez.mandomc.features.events.types.jabba_dungeon.DoorListener;
import com.astuteflamez.mandomc.features.events.types.jabba_dungeon.JabbaChestListener;
import com.astuteflamez.mandomc.features.events.types.jabba_dungeon.KeyCommand;
import com.astuteflamez.mandomc.features.events.types.koth.KothChestListener;
import com.astuteflamez.mandomc.features.events.types.koth.KothEvent;
import com.astuteflamez.mandomc.features.events.types.mining.BeskarMiningListener;
import com.astuteflamez.mandomc.features.items.ItemLoader;
import com.astuteflamez.mandomc.features.items.commands.GetCommand;
import com.astuteflamez.mandomc.features.items.commands.GiveCommand;
import com.astuteflamez.mandomc.features.items.commands.RecipeCommand;
import com.astuteflamez.mandomc.features.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.features.items.listeners.ItemBrowserListener;
import com.astuteflamez.mandomc.features.items.listeners.RecipeListener;
import com.astuteflamez.mandomc.features.notes.NoteCommand;
import com.astuteflamez.mandomc.features.notes.NoteListener;
import com.astuteflamez.mandomc.features.parkour.ParkourTimerDisplay;
import com.astuteflamez.mandomc.features.parkour.commands.ParkourFinishCommand;
import com.astuteflamez.mandomc.features.parkour.configs.ParkourConfig;
import com.astuteflamez.mandomc.features.parkour.listeners.ParkourCheckpointListener;
import com.astuteflamez.mandomc.features.parkour.listeners.ParkourDisconnectListener;
import com.astuteflamez.mandomc.features.parkour.listeners.ParkourItemListener;
import com.astuteflamez.mandomc.features.parkour.listeners.ParkourProtectionListener;
import com.astuteflamez.mandomc.features.parkour.listeners.ParkourRespawnListener;
import com.astuteflamez.mandomc.features.parkour.listeners.ParkourWorldListener;
import com.astuteflamez.mandomc.features.parkour.managers.CheckpointManager;
import com.astuteflamez.mandomc.features.parkour.managers.ParkourLeaderboardManager;
import com.astuteflamez.mandomc.features.parkour.managers.ParkourManager;
import com.astuteflamez.mandomc.features.parkour.managers.ParkourTimeManager;
import com.astuteflamez.mandomc.features.small_features.fuel.listeners.BarrelCanisterInteractListener;
import com.astuteflamez.mandomc.features.small_features.fuel.listeners.BarrelPickupListener;
import com.astuteflamez.mandomc.features.small_features.fuel.listeners.BarrelPlaceListener;
import com.astuteflamez.mandomc.features.small_features.fuel.listeners.CanisterModeSwitchListener;
import com.astuteflamez.mandomc.features.small_features.leaderboards.LeaderboardManager;
import com.astuteflamez.mandomc.features.small_features.lightsabers.listeners.SaberDeflectListener;
import com.astuteflamez.mandomc.features.small_features.lightsabers.listeners.SaberHitListener;
import com.astuteflamez.mandomc.features.small_features.lightsabers.listeners.SaberThrowListener;
import com.astuteflamez.mandomc.features.small_features.lightsabers.listeners.SaberToggleListener;
import com.astuteflamez.mandomc.features.small_features.lightsabers.listeners.SaberWeaponMechanicsDeflectListener;
import com.astuteflamez.mandomc.features.small_features.teleportation.TeleportWarmupListener;
import com.astuteflamez.mandomc.features.small_features.warps.WarpCommand;
import com.astuteflamez.mandomc.features.small_features.warps.WarpConfig;
import com.astuteflamez.mandomc.features.vehicles.Vehicle;
import com.astuteflamez.mandomc.features.vehicles.listeners.DamageListener;
import com.astuteflamez.mandomc.features.vehicles.listeners.DeathListener;
import com.astuteflamez.mandomc.features.vehicles.listeners.MountListener;
import com.astuteflamez.mandomc.features.vehicles.listeners.PickupListener;
import com.astuteflamez.mandomc.features.vehicles.listeners.RepairListener;
import com.astuteflamez.mandomc.features.vehicles.listeners.ShootListener;
import com.astuteflamez.mandomc.features.vehicles.listeners.SpawnListener;
import com.astuteflamez.mandomc.features.vehicles.listeners.VehicleCanisterInteractListener;
import com.astuteflamez.mandomc.features.vehicles.movement.AerialMountController;
import com.astuteflamez.mandomc.features.vehicles.movement.SurfaceMountController;
import com.astuteflamez.mandomc.guis.GUIListener;
import com.astuteflamez.mandomc.guis.GUIManager;
import com.ticxo.modelengine.api.ModelEngineAPI;

public final class MandoMC extends JavaPlugin {

    public static MandoMC instance;
    public static final HashMap<UUID, Vehicle> activeVehicles = new HashMap<>();

    private NamespacedKey vehicleKey;

    // GUI
    private GUIManager guiManager;

    // Parkour
    private ParkourManager parkourManager;
    private ParkourTimeManager timeManager;
    private ParkourLeaderboardManager leaderboardManager;
    private ParkourTimerDisplay timerDisplay;

    // Events
    private EventManager eventManager;
    private EventScheduler eventScheduler;

    @Override
    public void onEnable() {
        instance = this;

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

        setupLeaderboards();

        getLogger().info("MandoMC Enabled!");
    }

    @Override
    public void onDisable() {
        shutdownEventSystem();
        getLogger().info("MandoMC Disabled!");
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

    private void setupWarpSystem() {
        // config already handled in setupConfigs()
    }

    private void setupParkourSystem() {
        timeManager = new ParkourTimeManager(this);
        leaderboardManager = new ParkourLeaderboardManager(this, timeManager);
        parkourManager = new ParkourManager(timeManager, leaderboardManager);

        leaderboardManager.updateLeaderboards();
        leaderboardManager.startAutoUpdate();

        timerDisplay = new ParkourTimerDisplay(this, parkourManager, timeManager);
        timerDisplay.start();
    }

    private void setupVehicleSystem() {
        // Vehicle-specific setup can go here later
    }

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
        new LeaderboardManager(this);
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
        registerWarpCommands();
        registerGeneralCommands();
        registerItemCommands();
        registerParkourCommands();
        registerEventCommands();
    }

    private void registerWarpCommands() {
        getCommand("warps").setExecutor(new WarpCommand(guiManager));
    }

    private void registerGeneralCommands() {
        getCommand("test").setExecutor(new TestCommand(this));
        getCommand("mmcreload").setExecutor(new ReloadCommand(this));
        NoteCommand noteCommand = new NoteCommand(this);
        getCommand("note").setExecutor(noteCommand);
        getCommand("note").setTabCompleter(noteCommand);
    }

    private void registerItemCommands() {
        GetCommand getCommand = new GetCommand();
        GiveCommand giveCommand = new GiveCommand();
        RecipeCommand recipeCommand = new RecipeCommand();

        getCommand("get").setExecutor(getCommand);
        getCommand("get").setTabCompleter(getCommand);

        getCommand("give").setExecutor(giveCommand);
        getCommand("give").setTabCompleter(giveCommand);

        getCommand("recipes").setExecutor(recipeCommand);
        getCommand("recipes").setTabCompleter(recipeCommand);
    }

    private void registerParkourCommands() {
        getCommand("parkourfinish").setExecutor(new ParkourFinishCommand(parkourManager));
    }

    private void registerEventCommands() {
        EventCommand eventCommand = new EventCommand(eventManager);
        getCommand("event").setExecutor(eventCommand);
        getCommand("event").setTabCompleter(eventCommand);
        KeyCommand keyCommand = new KeyCommand(this);
        getCommand("key").setExecutor(keyCommand);
        getCommand("key").setTabCompleter(keyCommand);

    }

    /*
     * --------------------------------
     * LISTENERS
     * --------------------------------
     */

    private void registerListeners() {
        registerGUIListeners();
        registerItemListeners();
        registerLightsaberListeners();
        registerVehicleListeners();
        registerFuelListeners();
        registerParkourListeners();
        registerEventListeners();

        Bukkit.getPluginManager().registerEvents(new TeleportWarmupListener(), this);
    }

    private void registerGUIListeners() {
        Bukkit.getPluginManager().registerEvents(new GUIListener(guiManager), this);
    }

    private void registerItemListeners() {
        Bukkit.getPluginManager().registerEvents(new ItemBrowserListener(), this);
        Bukkit.getPluginManager().registerEvents(new RecipeListener(), this);
        getServer().getPluginManager().registerEvents(new NoteListener(this), this);
    }

    private void registerLightsaberListeners() {
        Bukkit.getPluginManager().registerEvents(new SaberHitListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberThrowListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberToggleListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberDeflectListener(), this);
        Bukkit.getPluginManager().registerEvents(new SaberWeaponMechanicsDeflectListener(), this);
    }

    private void registerVehicleListeners() {
        Bukkit.getPluginManager().registerEvents(new SpawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new MountListener(), this);
        Bukkit.getPluginManager().registerEvents(new PickupListener(), this);
        Bukkit.getPluginManager().registerEvents(new VehicleCanisterInteractListener(), this);
        Bukkit.getPluginManager().registerEvents(new DamageListener(), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new RepairListener(), this);
        Bukkit.getPluginManager().registerEvents(new ShootListener(), this);
    }

    private void registerFuelListeners() {
        Bukkit.getPluginManager().registerEvents(new BarrelPlaceListener(), this);
        Bukkit.getPluginManager().registerEvents(new BarrelPickupListener(), this);
        Bukkit.getPluginManager().registerEvents(new BarrelCanisterInteractListener(), this);
        Bukkit.getPluginManager().registerEvents(new CanisterModeSwitchListener(), this);
    }

    private void registerParkourListeners() {
        CheckpointManager checkpointManager = new CheckpointManager(this);
        checkpointManager.loadCheckpoints();

        Bukkit.getPluginManager().registerEvents(new ParkourWorldListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourItemListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourCheckpointListener(parkourManager, checkpointManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourProtectionListener(), this);
        Bukkit.getPluginManager().registerEvents(new ParkourDisconnectListener(parkourManager), this);
        Bukkit.getPluginManager().registerEvents(new ParkourRespawnListener(parkourManager), this);
    }

    private void registerEventListeners() { 
        Bukkit.getPluginManager().registerEvents(new EventMenuListener(eventManager), this);
        Bukkit.getPluginManager().registerEvents(new KothChestListener(), this);
        Bukkit.getPluginManager().registerEvents(new DoorListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BeskarMiningListener(eventManager),this);
        Bukkit.getPluginManager().registerEvents(new JabbaChestListener(), this);
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

    public static MandoMC getInstance() {
        return instance;
    }

    public NamespacedKey getVehicleKey() {
        return vehicleKey;
    }

    public ParkourManager getParkourManager() {
        return parkourManager;
    }

    public ParkourTimeManager getTimeManager() {
        return timeManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }
}