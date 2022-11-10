package at.hugo.bukkit.plugin.tresuryprovider;

import me.lokka30.treasury.api.common.misc.TriState;
import me.lokka30.treasury.api.common.response.FailureReason;
import me.lokka30.treasury.api.common.response.Response;
import me.lokka30.treasury.api.economy.account.AccountPermission;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomyFailureReason;
import me.lokka30.treasury.api.economy.transaction.EconomyTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class NonPlayerAccount implements me.lokka30.treasury.api.economy.account.NonPlayerAccount {
    private final SimpleTreasuryEconomyPlugin plugin;
    private final String identifier;

    public NonPlayerAccount(SimpleTreasuryEconomyPlugin plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }


    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public Optional<String> getName() {
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Response<TriState>> setName(@Nullable String name) {
        return CompletableFuture.completedFuture(Response.success(TriState.FALSE));
    }

    @Override
    public CompletableFuture<Response<BigDecimal>> retrieveBalance(@NotNull Currency currency) {
        return CompletableFuture.supplyAsync(() -> {
            BigDecimal bigDecimal = plugin.getDatabase().getBalance(this.getIdentifier(), currency);
            if (bigDecimal != null) return Response.success(bigDecimal);
            if (plugin.getDatabase().setBalance(this.getIdentifier(), currency, BigDecimal.ZERO))
                return Response.success(BigDecimal.ZERO);
            return Response.failure(EconomyFailureReason.ACCOUNT_NOT_FOUND);
        });
    }

    @Override
    public CompletableFuture<Response<BigDecimal>> doTransaction(@NotNull EconomyTransaction economyTransaction) {
        switch (economyTransaction.getTransactionType()) {
            case SET:
                return CompletableFuture.supplyAsync(() -> {
                    if (plugin.getDatabase().setBalance(getIdentifier(), economyTransaction.getCurrencyID(), economyTransaction.getTransactionAmount())) {
                        return Response.success(economyTransaction.getTransactionAmount());
                    }
                    return Response.failure(EconomyFailureReason.OTHER_FAILURE);
                });
            case DEPOSIT:
                return CompletableFuture.supplyAsync(() -> {
                    var result = plugin.getDatabase().changeBalance(getIdentifier(), economyTransaction.getCurrencyID(), economyTransaction.getTransactionAmount());
                    return result.map(Response::success).orElseGet(() -> Response.failure(EconomyFailureReason.OTHER_FAILURE));
                });
            case WITHDRAWAL:
                return CompletableFuture.supplyAsync(() -> {
                    var result = plugin.getDatabase().changeBalance(getIdentifier(), economyTransaction.getCurrencyID(), economyTransaction.getTransactionAmount().negate());
                    return result.map(Response::success).orElseGet(() -> Response.failure(EconomyFailureReason.OTHER_FAILURE));
                });
        }
        return CompletableFuture.completedFuture(Response.failure(FailureReason.of("shouldn't have happened")));
    }

    @Override
    public CompletableFuture<Response<TriState>> deleteAccount() {
        return CompletableFuture.supplyAsync(() -> Response.success(TriState.fromBoolean(plugin.getDatabase().deleteAccount(getIdentifier()))));
    }

    @Override
    public CompletableFuture<Response<Collection<String>>> retrieveHeldCurrencies() {
        return CompletableFuture.supplyAsync(() -> Response.success(plugin.getDatabase().getCurrenciesOf(getIdentifier())));
    }

    @Override
    public CompletableFuture<Response<Collection<EconomyTransaction>>> retrieveTransactionHistory(int transactionCount, @NotNull Temporal from, @NotNull Temporal to) {
        return CompletableFuture.completedFuture(Response.failure(EconomyFailureReason.OTHER_FAILURE));
    }

    @Override
    public CompletableFuture<Response<Collection<UUID>>> retrieveMemberIds() {
        return CompletableFuture.supplyAsync(() -> Response.success(plugin.getDatabase().getMembers(getIdentifier())));
    }

    @Override
    public CompletableFuture<Response<TriState>> isMember(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> Response.success(TriState.fromBoolean(retrieveMemberIds().join().getResult().contains(player))));
    }

    @Override
    public CompletableFuture<Response<TriState>> setPermission(@NotNull UUID player, @NotNull TriState permissionValue, @NotNull AccountPermission @NotNull ... permissions) {
        return CompletableFuture.supplyAsync(() -> {
            for (AccountPermission permission : permissions) {
                plugin.getDatabase().setPermission(player, getIdentifier(), TriState.TRUE.equals(permissionValue), permission);
            }
            return Response.success(TriState.TRUE);
        });
    }

    @Override
    public CompletableFuture<Response<Map<AccountPermission, TriState>>> retrievePermissions(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> Response.success(plugin.getDatabase().getPermissions(player, getIdentifier())));
    }

    @Override
    public @NotNull CompletableFuture<Response<Map<UUID, Set<Map.Entry<AccountPermission, TriState>>>>> retrievePermissionsMap() {
        return CompletableFuture.supplyAsync(() -> {
            var result = new HashMap<UUID, Set<Map.Entry<AccountPermission, TriState>>>();
            plugin.getDatabase().getPermissionMap(getIdentifier()).forEach((key, value) -> result.put(key, value.entrySet()));
            return Response.success(result);
        });

    }

    @Override
    public CompletableFuture<Response<TriState>> hasPermission(@NotNull UUID player, @NotNull AccountPermission @NotNull ... permissions) {
        return CompletableFuture.supplyAsync(() -> Response.success(TriState.fromBoolean(plugin.getDatabase().getPermissions(player, getIdentifier()).keySet().containsAll(Arrays.asList(permissions)))));
    }
}
