package com.hylypto.rts;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages RTS unit selection and command dispatch.
 * Tracks which units each player currently has selected.
 */
public class RtsManager {

    private final Map<UUID, Set<UUID>> playerSelections = new ConcurrentHashMap<>();

    public void selectUnits(UUID playerId, Set<UUID> unitIds) {
        playerSelections.put(playerId, unitIds);
    }

    public Set<UUID> getSelection(UUID playerId) {
        return playerSelections.getOrDefault(playerId, Set.of());
    }

    public void clearSelection(UUID playerId) {
        playerSelections.remove(playerId);
    }

    /**
     * Issue a command to all currently selected units for a player.
     * TODO: Wire to actual unit behavior trees / AI system.
     */
    public void issueCommand(UUID playerId, UnitCommand command) {
        Set<UUID> selected = getSelection(playerId);
        if (selected.isEmpty()) {
            return;
        }
        // Future: dispatch command to each selected unit's behavior tree
        // For now, this is a structural placeholder
    }
}
