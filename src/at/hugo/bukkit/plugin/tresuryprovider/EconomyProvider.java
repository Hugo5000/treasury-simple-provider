package at.hugo.bukkit.plugin.tresuryprovider;

import me.lokka30.treasury.api.common.misc.TriState;
import me.lokka30.treasury.api.common.response.Response;
import me.lokka30.treasury.api.economy.account.AccountData;
import me.lokka30.treasury.api.economy.account.AccountPermission;
import me.lokka30.treasury.api.economy.account.accessor.AccountAccessor;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomyFailureReason;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EconomyProvider implements me.lokka30.treasury.api.economy.EconomyProvider, AccountAccessor {
    private final SimpleTreasuryEconomyPlugin plugin;
    private final HashMap<String, Currency> currencies;

    public EconomyProvider(SimpleTreasuryEconomyPlugin plugin, HashMap<String, Currency> currencies) {
        this.plugin = plugin;
        this.currencies = currencies;
    }

    @Override
    public @NotNull AccountAccessor accountAccessor() {
        return this;
    }

    @Override
    public @NotNull CompletableFuture<Response<TriState>> hasAccount(AccountData accountData) {
        return CompletableFuture.completedFuture(Response.success(TriState.TRUE));
    }

    @Override
    public CompletableFuture<Response<Collection<UUID>>> retrievePlayerAccountIds() {
        return CompletableFuture.supplyAsync(() -> Response.success(plugin.getDatabase().getPlayerIds()));
    }

    @Override
    public CompletableFuture<Response<Collection<String>>> retrieveAccountIds() {
        return CompletableFuture.supplyAsync(() -> Response.failure(EconomyFailureReason.OTHER_FAILURE));
    }

    @Override
    public CompletableFuture<Response<Collection<String>>> retrieveNonPlayerAccountIds() {
        return CompletableFuture.supplyAsync(() -> Response.success(plugin.getDatabase().getAccountIds()));
    }

    @Override
    public CompletableFuture<Collection<String>> retrieveAllAccountsPlayerIsMemberOf(@NotNull UUID playerId) {
        return me.lokka30.treasury.api.economy.EconomyProvider.super.retrieveAllAccountsPlayerIsMemberOf(playerId);
    }

    @Override
    public CompletableFuture<Collection<String>> retrieveAllAccountsPlayerHasPermission(@NotNull UUID playerId, @NotNull AccountPermission @NotNull ... permissions) {
        return me.lokka30.treasury.api.economy.EconomyProvider.super.retrieveAllAccountsPlayerHasPermission(playerId, permissions);
    }

    @Override
    public @NotNull Currency getPrimaryCurrency() {
        return currencies.values().stream().filter(Currency::isPrimary).findFirst().get();
    }

    @Override
    public Optional<Currency> findCurrency(@NotNull String identifier) {
        return Optional.ofNullable(currencies.get(identifier));
    }

    @Override
    public Set<Currency> getCurrencies() {
        return new HashSet<>(currencies.values());
    }

    @Override
    public @NotNull String getPrimaryCurrencyId() {
        return getPrimaryCurrency().getIdentifier();
    }

    public CompletableFuture<Response<TriState>> unregisterCurrency(@NotNull Currency currency) {
        return unregisterCurrency(currency.getIdentifier());
    }

    public CompletableFuture<Response<TriState>> unregisterCurrency(@NotNull String currency) {
        currencies.remove(currency);
        return CompletableFuture.supplyAsync(() -> {
            plugin.getDatabase().deleteCurrency(currency);
            return Response.success(TriState.TRUE);
        });
    }

    @Override
    public CompletableFuture<Response<TriState>> registerCurrency(@NotNull Currency generalCurrency) {
        final Currency currency;
        if (!(generalCurrency instanceof at.hugo.bukkit.plugin.tresuryprovider.Currency))
            currency = new at.hugo.bukkit.plugin.tresuryprovider.Currency(generalCurrency.getIdentifier(), generalCurrency.getSymbol(), generalCurrency.getDecimal(), null, generalCurrency.getDisplayNameSingular(), generalCurrency.getDisplayNamePlural(), generalCurrency.getPrecision(), generalCurrency.isPrimary());
        else currency = generalCurrency;
        if (currencies.containsKey(currency.getIdentifier()))
            return CompletableFuture.completedFuture(Response.failure(EconomyFailureReason.CURRENCY_ALREADY_REGISTERED));

        return CompletableFuture.supplyAsync(() -> {
            if (plugin.getDatabase().createCurrency(currency.getIdentifier(), currency.getDisplayNameSingular(), currency.getDisplayNamePlural(), currency.getSymbol(), currency.getDecimal(), null, currency.getPrecision(), currency.isPrimary())) {
                if (currency.isPrimary() && !currencies.isEmpty()) {
                    if (getPrimaryCurrency().getIdentifier().equals("temporaryCurrencyWillDeleteItselfOnceANewOneIsCreated")) {
                        unregisterCurrency("temporaryCurrencyWillDeleteItselfOnceANewOneIsCreated").join();
                    }
                    currencies.values().stream().filter(Currency::isPrimary).map(at.hugo.bukkit.plugin.tresuryprovider.Currency.class::cast).forEach(currency1 -> currency1.setPrimary(false));
                }
                currencies.put(currency.getIdentifier(), currency);
                currencies.values().forEach(currency1 -> plugin.getLogger().info(currency1.getIdentifier()+": "+currency1.isPrimary()));
                return Response.success(TriState.TRUE);
            } else {
                return Response.success(TriState.FALSE);
            }
        });
    }

    @Override
    public @NotNull PlayerAccountAccessor player() {
        return new PlayerAccountAccessor(plugin);
    }

    @Override
    public @NotNull NonPlayerAccountAccessor nonPlayer() {
        return new NonPlayerAccountAccessor(plugin);
    }
}
