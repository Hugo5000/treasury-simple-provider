package at.hugo.bukkit.plugin.tresuryprovider;

import me.lokka30.treasury.api.common.response.Response;
import me.lokka30.treasury.api.economy.account.NonPlayerAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class NonPlayerAccountAccessor extends me.lokka30.treasury.api.economy.account.accessor.NonPlayerAccountAccessor {
    private final SimpleTreasuryEconomyPlugin plugin;

    public NonPlayerAccountAccessor(SimpleTreasuryEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected @NotNull CompletableFuture<Response<NonPlayerAccount>> getOrCreate(@NotNull String identifier, @Nullable String name) {
        return CompletableFuture.supplyAsync(()->Response.success(new at.hugo.bukkit.plugin.tresuryprovider.NonPlayerAccount(plugin, identifier)));
    }
}
