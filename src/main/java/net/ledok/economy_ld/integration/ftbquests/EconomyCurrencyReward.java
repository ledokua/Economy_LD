package net.ledok.economy_ld.integration.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.ledok.economy_ld.EconomyLdMod;
import net.ledok.economy_ld.manager.EconomyManager;
import net.ledok.economy_ld.util.CurrencyFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quests reward that grants Economy_LD currency to the claiming player.
 */
public class EconomyCurrencyReward extends Reward {
    private static final long DEFAULT_AMOUNT = 100L;

    private long amount;

    public EconomyCurrencyReward(long id, Quest quest) {
        this(id, quest, DEFAULT_AMOUNT);
    }

    public EconomyCurrencyReward(long id, Quest quest, long amount) {
        super(id, quest);
        this.amount = amount;
    }

    @Override
    public RewardType getType() {
        return FtbQuestsIntegration.CURRENCY;
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putLong("amount", amount);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        amount = nbt.getLong("amount");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarLong(amount);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        amount = buffer.readVarLong();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addLong("amount", amount, v -> amount = v, DEFAULT_AMOUNT, 1L, Long.MAX_VALUE)
                .setNameKey("economy_ld.ftbquests.reward.amount");
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        if (amount <= 0) {
            return;
        }

        String username = player.getGameProfile().getName();
        EconomyManager.getInstance().give(player.getUUID(), username, amount)
                .exceptionally(throwable -> {
                    EconomyLdMod.LOGGER.error("Failed to grant FTB Quests currency reward to {}", username, throwable);
                    return null;
                });

        if (notify) {
            player.sendSystemMessage(Component.translatable(
                    "economy_ld.ftbquests.reward.granted",
                    CurrencyFormatter.format(amount)));
        }
    }

    @Override
    public MutableComponent getAltTitle() {
        return Component.literal(CurrencyFormatter.format(amount)).withStyle(ChatFormatting.GREEN);
    }

    @Override
    public String getButtonText() {
        return CurrencyFormatter.format(amount);
    }
}
