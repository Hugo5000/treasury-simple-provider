package at.hugo.bukkit.plugin.tresuryprovider;

import me.lokka30.treasury.api.common.response.Response;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerAccountAccessor extends me.lokka30.treasury.api.economy.account.accessor.PlayerAccountAccessor {
    private final SimpleTreasuryEconomyPlugin plugin;

    public PlayerAccountAccessor(SimpleTreasuryEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected @NotNull CompletableFuture<Response<PlayerAccount>> getOrCreate(@NotNull UUID uniqueId) {
        return CompletableFuture.completedFuture(Response.success(new at.hugo.bukkit.plugin.tresuryprovider.PlayerAccount(plugin, uniqueId)));
    }
}
