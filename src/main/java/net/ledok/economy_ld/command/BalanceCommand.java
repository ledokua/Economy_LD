package net.ledok.economy_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.ledok.economy_ld.util.PermissionHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BalanceCommand {
    private BalanceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("balance")
                .executes(ctx -> executeSelf(ctx.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> PermissionHelper.check(source, "economy_ld.balance.others", 0))
                        .executes(ctx -> executeOther(
                                ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")
                        ))));
    }

    private static int executeSelf(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.balance.players_only"));
            return 0;
        }

        EconomyManager.getInstance().getBalance(player.getUUID(), player.getName().getString())
                .whenComplete((balance, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("economy_ld.command.balance.error"));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("economy_ld.command.balance.self", CurrencyFormatter.format(balance)), false);
                }));
        return 1;
    }

    private static int executeOther(CommandSourceStack source, ServerPlayer target) {
        EconomyManager.getInstance().getBalance(target.getUUID(), target.getName().getString())
                .whenComplete((balance, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("economy_ld.command.balance.error.other", target.getName().getString()));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.balance.other",
                            target.getName().getString(),
                            CurrencyFormatter.format(balance)
                    ), false);
                }));
        return 1;
    }
}
