package com.hylypto.rts.component;

import java.util.UUID;

/**
 * ECS component establishing player-to-unit ownership.
 * Attached to entities that a player can control as RTS units.
 */
public class UnitOwnershipComponent {

    private UUID ownerPlayerId;
    private int groupId;

    public UnitOwnershipComponent(UUID ownerPlayerId, int groupId) {
        this.ownerPlayerId = ownerPlayerId;
        this.groupId = groupId;
    }

    public UnitOwnershipComponent(UnitOwnershipComponent other) {
        this.ownerPlayerId = other.ownerPlayerId;
        this.groupId = other.groupId;
    }

    public UUID getOwnerPlayerId() {
        return ownerPlayerId;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }
}
