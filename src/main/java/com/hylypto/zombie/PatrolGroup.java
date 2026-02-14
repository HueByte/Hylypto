package com.hylypto.zombie;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hylypto.zombie.state.PatrolState;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PatrolGroup {

    private final UUID groupId;
    private final List<Vector3d> waypoints;
    private int currentWaypointIndex = 0;
    private PatrolState currentState = PatrolState.PATROLLING;

    private final Set<Ref<EntityStore>> memberRefs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> memberUUIDs = ConcurrentHashMap.newKeySet();

    // Screamer tracking
    private boolean hasScreamer;
    private UUID screamerUUID;
    private boolean hasScreamed = false;

    // State metadata
    private Vector3d lastKnownPlayerPosition;
    private long stateEnteredAtMillis;
    private long lastTickMillis;

    public PatrolGroup(UUID groupId, List<Vector3d> waypoints, boolean hasScreamer) {
        this.groupId = groupId;
        this.waypoints = waypoints;
        this.hasScreamer = hasScreamer;
        this.stateEnteredAtMillis = System.currentTimeMillis();
        this.lastTickMillis = 0;
    }

    public Vector3d getCurrentWaypoint() {
        if (currentWaypointIndex < waypoints.size()) {
            return waypoints.get(currentWaypointIndex);
        }
        return null;
    }

    public boolean advanceWaypoint() {
        currentWaypointIndex++;
        return currentWaypointIndex < waypoints.size();
    }

    public void addMember(Ref<EntityStore> ref, UUID uuid) {
        memberRefs.add(ref);
        memberUUIDs.add(uuid);
    }

    public void removeMember(UUID uuid) {
        memberUUIDs.remove(uuid);
        memberRefs.removeIf(ref -> !ref.isValid());
    }

    public boolean isEmpty() {
        return memberUUIDs.isEmpty();
    }

    public int size() {
        return memberUUIDs.size();
    }

    // --- State transitions ---

    public void transitionTo(PatrolState newState) {
        this.currentState = newState;
        this.stateEnteredAtMillis = System.currentTimeMillis();
    }

    public long millisInCurrentState() {
        return System.currentTimeMillis() - stateEnteredAtMillis;
    }

    // --- Getters / Setters ---

    public UUID getGroupId() { return groupId; }
    public List<Vector3d> getWaypoints() { return waypoints; }
    public int getCurrentWaypointIndex() { return currentWaypointIndex; }
    public PatrolState getCurrentState() { return currentState; }
    public Set<Ref<EntityStore>> getMemberRefs() { return memberRefs; }
    public Set<UUID> getMemberUUIDs() { return memberUUIDs; }

    public boolean hasScreamer() { return hasScreamer; }
    public UUID getScreamerUUID() { return screamerUUID; }
    public void setScreamerUUID(UUID uuid) { this.screamerUUID = uuid; }
    public boolean hasScreamed() { return hasScreamed; }
    public void setHasScreamed(boolean screamed) { this.hasScreamed = screamed; }

    public Vector3d getLastKnownPlayerPosition() { return lastKnownPlayerPosition; }
    public void setLastKnownPlayerPosition(Vector3d pos) { this.lastKnownPlayerPosition = pos; }

    public long getLastTickMillis() { return lastTickMillis; }
    public void setLastTickMillis(long millis) { this.lastTickMillis = millis; }

}
