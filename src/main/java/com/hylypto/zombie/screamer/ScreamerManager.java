package com.hylypto.zombie.screamer;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hylypto.zombie.PatrolConfig;
import com.hylypto.zombie.PatrolGroup;
import com.hylypto.zombie.PatrolManager;

public class ScreamerManager {

    private static final System.Logger LOG = System.getLogger(ScreamerManager.class.getName());

    private final PatrolConfig config;
    private final PatrolManager patrolManager;

    public ScreamerManager(PatrolConfig config, PatrolManager patrolManager) {
        this.config = config;
        this.patrolManager = patrolManager;
    }

    /**
     * Called when a patrol group with a screamer enters AGGRO for the first time.
     * Spawns a reinforcement horde heading directly toward the player.
     */
    public void onScream(PatrolGroup group) {
        if (!config.screamerEnabled) return;
        if (group.hasScreamed()) return;

        Vector3d playerPos = group.getLastKnownPlayerPosition();
        if (playerPos == null) {
            LOG.log(System.Logger.Level.WARNING,
                    "Screamer triggered but no player position known for group " + group.getGroupId());
            return;
        }

        LOG.log(System.Logger.Level.INFO,
                "SCREAMER! Patrol " + group.getGroupId() + " — summoning reinforcements ("
                + config.screamerHordeSize + " zombies)");

        // Spawn a reinforcement patrol — no screamer in the reinforcement group
        patrolManager.spawnPatrol(config.screamerHordeSize, false);
    }
}
