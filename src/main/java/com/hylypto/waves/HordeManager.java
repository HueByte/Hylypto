package com.hylypto.waves;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spawns zombie hordes on demand toward a player's position.
 * Handles lifecycle: spawning, death tracking, and full cleanup
 * on player disconnect or plugin shutdown.
 */
public class HordeManager {

    private static final System.Logger LOG = System.getLogger(HordeManager.class.getName());
    private static final double SPAWN_RADIUS = 8.0;
    private static final int SURFACE_SCAN_RANGE = 60;
    private static final String ZOMBIE_MODEL = "Zombie";

    private final AtomicInteger aliveZombieCount = new AtomicInteger(0);
    private final Set<Ref<EntityStore>> hordeEntityRefs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hordeZombieUUIDs = ConcurrentHashMap.newKeySet();

    /**
     * Spawns a horde of zombies around the first player's position.
     */
    public String spawnHorde(int count) {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            LOG.log(System.Logger.Level.ERROR, "Cannot spawn — no default world available");
            return "No world available.";
        }

        Vector3d playerPos = getPlayerPosition(world);
        LOG.log(System.Logger.Level.INFO,
                "Spawning horde of " + count + " zombies near ("
                + (int) playerPos.x + ", " + (int) playerPos.y + ", " + (int) playerPos.z + ")");

        List<Vector3d> spawnedPositions = new ArrayList<>();

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();

                for (int i = 0; i < count; i++) {
                    Vector3d pos = spawnSingleZombie(store, playerPos, world);
                    if (pos != null) {
                        spawnedPositions.add(pos);
                    }
                }

                logSpawnSummary(spawnedPositions, count);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Failed to spawn horde: " + e.getMessage(), e);
            }
        });

        aliveZombieCount.addAndGet(count);
        return "Spawning " + count + " zombies toward your position.";
    }

    /**
     * Despawns all tracked horde zombies and clears tracking state.
     * Called on player disconnect and plugin shutdown.
     */
    public void despawnAll() {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            clearTrackingState();
            return;
        }

        // Copy refs before clearing — world.execute is async
        List<Ref<EntityStore>> refsToRemove = new ArrayList<>(hordeEntityRefs);
        int count = refsToRemove.size();

        clearTrackingState();

        if (refsToRemove.isEmpty()) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int removed = 0;
            for (Ref<EntityStore> ref : refsToRemove) {
                try {
                    if (ref.isValid()) {
                        store.removeEntity(ref, RemoveReason.REMOVE);
                        removed++;
                    }
                } catch (Exception e) {
                    // Entity may have already been removed — ignore
                }
            }
            LOG.log(System.Logger.Level.INFO,
                    "Despawned " + removed + "/" + count + " horde zombies");
        });
    }

    /**
     * Prunes refs that have become invalid (entity unloaded from chunk, despawned externally, etc.)
     * and adjusts the alive count accordingly. Called periodically from the aggro system tick.
     */
    public void pruneInvalidRefs() {
        Iterator<Ref<EntityStore>> it = hordeEntityRefs.iterator();
        while (it.hasNext()) {
            Ref<EntityStore> ref = it.next();
            if (!ref.isValid()) {
                it.remove();
            }
        }

        // Sync alive count with actual tracked UUIDs
        // (deaths handled by ZombieDeathSystem reduce UUIDs, but unloaded entities
        //  only invalidate refs — we need to reconcile)
        int trackedCount = hordeZombieUUIDs.size();
        int currentCount = aliveZombieCount.get();
        if (currentCount > trackedCount) {
            aliveZombieCount.set(trackedCount);
        }
    }

    /**
     * Full shutdown — despawn all and log.
     */
    public void shutdown() {
        despawnAll();
        LOG.log(System.Logger.Level.INFO, "Horde manager shut down.");
    }

    // --- Spawning ---

    private Vector3d spawnSingleZombie(Store<EntityStore> store, Vector3d center, World world) {
        try {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double radius = SPAWN_RADIUS * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;

            double y = findSurfaceY(world, (int) x, (int) center.y + 30, (int) z);

            Vector3d position = new Vector3d(x, y, z);
            Vector3f rotation = new Vector3f(0, (float) Math.toDegrees(angle), 0);

            var result = NPCPlugin.get().spawnNPC(store, ZOMBIE_MODEL, null, position, rotation);

            if (result != null) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> npcRef = (Ref<EntityStore>) result.first();
                hordeEntityRefs.add(npcRef);

                UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
                if (uuidComp != null) {
                    hordeZombieUUIDs.add(uuidComp.getUuid());
                }

                return position;
            } else {
                LOG.log(System.Logger.Level.ERROR, "NPCPlugin.spawnNPC returned null for Zombie");
                aliveZombieCount.decrementAndGet();
                return null;
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Failed to spawn zombie: " + e.getMessage(), e);
            aliveZombieCount.decrementAndGet();
            return null;
        }
    }

    private void logSpawnSummary(List<Vector3d> positions, int requested) {
        int total = positions.size();
        var sb = new StringBuilder();
        sb.append("Spawned ").append(total).append("/").append(requested).append(" zombies — sample positions: ");

        List<Vector3d> samples;
        if (total <= 3) {
            samples = positions;
        } else {
            samples = new ArrayList<>();
            var rng = ThreadLocalRandom.current();
            var indices = new HashSet<Integer>();
            while (indices.size() < 3) {
                indices.add(rng.nextInt(total));
            }
            for (int idx : indices) {
                samples.add(positions.get(idx));
            }
        }

        for (int i = 0; i < samples.size(); i++) {
            Vector3d p = samples.get(i);
            if (i > 0) sb.append(", ");
            sb.append("(").append((int) p.x).append(", ").append((int) p.y).append(", ").append((int) p.z).append(")");
        }

        LOG.log(System.Logger.Level.INFO, sb.toString());
    }

    private double findSurfaceY(World world, int x, int startY, int z) {
        int minY = startY - SURFACE_SCAN_RANGE;
        for (int y = startY; y > minY; y--) {
            BlockType current = world.getBlockType(x, y, z);
            BlockType below = world.getBlockType(x, y - 1, z);
            if (current == BlockType.EMPTY && below != BlockType.EMPTY) {
                return y;
            }
        }
        return startY;
    }

    private Vector3d getPlayerPosition(World world) {
        try {
            var players = Universe.get().getPlayers();
            if (players != null && !players.isEmpty()) {
                PlayerRef playerRef = players.iterator().next();
                var ref = playerRef.getReference();
                if (ref != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    TransformComponent transform = store.getComponent(
                            ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        return transform.getPosition();
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "Could not get player position, using default");
        }
        return new Vector3d(0, 64, 0);
    }

    private void clearTrackingState() {
        hordeEntityRefs.clear();
        hordeZombieUUIDs.clear();
        aliveZombieCount.set(0);
    }

    // --- Callbacks for ECS systems ---

    public void onZombieKilled(UUID uuid) {
        if (hordeZombieUUIDs.remove(uuid)) {
            int remaining = aliveZombieCount.decrementAndGet();
            LOG.log(System.Logger.Level.INFO, "Zombie killed. Remaining: " + remaining);
        }
    }

    public boolean isHordeZombie(UUID uuid) {
        return hordeZombieUUIDs.contains(uuid);
    }

    public void cleanupZombie(UUID uuid) {
        hordeZombieUUIDs.remove(uuid);
    }

    // --- Accessors ---

    public int getAliveZombieCount() {
        return aliveZombieCount.get();
    }
}
