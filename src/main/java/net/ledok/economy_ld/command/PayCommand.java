package net.ledok.economy_ld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletionException;

public final class PayCommand {
    private PayCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> execute(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        LongArgumentType.getLong(ctx, "amount")
                                )))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer target, long amount) {
        ServerPlayer sender;
        try {
            sender = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.translatable("economy_ld.command.pay.players_only"));
            return 0;
        }

        EconomyManager.getInstance()
                .transfer(sender.getUUID(), sender.getName().getString(), target.getUUID(), target.getName().getString(), amount)
                .whenComplete((success, error) -> source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("economy_ld.command.pay.error", errorMessage(error)));
                        return;
                    }
                    if (!success) {
                        source.sendFailure(Component.translatable("economy_ld.command.pay.insufficient_funds"));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable(
                            "economy_ld.command.pay.sent",
                            target.getName().getString(),
                            CurrencyFormatter.format(amount)
                    ), false);
                    target.sendSystemMessage(Component.translatable(
                            "economy_ld.command.pay.received",
                            sender.getName().getString(),
                            CurrencyFormatter.format(amount)
                    ));
                }));
        return 1;
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause().getMessage();
        }
        return error.getMessage();
    }
}
