package net.mandomc.core.modules.gameplay;

import net.mandomc.MandoMC;
import net.mandomc.core.config.MainConfig;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.gameplay.abilities.config.AbilityDefinitionConfig;
import net.mandomc.gameplay.abilities.listener.AbilityActivationListener;
import net.mandomc.gameplay.abilities.listener.AbilityPersistenceListener;
import net.mandomc.gameplay.abilities.service.AbilityService;
import net.mandomc.gameplay.abilities.storage.AbilitiesRepository;
import net.mandomc.gameplay.abilities.task.AbilityActionBarTask;

/**
 * Lifecycle module for gameplay abilities.
 */
public class AbilitiesModule implements Module {
    private final MandoMC plugin;
    private ListenerRegistrar listenerRegistrar;
    private AbilitiesRepository repository;
    private AbilityService abilityService;
    private AbilityActionBarTask actionBarTask;

    public AbilitiesModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable(ServiceRegistry registry) {
        MainConfig mainConfig = registry.get(MainConfig.class);
        if (mainConfig == null || !mainConfig.isAbilitiesEnabled()) {
            plugin.getLogger().info("Abilities module disabled by config (abilities.enabled=false).");
            return;
        }

        AbilityDefinitionConfig definitionConfig = registry.get(AbilityDefinitionConfig.class);
        listenerRegistrar = new ListenerRegistrar(plugin);

        repository = new AbilitiesRepository(plugin);
        repository.load();
        registry.register(AbilitiesRepository.class, repository);

        abilityService = new AbilityService(plugin, repository, definitionConfig);
        abilityService.start();
        registry.register(AbilityService.class, abilityService);

        actionBarTask = new AbilityActionBarTask(plugin, abilityService);
        actionBarTask.start();

        listenerRegistrar.register(new AbilityActivationListener(abilityService));
        listenerRegistrar.register(new AbilityPersistenceListener(abilityService));
        plugin.getLogger().info("Abilities module enabled.");
    }

    @Override
    public void disable() {
        if (listenerRegistrar != null) {
            listenerRegistrar.unregisterAll();
        }
        if (actionBarTask != null) {
            actionBarTask.stop();
            actionBarTask = null;
        }
        if (abilityService != null) {
            abilityService.shutdown();
            abilityService = null;
        } else if (repository != null) {
            repository.flush();
        }
        plugin.getLogger().info("Abilities module disabled.");
    }
}
