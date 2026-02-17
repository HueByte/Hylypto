package com.hylypto.zombie;

import com.hypixel.hytale.builtin.path.path.TransientPath;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.zombie.state.PatrolState;
import com.hylypto.zombie.state.PatrolStateHandler;
import com.hylypto.zombie.state.FormingStateHandler;
import com.hylypto.zombie.state.PatrollingStateHandler;
import com.hylypto.zombie.state.AggroStateHandler;
import com.hylypto.zombie.state.SearchingStateHandler;
import com.hylypto.zombie.state.DespawningStateHandler;
import com.hylypto.zombie.blockbreak.BlockBreakTracker;
import com.hylypto.zombie.screamer.ScreamerManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PatrolManager {

    private static final System.Logger LOG = System.getLogger(PatrolManager.class.getName());
    private static final int SURFACE_SCAN_RANGE = 60;
    private static final String PATROL_ROLE = "Hylypto_Patrol_Zombie";

    private final PatrolConfig config;
    private final ScreamerManager screamerManager;
    private final BlockBreakTracker blockBreakTracker;
    private final Map<UUID, PatrolGroup> activeGroups = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> zombieToGroup = new ConcurrentHashMap<>();
    private final EnumMap<PatrolState, PatrolStateHandler> stateHandlers = new EnumMap<>(PatrolState.class);

    public PatrolManager(PatrolConfig config) {
        this.config = config;
        this.screamerManager = new ScreamerManager(config, this);
        this.blockBreakTracker = new BlockBreakTracker(config);

        stateHandlers.put(PatrolState.FORMING, new FormingStateHandler(config));
        stateHandlers.put(PatrolState.PATROLLING, new PatrollingStateHandler(config));
        stateHandlers.put(PatrolState.AGGRO, new AggroStateHandler(config, screamerManager, blockBreakTracker));
        stateHandlers.put(PatrolState.SEARCHING, new SearchingStateHandler(config));
        stateHandlers.put(PatrolState.DESPAWNING, new DespawningStateHandler(config));
    }

    /**
     * Spawns a patrol group near the player position.
     * Generates a route that passes through the player's area.
     * Zombies spawn within loaded chunks and patrol toward waypoints.
     */
    public String spawnPatrol(int groupSize, boolean includeScreamer) {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            return "No world available.";
        }

        UUID groupId = UUID.randomUUID();

        // All world access (player position, block queries, spawning) must happen inside world.execute()
        world.execute(() -> {
            try {
                Vector3d playerPos = getPlayerPosition(world);
                LOG.log(System.Logger.Level.INFO,
                        "spawnPatrol — player at (" + (int) playerPos.x + ", " + (int) playerPos.y
                        + ", " + (int) playerPos.z + "), groupSize=" + groupSize);

                List<Vector3d> route = generatePatrolRoute(playerPos, world);
                PatrolGroup group = new PatrolGroup(groupId, route, includeScreamer && config.screamerEnabled);

                LOG.log(System.Logger.Level.INFO,
                        "Spawning patrol " + groupId + " — " + groupSize + " zombies, "
                        + route.size() + " waypoints, screamer=" + group.hasScreamer());

                // Spawn near the player (within loaded chunks), first waypoint is the walk-toward target
                Vector3d spawnCenter = generateSpawnPosition(playerPos, world);

                Store<EntityStore> store = world.getEntityStore().getStore();

                for (int i = 0; i < groupSize; i++) {
                    String model = (i == 0 && group.hasScreamer()) ? config.screamerModel : config.zombieModel;
                    spawnPatrolMember(store, group, spawnCenter, world, model, i == 0 && group.hasScreamer());
                }

                LOG.log(System.Logger.Level.INFO,
                        "Patrol " + groupId + " spawned with " + group.size() + " members at ("
                        + (int) spawnCenter.x + ", " + (int) spawnCenter.y + ", " + (int) spawnCenter.z + ")");

                activeGroups.put(groupId, group);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Failed to spawn patrol: " + e.getMessage(), e);
            }
        });

        return "Spawning patrol of " + groupSize + " zombies.";
    }

    /**
     * Generates a spawn position within loaded chunk range of the player,
     * but far enough to be outside direct line of sight.
     */
    private Vector3d generateSpawnPosition(Vector3d playerPos, World world) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle = rng.nextDouble() * 2 * Math.PI;
        // Spawn 30-50 blocks away — within loaded chunks but out of easy view
        double dist = 30.0 + rng.nextDouble() * 20.0;
        double x = playerPos.x + Math.cos(angle) * dist;
        double z = playerPos.z + Math.sin(angle) * dist;
        double y = findSurfaceY(world, (int) x, (int) playerPos.y + 30, (int) z);
        return new Vector3d(x, y, z);
    }

    private void spawnPatrolMember(Store<EntityStore> store, PatrolGroup group,
                                    Vector3d center, World world, String model, boolean isScreamer) {
        try {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double radius = 4.0 * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = findSurfaceY(world, (int) x, (int) center.y + 30, (int) z);

            Vector3d position = new Vector3d(x, y, z);
            Vector3f rotation = new Vector3f(0, (float) Math.toDegrees(angle), 0);

            boolean roleExists = NPCPlugin.get().hasRoleName(PATROL_ROLE);
            LOG.log(System.Logger.Level.INFO,
                    "[SPAWN] Attempting spawnNPC: model=" + model + " role=" + PATROL_ROLE
                    + " roleExists=" + roleExists
                    + " at (" + (int) x + ", " + (int) y + ", " + (int) z + ")");

            // Spawn using our custom config — "Appearance" field in the JSON sets the visual model
            // The 2nd param is the NPC config name (determines both appearance + behavior)
            var result = NPCPlugin.get().spawnNPC(store, PATROL_ROLE, null, position, rotation);

            if (result != null) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> npcRef = (Ref<EntityStore>) result.first();

                UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
                if (uuidComp != null) {
                    UUID uuid = uuidComp.getUuid();
                    group.addMember(npcRef, uuid);
                    zombieToGroup.put(uuid, group.getGroupId());

                    if (isScreamer) {
                        group.setScreamerUUID(uuid);
                    }

                    // Log actual role info for debugging
                    NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
                    if (npcEntity != null) {
                        LOG.log(System.Logger.Level.INFO, "[SPAWN] NPC role info: roleName="
                                + npcEntity.getRoleName() + " roleIndex=" + npcEntity.getRoleIndex()
                                + " hasRole=" + (npcEntity.getRole() != null)
                                + " isFollowingPath=" + npcEntity.getPathManager().isFollowingPath());
                    }

                    // Assign patrol waypoints via TransientPath — engine's BodyMotionPath reads this
                    assignPatrolPath(store, npcRef, group.getWaypoints());

                    // Log path status after assignment
                    if (npcEntity != null) {
                        LOG.log(System.Logger.Level.INFO, "[SPAWN] After path assign: isFollowingPath="
                                + npcEntity.getPathManager().isFollowingPath());
                    }

                    LOG.log(System.Logger.Level.INFO, "[SPAWN] Patrol member spawned: uuid=" + uuid
                            + " ref=" + npcRef + " group=" + group.getGroupId()
                            + " waypoints=" + group.getWaypoints().size());
                } else {
                    LOG.log(System.Logger.Level.WARNING, "[SPAWN] spawnNPC result has no UUIDComponent");
                }
            } else {
                LOG.log(System.Logger.Level.ERROR, "[SPAWN] spawnNPC returned null for model=" + model
                        + " role=" + PATROL_ROLE + " — role may not be loaded. "
                        + "Check that IncludesAssetPack=true in manifest.json and role JSON is valid.");
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "[SPAWN] Failed to spawn patrol member: " + e.getMessage(), e);
        }
    }

    /**
     * Assigns a TransientPath with the group's waypoints to the NPC.
     * The engine's BodyMotionPath instruction (from the custom role) reads this path
     * and handles movement natively — no manual bodySteering writes needed.
     */
    private void assignPatrolPath(Store<EntityStore> store, Ref<EntityStore> ref, List<Vector3d> waypoints) {
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null) {
                LOG.log(System.Logger.Level.WARNING, "[SPAWN] Cannot assign path — NPCEntity is null");
                return;
            }

            TransientPath path = new TransientPath();
            for (Vector3d wp : waypoints) {
                path.addWaypoint(wp, new Vector3f(0, 0, 0));
            }

            npc.getPathManager().setTransientPath(path);
            LOG.log(System.Logger.Level.INFO, "[SPAWN] Assigned TransientPath with " + waypoints.size()
                    + " waypoints to NPC ref=" + ref);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "[SPAWN] Failed to assign patrol path: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a patrol route that passes through the player's area.
     * Waypoints stay within reasonable range (loaded chunks).
     * Route: toward player → through player area → away on the other side.
     */
    private List<Vector3d> generatePatrolRoute(Vector3d playerPos, World world) {
        List<Vector3d> waypoints = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double dirAngle = rng.nextDouble() * 2 * Math.PI;

        // First waypoint: near the player (patrol walks toward them)
        double nearDist = 10.0 + rng.nextDouble() * 10.0;
        double perpAngle = dirAngle + Math.PI / 2;
        double lateralOffset = rng.nextDouble(-15, 15);
        double wx = playerPos.x + Math.cos(dirAngle) * nearDist + Math.cos(perpAngle) * lateralOffset;
        double wz = playerPos.z + Math.sin(dirAngle) * nearDist + Math.sin(perpAngle) * lateralOffset;
        double wy = findSurfaceY(world, (int) wx, (int) playerPos.y + 30, (int) wz);
        waypoints.add(new Vector3d(wx, wy, wz));

        // Second waypoint: through the player area (close to player)
        double throughDist = rng.nextDouble(-5, 5);
        lateralOffset = rng.nextDouble(-10, 10);
        wx = playerPos.x + Math.cos(dirAngle) * throughDist + Math.cos(perpAngle) * lateralOffset;
        wz = playerPos.z + Math.sin(dirAngle) * throughDist + Math.sin(perpAngle) * lateralOffset;
        wy = findSurfaceY(world, (int) wx, (int) playerPos.y + 30, (int) wz);
        waypoints.add(new Vector3d(wx, wy, wz));

        // Third waypoint: away from player on the opposite side (still within loaded range)
        double awayDist = 40.0 + rng.nextDouble() * 20.0;
        wx = playerPos.x - Math.cos(dirAngle) * awayDist;
        wz = playerPos.z - Math.sin(dirAngle) * awayDist;
        wy = findSurfaceY(world, (int) wx, (int) playerPos.y + 30, (int) wz);
        waypoints.add(new Vector3d(wx, wy, wz));

        return waypoints;
    }

    // --- State machine dispatch ---

    /**
     * Called by PatrolTickSystem for each patrol zombie.
     * Deduplicates per-group using lastTickMillis.
     */
    private int tickGroupCount = 0;

    public void tickGroup(UUID groupId, Store<EntityStore> store) {
        PatrolGroup group = activeGroups.get(groupId);
        if (group == null) {
            LOG.log(System.Logger.Level.WARNING, "[TICK-GROUP] groupId=" + groupId + " not found in activeGroups");
            return;
        }

        // Dedup — only tick once per cycle
        long now = System.currentTimeMillis();
        if (now - group.getLastTickMillis() < 500) return;
        group.setLastTickMillis(now);

        tickGroupCount++;

        PatrolState currentState = group.getCurrentState();
        PatrolStateHandler handler = stateHandlers.get(currentState);
        if (handler == null) {
            LOG.log(System.Logger.Level.WARNING, "[TICK-GROUP] No handler for state " + currentState);
            return;
        }

        if (tickGroupCount % 5 == 0) {
            LOG.log(System.Logger.Level.INFO, "[TICK-GROUP] group=" + groupId
                    + " state=" + currentState + " members=" + group.size()
                    + " tick#=" + tickGroupCount);
        }

        PatrolState newState = handler.tick(group, store);

        if (newState != currentState) {
            LOG.log(System.Logger.Level.INFO, "[TICK-GROUP] group=" + groupId
                    + " STATE TRANSITION: " + currentState + " -> " + newState);
            handler.onExit(group, store);
            group.transitionTo(newState);

            PatrolStateHandler newHandler = stateHandlers.get(newState);
            if (newHandler != null) {
                newHandler.onEnter(group, store);
            }

            // If despawning completed (group empty), clean up
            if (newState == PatrolState.DESPAWNING && group.isEmpty()) {
                cleanupGroup(groupId);
            }
        }

        // Also check if despawning state has removed all members
        if (group.getCurrentState() == PatrolState.DESPAWNING && group.isEmpty()) {
            cleanupGroup(groupId);
        }
    }

    // --- Death / cleanup callbacks ---

    public void onZombieDeath(UUID zombieUUID) {
        UUID groupId = zombieToGroup.remove(zombieUUID);
        if (groupId == null) return;

        PatrolGroup group = activeGroups.get(groupId);
        if (group == null) return;

        group.removeMember(zombieUUID);
        LOG.log(System.Logger.Level.DEBUG,
                "Patrol zombie died — group " + groupId + " has " + group.size() + " remaining");

        if (group.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "Patrol group " + groupId + " wiped out");
            cleanupGroup(groupId);
        }
    }

    public boolean isPatrolZombie(UUID uuid) {
        return zombieToGroup.containsKey(uuid);
    }

    public UUID getGroupIdForZombie(UUID uuid) {
        return zombieToGroup.get(uuid);
    }

    public PatrolGroup getGroup(UUID groupId) {
        return activeGroups.get(groupId);
    }

    private void cleanupGroup(UUID groupId) {
        PatrolGroup group = activeGroups.remove(groupId);
        if (group != null) {
            for (UUID uuid : group.getMemberUUIDs()) {
                zombieToGroup.remove(uuid);
            }
            LOG.log(System.Logger.Level.INFO, "Patrol group " + groupId + " cleaned up");
        }
    }

    /**
     * Despawns all active patrol groups — called on disconnect / shutdown.
     */
    public void despawnAll() {
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            activeGroups.clear();
            zombieToGroup.clear();
            return;
        }

        List<PatrolGroup> groups = new ArrayList<>(activeGroups.values());
        activeGroups.clear();
        zombieToGroup.clear();

        if (groups.isEmpty()) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int removed = 0;
            for (PatrolGroup group : groups) {
                for (Ref<EntityStore> ref : group.getMemberRefs()) {
                    try {
                        if (ref.isValid()) {
                            store.removeEntity(ref, RemoveReason.REMOVE);
                            removed++;
                        }
                    } catch (Exception e) {
                        // Already removed
                    }
                }
            }
            LOG.log(System.Logger.Level.INFO, "Despawned " + removed + " patrol zombies across " + groups.size() + " groups");
        });
    }

    public void shutdown() {
        despawnAll();
        LOG.log(System.Logger.Level.INFO, "PatrolManager shut down.");
    }

    public int getActiveGroupCount() {
        return activeGroups.size();
    }

    public int getTotalPatrolZombies() {
        return zombieToGroup.size();
    }

    public ScreamerManager getScreamerManager() {
        return screamerManager;
    }

    public BlockBreakTracker getBlockBreakTracker() {
        return blockBreakTracker;
    }

    /**
     * Draws debug shapes for all active patrol groups:
     * - Green spheres at waypoints
     * - Yellow arrows connecting waypoints
     * - Red sphere at each zombie's current position
     * - Blue arrow from each zombie toward current waypoint
     */
    public String debugDrawPaths() {
        World world = Universe.get().getDefaultWorld();
        if (world == null) return "No world available.";
        if (activeGroups.isEmpty()) return "No active patrol groups.";

        Vector3f green = new Vector3f(0f, 1f, 0f);
        Vector3f yellow = new Vector3f(1f, 1f, 0f);
        Vector3f red = new Vector3f(1f, 0f, 0f);
        Vector3f blue = new Vector3f(0.2f, 0.5f, 1f);
        float duration = 10.0f;

        int groupCount = activeGroups.size();

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();

            for (PatrolGroup group : activeGroups.values()) {
                List<Vector3d> waypoints = group.getWaypoints();
                int wpIdx = group.getCurrentWaypointIndex();

                // Draw all waypoints as spheres (green = future, yellow = current)
                for (int i = 0; i < waypoints.size(); i++) {
                    Vector3d wp = waypoints.get(i);
                    Vector3f color = (i == wpIdx) ? yellow : green;
                    double radius = (i == wpIdx) ? 1.0 : 0.5;
                    DebugUtils.addSphere(world, wp, color, radius, duration);
                }

                // Draw arrows between consecutive waypoints
                for (int i = 0; i < waypoints.size() - 1; i++) {
                    DebugUtils.addArrow(world, waypoints.get(i), waypoints.get(i + 1), yellow, duration, false);
                }

                // Draw each zombie's position and direction to current waypoint
                Vector3d currentWp = group.getCurrentWaypoint();
                for (Ref<EntityStore> ref : group.getMemberRefs()) {
                    if (!ref.isValid()) continue;
                    TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform == null) continue;

                    Vector3d pos = transform.getPosition();
                    DebugUtils.addSphere(world, pos, red, 0.4, duration);

                    if (currentWp != null) {
                        DebugUtils.addArrow(world, pos, currentWp, blue, duration, false);
                    }
                }
            }
        });

        return "Drawing paths for " + groupCount + " patrol group(s) — visible for " + (int) duration + "s.";
    }

    // --- Terrain helpers ---

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
}
