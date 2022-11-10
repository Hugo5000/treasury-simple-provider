package at.hugo.bukkit.plugin.tresuryprovider;

import me.lokka30.treasury.api.common.response.Response;
import me.lokka30.treasury.api.economy.response.EconomyFailureReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Currency implements me.lokka30.treasury.api.economy.currency.Currency {
    private final String identifier;
    private final String symbol;
    private final char decimalCharacter;
    private final Character groupingCharacter;
    private final String displaynameSingular;
    private final String displaynamePlural;
    private final int precision;
    private boolean isPrimary;

    private final DecimalFormat decimalFormat;
    private final DecimalFormatSymbols decimalFormatSymbols;

    public Currency(String identifier, String symbol, char decimalCharacter, Character groupingCharacter, String displaynameSingular, String displaynamePlural, int precision, boolean isPrimary) {
        this.identifier = identifier;
        this.symbol = symbol;
        this.decimalCharacter = decimalCharacter;
        this.groupingCharacter = groupingCharacter;
        this.displaynameSingular = displaynameSingular;
        this.displaynamePlural = displaynamePlural;
        this.precision = precision;
        this.isPrimary = isPrimary;
        this.decimalFormatSymbols = new DecimalFormatSymbols();
        decimalFormatSymbols.setCurrencySymbol(symbol);
        decimalFormatSymbols.setDecimalSeparator(decimalCharacter);
        if(groupingCharacter != null)
            decimalFormatSymbols.setGroupingSeparator(groupingCharacter);
        this.decimalFormat = new DecimalFormat((groupingCharacter == null ? "0.": "#,##0.")+"0".repeat(precision), decimalFormatSymbols);
    }


    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getSymbol() {
        return symbol;
    }

    @Override
    public char getDecimal() {
        return decimalCharacter;
    }

    @Override
    public @NotNull String getDisplayNameSingular() {
        return displaynameSingular;
    }

    @Override
    public @NotNull String getDisplayNamePlural() {
        return displaynamePlural;
    }

    @Override
    public int getPrecision() {
        return precision;
    }

    @Override
    public boolean isPrimary() {
        return isPrimary;
    }
    public void setPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    @Override
    public boolean supportsNegativeBalances() {
        return true;
    }

    @Override
    public CompletableFuture<Response<BigDecimal>> to(me.lokka30.treasury.api.economy.currency.@NotNull Currency currency, @NotNull BigDecimal amount) {
        return CompletableFuture.completedFuture(Response.success(amount));
    }

    @Override
    public CompletableFuture<Response<BigDecimal>> parse(@NotNull String formatted) {
        String unformatted = formatted;
        if(groupingCharacter != null) unformatted = formatted.replace(String.valueOf(groupingCharacter), "");
        unformatted = unformatted.replace(symbol, "").replace(decimalCharacter, '.');
        try{
            return CompletableFuture.completedFuture(Response.success(new BigDecimal(unformatted).setScale(getPrecision(), RoundingMode.DOWN)));
        } catch (NumberFormatException exception) {
            return CompletableFuture.completedFuture(Response.failure(EconomyFailureReason.NUMBER_PARSING_ERROR));
        }
    }

    @Override
    public @NotNull BigDecimal getStartingBalance(@Nullable UUID playerID) {
        return BigDecimal.ZERO;
    }

    @Override
    public @NotNull String format(@NotNull BigDecimal amount, @Nullable Locale locale) {
        return decimalFormat.format(amount);
    }

    @Override
    public @NotNull String format(@NotNull BigDecimal amount, @Nullable Locale locale, int precision) {
        if(precision == getPrecision()) return format(amount, locale);
        final DecimalFormat format;
        if(precision <= 0) format = new DecimalFormat(groupingCharacter != null ? "#,##0" : "0", decimalFormatSymbols);
        else format = new DecimalFormat(groupingCharacter != null ? "#,##0" : "0"+"0".repeat(precision), decimalFormatSymbols);
        return format.format(amount.setScale(precision, RoundingMode.DOWN));
    }
}
