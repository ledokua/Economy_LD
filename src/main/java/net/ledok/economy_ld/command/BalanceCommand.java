package net.ledok.economy_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BalanceCommand {
    private BalanceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balance")
                .executes(ctx -> executeSelf(ctx.getSource()))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> executeOther(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")
                        ))));
    }

    private static int executeSelf(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use /balance without arguments."));
            return 0;
        }

        EconomyManager.getInstance().getBalance(player.getUUID(), player.getName().getString())
                .whenComplete((balance, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to read your balance."));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal("Your balance: " + CurrencyFormatter.format(balance)), false);
                }));
        return 1;
    }

    private static int executeOther(CommandSourceStack source, String username) {
        EconomyManager.getInstance().getBalanceByUsername(username)
                .whenComplete((balanceOpt, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Failed to read balance for " + username + "."));
                        return;
                    }
                    if (balanceOpt.isEmpty()) {
                        source.sendFailure(Component.literal("Player '" + username + "' has no wallet record."));
                        return;
                    }
                    source.sendSuccess(() -> Component.literal(
                            username + "'s balance: " + CurrencyFormatter.format(balanceOpt.getAsLong())
                    ), false);
                }));
        return 1;
    }
}
