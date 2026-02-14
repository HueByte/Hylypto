package com.hylypto.zombie.blockbreak;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hylypto.zombie.PatrolConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockBreakTracker {

    private static final System.Logger LOG = System.getLogger(BlockBreakTracker.class.getName());

    private final PatrolConfig config;
    private final Map<Long, Integer> blockDamage = new ConcurrentHashMap<>();

    public BlockBreakTracker(PatrolConfig config) {
        this.config = config;
    }

    /**
     * Registers a hit against a block at the given position.
     * If the block has been hit enough times, it will be destroyed.
     *
     * @return true if the block was destroyed
     */
    public boolean hitBlock(int x, int y, int z) {
        if (!config.blockBreakEnabled) return false;

        long key = packPosition(x, y, z);
        int hits = blockDamage.merge(key, 1, Integer::sum);

        if (hits >= config.hitsToBreakDoor) {
            blockDamage.remove(key);
            destroyBlock(x, y, z);
            return true;
        }

        return false;
    }

    private void destroyBlock(int x, int y, int z) {
        World world = Universe.get().getDefaultWorld();
        if (world == null) return;

        world.execute(() -> {
            try {
                BlockType current = world.getBlockType(x, y, z);
                if (current != BlockType.EMPTY) {
                    world.breakBlock(x, y, z, 0);
                    LOG.log(System.Logger.Level.INFO,
                            "Zombies broke block at (" + x + ", " + y + ", " + z + ")");
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Failed to break block: " + e.getMessage(), e);
            }
        });
    }

    public void clear() {
        blockDamage.clear();
    }

    private static long packPosition(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
