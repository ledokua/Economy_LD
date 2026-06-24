package net.ledok.economy_ld.integration.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.ledok.economy_ld.EconomyLdMod;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional integration with FTB Quests. Registers a reward type that pays out
 * Economy_LD currency to the player who claims it.
 *
 * <p>This class references FTB Quests types directly, so it must only be loaded
 * when FTB Quests is present on the classpath. {@link EconomyLdMod} guards the
 * call to {@link #register()} behind a mod-loaded check.
 */
public final class FtbQuestsIntegration {
    public static final ResourceLocation CURRENCY_ID =
            ResourceLocation.fromNamespaceAndPath(EconomyLdMod.MOD_ID, "currency");

    /** The registered reward type, populated by {@link #register()}. */
    public static RewardType CURRENCY;

    private FtbQuestsIntegration() {
    }

    public static void register() {
        CURRENCY = RewardTypes.register(CURRENCY_ID, EconomyCurrencyReward::new, () -> Icons.MONEY);
        EconomyLdMod.LOGGER.info("Registered FTB Quests reward type '{}'", CURRENCY_ID);
    }
}
