package com.hylypto;

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hylypto.api.config.ConfigLoader;
import com.hylypto.api.event.HylyptoEventBus;
import com.hylypto.combat.CombatManager;
import com.hylypto.rts.RtsManager;
import com.hylypto.survival.SurvivalManager;
import com.hylypto.zombie.PatrolConfig;
import com.hylypto.zombie.PatrolManager;
import com.hylypto.zombie.system.PatrolTickSystem;
import com.hylypto.zombie.system.PatrolDeathSystem;
import com.hylypto.zombie.HordeManager;
import com.hylypto.HylyptoCommand;
import com.hylypto.zombie.system.ZombieAggroSystem;
import com.hylypto.zombie.system.ZombieDeathSystem;

import javax.annotation.Nonnull;

/**
 * Main entry point for the Hylypto mod framework.
 */
public class HylyptoPlugin extends JavaPlugin {

    private static HylyptoPlugin instance;

    // Core services
    private HylyptoEventBus eventBus;
    private ConfigLoader configLoader;

    // Pillar managers
    private HordeManager hordeManager;
    private RtsManager rtsManager;
    private SurvivalManager survivalManager;
    private CombatManager combatManager;

    // ECS systems (kept for shutdown cleanup)
    private ZombieAggroSystem aggroSystem;

    public HylyptoPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log("Setting up Hylypto...");

        // Core services
        this.eventBus = new HylyptoEventBus();
        this.configLoader = new ConfigLoader(getDataDirectory());

        // Pillar managers
        this.hordeManager = new HordeManager();
        this.rtsManager = new RtsManager();
        this.combatManager = new CombatManager();

        // Survival — patrol system
        PatrolConfig patrolConfig = configLoader.loadOrDefault(
                "patrol-config.json", PatrolConfig.class, new PatrolConfig());
        PatrolManager patrolManager = new PatrolManager(patrolConfig);
        this.survivalManager = new SurvivalManager(patrolManager);

        // Commands
        getCommandRegistry().registerCommand(new HylyptoCommand(hordeManager, patrolManager));

        // ECS systems — horde aggro + death tracking
        this.aggroSystem = new ZombieAggroSystem(hordeManager);
        ZombieDeathSystem deathSystem = new ZombieDeathSystem(hordeManager);
        deathSystem.setAggroSystem(aggroSystem);
        getEntityStoreRegistry().registerSystem(deathSystem);
        getEntityStoreRegistry().registerSystem(aggroSystem);

        // ECS systems — patrol tick + death tracking
        getEntityStoreRegistry().registerSystem(new PatrolTickSystem(patrolManager, patrolConfig));
        getEntityStoreRegistry().registerSystem(new PatrolDeathSystem(patrolManager));

        // Player disconnect — despawn all zombies when a player leaves
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            getLogger().atInfo().log("Player disconnected — despawning all zombies");
            aggroSystem.clearAll();
            hordeManager.despawnAll();
            patrolManager.despawnAll();
        });

        getLogger().atInfo().log("Hylypto setup complete.");
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("Hylypto started.");
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("Shutting down Hylypto...");

        aggroSystem.clearAll();
        hordeManager.shutdown();
        survivalManager.shutdown();

        getLogger().atInfo().log("Hylypto shutdown complete.");
    }

    // --- Accessors ---

    public static HylyptoPlugin getInstance() {
        return instance;
    }

    public HylyptoEventBus getHylyptoEventBus() {
        return eventBus;
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public HordeManager getHordeManager() {
        return hordeManager;
    }

    public RtsManager getRtsManager() {
        return rtsManager;
    }

    public SurvivalManager getSurvivalManager() {
        return survivalManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }
}
