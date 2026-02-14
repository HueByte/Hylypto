package com.hylypto;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hylypto.zombie.HordeManager;
import com.hylypto.zombie.PatrolManager;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /hylypto command — main entry point for the mod.
 * Subcommands:
 *   spawn [count]   — spawns a zombie horde at player position (default 10)
 *   horde [count]   — alias for spawn
 *   patrol [count]  — spawns a patrol group (default 5, screamer if count > 3)
 *   kill / killall  — kills all mod-spawned zombies (horde + patrol)
 *   status          — shows alive zombie + patrol count
 */
public class HylyptoCommand extends AbstractCommand {

    private final HordeManager hordeManager;
    private final PatrolManager patrolManager;
    private final RequiredArg<String> action;
    private final OptionalArg<Integer> countArg;

    public HylyptoCommand(HordeManager hordeManager, PatrolManager patrolManager) {
        super("hylypto", "Hylypto mod commands");
        this.hordeManager = hordeManager;
        this.patrolManager = patrolManager;
        this.action = withRequiredArg("action", "spawn, patrol, despawn, or status", ArgTypes.STRING);
        this.countArg = withOptionalArg("count", "number of zombies (default varies)", ArgTypes.INTEGER);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        String subcommand = context.get(action);
        Integer count = context.get(countArg);

        String response = switch (subcommand.toLowerCase()) {
            case "spawn", "horde" -> {
                int n = (count != null && count > 0) ? count : 10;
                yield hordeManager.spawnHorde(n);
            }
            case "patrol" -> {
                int n = (count != null && count > 0) ? count : 5;
                boolean screamer = (count != null && count > 3);
                yield patrolManager.spawnPatrol(n, screamer);
            }
            case "kill", "killall", "despawn" -> {
                int hordeCount = hordeManager.getAliveZombieCount();
                int patrolCount = patrolManager.getTotalPatrolZombies();
                patrolManager.despawnAll();
                hordeManager.despawnAll();
                yield "Killed " + (hordeCount + patrolCount) + " zombies ("
                    + hordeCount + " horde, " + patrolCount + " patrol).";
            }
            case "status" -> {
                yield "Horde zombies: " + hordeManager.getAliveZombieCount()
                    + " | Patrol groups: " + patrolManager.getActiveGroupCount()
                    + " (" + patrolManager.getTotalPatrolZombies() + " zombies)";
            }
            default -> "Unknown action: " + subcommand + ". Use: spawn/horde, patrol, kill, or status";
        };

        context.sendMessage(Message.raw(response));
        return CompletableFuture.completedFuture(null);
    }
}
