package at.hugo.bukkit.plugin.tresuryprovider;

import me.lokka30.treasury.api.common.misc.TriState;
import me.lokka30.treasury.api.common.response.FailureReason;
import me.lokka30.treasury.api.common.response.Response;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomyFailureReason;
import me.lokka30.treasury.api.economy.transaction.EconomyTransaction;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerAccount implements me.lokka30.treasury.api.economy.account.PlayerAccount {
    private final SimpleTreasuryEconomyPlugin plugin;
    private final UUID uuid;

    public PlayerAccount(SimpleTreasuryEconomyPlugin plugin, UUID uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
    }

    @Override
    public @NotNull UUID getUniqueId() {
        return uuid;
    }

    @Override
    public Optional<String> getName() {
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Response<BigDecimal>> retrieveBalance(@NotNull Currency currency) {
        return CompletableFuture.supplyAsync(() -> {
            BigDecimal bigDecimal = plugin.getDatabase().getBalance(this.getUniqueId(), currency);
            if (bigDecimal != null) return Response.success(bigDecimal);
            if (plugin.getDatabase().setBalance(this.getUniqueId(), currency, currency.getStartingBalance(this.getUniqueId())))
                return Response.success(currency.getStartingBalance(getUniqueId()));
            return Response.failure(EconomyFailureReason.ACCOUNT_NOT_FOUND);
        });
    }

    @Override
    public CompletableFuture<Response<BigDecimal>> doTransaction(@NotNull EconomyTransaction economyTransaction) {
        return CompletableFuture.supplyAsync(() -> {
            switch (economyTransaction.getTransactionType()) {
                case SET: {
                    if (plugin.getDatabase().setBalance(getUniqueId(), plugin.getEconomy().findCurrency(economyTransaction.getCurrencyID()).get(), economyTransaction.getTransactionAmount())) {
                        return Response.success(economyTransaction.getTransactionAmount());
                    }
                    return Response.failure(EconomyFailureReason.REQUEST_CANCELLED);
                }
                case DEPOSIT: {
                    var result = plugin.getDatabase().changeBalance(getUniqueId(), economyTransaction.getCurrencyID(), economyTransaction.getTransactionAmount());
                    return result.map(Response::success).orElseGet(() -> Response.failure(EconomyFailureReason.OTHER_FAILURE));
                }
                case WITHDRAWAL: {
                    var result = plugin.getDatabase().changeBalance(getUniqueId(), economyTransaction.getCurrencyID(), economyTransaction.getTransactionAmount().negate());
                    return result.map(Response::success).orElseGet(() -> Response.failure(EconomyFailureReason.OTHER_FAILURE));
                }
            }
            return Response.failure(FailureReason.of("This should not have happened!"));
        });
    }

    @Override
    public CompletableFuture<Response<TriState>> deleteAccount() {
        return CompletableFuture.supplyAsync(() -> Response.success(TriState.fromBoolean(plugin.getDatabase().deleteAccount(getUniqueId()))));
    }

    @Override
    public CompletableFuture<Response<Collection<String>>> retrieveHeldCurrencies() {
        return CompletableFuture.supplyAsync(() -> Response.success(plugin.getDatabase().getCurrenciesOf(getUniqueId())));
    }

    @Override
    public CompletableFuture<Response<Collection<EconomyTransaction>>> retrieveTransactionHistory(int transactionCount, @NotNull Temporal from, @NotNull Temporal to) {
        return CompletableFuture.completedFuture(Response.failure(EconomyFailureReason.OTHER_FAILURE));
    }
}
