package net.mandomc.core.modules.gameplay;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.gameplay.lottery.config.LotteryConfig;
import net.mandomc.gameplay.lottery.task.LotteryBroadcastTask;
import net.mandomc.gameplay.lottery.LotteryHologramManager;
import net.mandomc.gameplay.lottery.task.LotteryScheduler;
import net.mandomc.gameplay.lottery.LotteryStorage;
import net.mandomc.gameplay.lottery.LotteryTopHologramManager;
import net.mandomc.gameplay.lottery.storage.LotteryRepository;

/**
 * Manages the lifecycle of gambling systems.
 *
 * Registers {@link LotteryRepository} in the service registry (consumed by
 * the Phase 10 LotteryService). Existing static LotteryStorage is kept
 * temporarily for backward compatibility.
 */
public class LotteryModule implements Module {

    private final MandoMC plugin;
    private LotteryRepository lotteryRepository;

    public LotteryModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable(ServiceRegistry registry) {
        // New typed repository
        lotteryRepository = new LotteryRepository(plugin);
        lotteryRepository.load();
        registry.register(LotteryRepository.class, lotteryRepository);

        // Legacy static storage (kept until Phase 10 migrates LotteryManager)
        LotteryStorage.setup();
        LotteryStorage.load();

        // Wire typed config to all consumers
        LotteryConfig config = registry.get(LotteryConfig.class);
        LotteryHologramManager.init(config);
        LotteryTopHologramManager.init(config);
        LotteryScheduler.start(config);
        LotteryBroadcastTask.start(config);
        LotteryHologramManager.update();
        LotteryTopHologramManager.update();

    }

    @Override
    public void disable() {
        if (lotteryRepository != null) lotteryRepository.flush();
        LotteryStorage.save();
        LotteryHologramManager.remove();
    }
}
