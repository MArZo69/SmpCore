package fr.smp.core;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Affichage des montants : 1500000 -> "1 500 000". */
public final class Money {

    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    private Money() {
    }

    public static synchronized String format(double amount) {
        return FORMAT.format(amount);
    }
}
