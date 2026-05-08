package net.ledok.economy_ld.util;

import net.ledok.economy_ld.manager.EconomyManager;

import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyFormatter {
    private CurrencyFormatter() {
    }

    public static String format(long amount) {
        String symbol = EconomyManager.getInstance().getConfig().currency.symbol;
        return NumberFormat.getIntegerInstance(Locale.US).format(amount) + " " + symbol;
    }
}
