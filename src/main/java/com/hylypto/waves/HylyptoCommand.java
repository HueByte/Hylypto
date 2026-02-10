package com.hylypto.waves;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /hylypto command — main entry point for the mod.
 * Subcommands:
 *   spawn [count] — spawns a zombie horde (default 10)
 *   status        — shows alive zombie count
 */
public class HylyptoCommand extends AbstractCommand {

    private final HordeManager hordeManager;
    private final RequiredArg<String> action;
    private final OptionalArg<Integer> countArg;

    public HylyptoCommand(HordeManager hordeManager) {
        super("hylypto", "Hylypto mod commands");
        this.hordeManager = hordeManager;
        this.action = withRequiredArg("action", "spawn or status", ArgTypes.STRING);
        this.countArg = withOptionalArg("count", "number of zombies (default 10)", ArgTypes.INTEGER);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        String subcommand = context.get(action);

        String response = switch (subcommand.toLowerCase()) {
            case "spawn" -> {
                Integer count = context.get(countArg);
                int n = (count != null && count > 0) ? count : 10;
                yield hordeManager.spawnHorde(n);
            }
            case "status" -> "Alive zombies: " + hordeManager.getAliveZombieCount();
            default -> "Unknown action: " + subcommand + ". Use: spawn [count] or status";
        };

        context.sendMessage(Message.raw(response));
        return CompletableFuture.completedFuture(null);
    }
}
