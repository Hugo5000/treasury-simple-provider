package at.hugo.bukkit.plugin.tresuryprovider;

import cloud.commandframework.arguments.standard.*;
import cloud.commandframework.context.CommandContext;
import me.lokka30.treasury.api.common.misc.TriState;
import me.lokka30.treasury.api.common.service.ServicePriority;
import me.lokka30.treasury.api.common.service.ServiceRegistry;
import me.lokka30.treasury.api.economy.transaction.EconomyTransaction;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionImportance;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionInitiator;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;

import static net.kyori.adventure.text.Component.text;

public class SimpleTreasuryEconomyPlugin extends JavaPlugin {
    private final static String commandName = "economyprovider";
    private CommandManager commandManager;

    private SQLiteDatabase database;
    private EconomyProvider economy;

    @Override
    public void onEnable() {
        reloadConfig();
        database = new SQLiteDatabase(this, new File(getDataFolder(), "database.db").getPath());
        getLogger().info("Loading Currencies");
        var currencies = database.loadCurrencies();
        getLogger().info("Loaded Currencies");
        this.economy = new EconomyProvider(this, currencies);
        if (currencies.isEmpty())
            economy.registerCurrency(new Currency("temporaryCurrencyWillDeleteItselfOnceANewOneIsCreated", "temp", '.', null, "temp", "temp", 2, true)).join();
        ServiceRegistry.INSTANCE.registerService(me.lokka30.treasury.api.economy.EconomyProvider.class, economy, "Simple Treasury EconomyProvider by Hugowo", ServicePriority.NORMAL);
        try {
            commandManager = new CommandManager(this, Component.text()
                    .append(text("[", NamedTextColor.DARK_GRAY))
                    .append(text("ERROR", NamedTextColor.GOLD))
                    .append(text("] ", NamedTextColor.DARK_GRAY)).build()
            );
        } catch (InstantiationException e) {
            e.printStackTrace();
            setEnabled(false);
        }
        createCommands();
    }

    @Override
    public void reloadConfig() {
        saveDefaultConfig();
        super.reloadConfig();
    }

    private void createCommands() {
        var builder = commandManager.manager().commandBuilder(commandName);
        commandManager.command(builder.literal("bal")
                .senderType(Player.class)
                .argument(StringArgument.<CommandSender>newBuilder("currency").withSuggestionsProvider((commandContext, s) -> economy.getCurrencies().stream().map(me.lokka30.treasury.api.economy.currency.Currency::getIdentifier).filter(name -> name.toLowerCase().startsWith(s)).toList()).build())
                .handler(commandContext -> {
                    final Currency currency = getCurrency(commandContext);
                    if (currency == null) return;
                    var res1 = economy.player().getOrCreate(((Player) commandContext.getSender()).getUniqueId()).join();
                    var res2 = res1.getResult().retrieveBalance(currency).join();
                    commandContext.getSender().sendMessage("Balance: " + res2.getResult().toPlainString());
                })
        );
        commandManager.command(builder.literal("bal")
                .argument(StringArgument.<CommandSender>newBuilder("currency").withSuggestionsProvider((commandContext, s) -> economy.getCurrencies().stream().map(me.lokka30.treasury.api.economy.currency.Currency::getIdentifier).filter(name -> name.toLowerCase().startsWith(s)).toList()).build())
                .argument(StringArgument.<CommandSender>newBuilder("player").withSuggestionsProvider((commandContext, s) -> Arrays.stream(Bukkit.getServer().getOfflinePlayers()).map(OfflinePlayer::getName).filter(s1 -> s1.toLowerCase().startsWith(s)).toList()))
                .handler(commandContext -> {
                    OfflinePlayer player = getOfflinePlayer(commandContext);
                    if (player == null) return;
                    final Currency currency = getCurrency(commandContext);
                    if (currency == null) return;

                    commandContext.getSender().sendMessage("Getting bal for " + player.getName() + "(" + player.getUniqueId() + ") with currency: " + currency.getIdentifier());
                    var res1 = economy.player().getOrCreate(((Player) commandContext.getSender()).getUniqueId()).join();
                    commandContext.getSender().sendMessage("response 1 recieved");
                    var res2 = res1.getResult().retrieveBalance(currency).join();
                    commandContext.getSender().sendMessage("response 2 recieved");
                    commandContext.getSender().sendMessage("Balance: " + res2.getResult().toPlainString());
                })
        );
        commandManager.command(builder.literal("set")
                .literal("player")
                .argument(StringArgument.<CommandSender>newBuilder("player").withSuggestionsProvider((commandContext, s) -> Arrays.stream(Bukkit.getServer().getOfflinePlayers()).map(OfflinePlayer::getName).filter(s1 -> s1.toLowerCase().startsWith(s)).toList()))
                .argument(DoubleArgument.of("amount"))
                .argument(StringArgument.<CommandSender>newBuilder("currency").withSuggestionsProvider((commandContext, s) -> economy.getCurrencies().stream().map(me.lokka30.treasury.api.economy.currency.Currency::getIdentifier).filter(name -> name.toLowerCase().startsWith(s)).toList()).build())
                .handler(commandContext -> {
                    OfflinePlayer player = getOfflinePlayer(commandContext);
                    if (player == null) return;
                    final Currency currency = getCurrency(commandContext);
                    if (currency == null) return;
                    final double amount = commandContext.get("amount");
                    commandContext.getSender().sendMessage("Setting bal for " + player.getName() + "(" + player.getUniqueId() + ") with currency: " + currency);
                    var res1 = economy.player().withUniqueId(player.getUniqueId()).get().join();
                    var res2 = res1.getResult().doTransaction(
                            EconomyTransaction.newBuilder()
                                    .withCurrency(currency)
                                    .withImportance(EconomyTransactionImportance.NORMAL)
                                    .withTransactionType(EconomyTransactionType.SET)
                                    .withTransactionAmount(BigDecimal.valueOf(amount))
                                    .withInitiator(EconomyTransactionInitiator.createInitiator(commandContext.getSender() instanceof Player ? EconomyTransactionInitiator.Type.PLAYER : EconomyTransactionInitiator.Type.SERVER, (commandContext.getSender() instanceof Player p) ? p.getUniqueId() : commandContext.getSender()))
                                    .build()
                    ).join();
                    commandContext.getSender().sendMessage("New Balance: " + currency.format(res2.getResult(), null));
                })
        );
        commandManager.command(builder.literal("add")
                .literal("player")
                .argument(StringArgument.<CommandSender>newBuilder("player").withSuggestionsProvider((commandContext, s) -> Arrays.stream(Bukkit.getServer().getOfflinePlayers()).map(OfflinePlayer::getName).filter(s1 -> s1.toLowerCase().startsWith(s)).toList()))
                .argument(DoubleArgument.of("amount"))
                .argument(StringArgument.<CommandSender>newBuilder("currency").withSuggestionsProvider((commandContext, s) -> economy.getCurrencies().stream().map(me.lokka30.treasury.api.economy.currency.Currency::getIdentifier).filter(name -> name.toLowerCase().startsWith(s)).toList()).build())
                .handler(commandContext -> {
                    final OfflinePlayer player = getOfflinePlayer(commandContext);
                    if (player == null) return;
                    final Currency currency = getCurrency(commandContext);
                    if (currency == null) return;
                    final double amount = commandContext.get("amount");
                    commandContext.getSender().sendMessage("Adding bal for " + player.getName() + "(" + player.getUniqueId() + ") with currency: " + currency);
                    var res1 = economy.player().withUniqueId(player.getUniqueId()).get().join();
                    var res2 = res1.getResult().doTransaction(
                            EconomyTransaction.newBuilder()
                                    .withCurrency(currency)
                                    .withImportance(EconomyTransactionImportance.NORMAL)
                                    .withTransactionType(amount < 0 ? EconomyTransactionType.WITHDRAWAL : EconomyTransactionType.DEPOSIT)
                                    .withTransactionAmount(BigDecimal.valueOf(amount).abs())
                                    .withInitiator(EconomyTransactionInitiator.createInitiator(commandContext.getSender() instanceof Player ? EconomyTransactionInitiator.Type.PLAYER : EconomyTransactionInitiator.Type.SERVER, (commandContext.getSender() instanceof Player p) ? p.getUniqueId() : commandContext.getSender()))
                                    .build()
                    ).join();
                    commandContext.getSender().sendMessage("New Balance: " + currency.format(res2.getResult(), null));
                })
        );
        commandManager.command(builder.literal("set")
                .literal("account")
                .argument(StringArgument.<CommandSender>newBuilder("account").withSuggestionsProvider((commandContext, s) -> economy.retrieveNonPlayerAccountIds().join().getResult().stream().filter(s1 -> s1.toLowerCase().startsWith(s)).toList()))
                .argument(DoubleArgument.of("amount"))
                .argument(StringArgument.<CommandSender>newBuilder("currency").withSuggestionsProvider((commandContext, s) -> economy.getCurrencies().stream().map(me.lokka30.treasury.api.economy.currency.Currency::getIdentifier).filter(name -> name.toLowerCase().startsWith(s)).toList()).build())
                .handler(commandContext -> {
                    final String accountName = commandContext.get("account");
                    final Currency currency = getCurrency(commandContext);
                    if (currency == null) return;
                    final double amount = commandContext.get("amount");
                    commandContext.getSender().sendMessage("Setting bal for Account " + accountName + " with currency: " + currency);
                    var res1 = economy.nonPlayer().withIdentifier(accountName).get().join();
                    var res2 = res1.getResult().doTransaction(
                            EconomyTransaction.newBuilder()
                                    .withCurrency(currency)
                                    .withImportance(EconomyTransactionImportance.NORMAL)
                                    .withTransactionType(EconomyTransactionType.SET)
                                    .withTransactionAmount(BigDecimal.valueOf(amount))
                                    .withInitiator(EconomyTransactionInitiator.createInitiator(commandContext.getSender() instanceof Player ? EconomyTransactionInitiator.Type.PLAYER : EconomyTransactionInitiator.Type.SERVER, (commandContext.getSender() instanceof Player p) ? p.getUniqueId() : commandContext.getSender()))
                                    .build()
                    ).join();
                    commandContext.getSender().sendMessage("New Balance: " + currency.format(res2.getResult(), null));
                })
        );
        commandManager.command(builder.literal("add")
                .literal("account")
                .argument(StringArgument.<CommandSender>newBuilder("account").withSuggestionsProvider((commandContext, s) -> economy.retrieveNonPlayerAccountIds().join().getResult().stream().filter(s1 -> s1.toLowerCase().startsWith(s)).toList()))
                .argument(DoubleArgument.of("amount"))
                .argument(StringArgument.<CommandSender>newBuilder("currency").withSuggestionsProvider((commandContext, s) -> economy.getCurrencies().stream().map(me.lokka30.treasury.api.economy.currency.Currency::getIdentifier).filter(name -> name.toLowerCase().startsWith(s)).toList()).build())
                .handler(commandContext -> {
                    final String accountName = commandContext.get("account");
                    final Currency currency = getCurrency(commandContext);
                    if (currency == null) return;
                    final double amount = commandContext.get("amount");
                    commandContext.getSender().sendMessage("Adding bal for Account " + accountName + " with currency: " + currency);
                    var res1 = economy.nonPlayer().withIdentifier(accountName).get().join();
                    var res2 = res1.getResult().doTransaction(
                            EconomyTransaction.newBuilder()
                                    .withCurrency(currency)
                                    .withImportance(EconomyTransactionImportance.NORMAL)
                                    .withTransactionType(amount < 0 ? EconomyTransactionType.WITHDRAWAL : EconomyTransactionType.DEPOSIT)
                                    .withTransactionAmount(BigDecimal.valueOf(amount).abs())
                                    .withInitiator(EconomyTransactionInitiator.createInitiator(commandContext.getSender() instanceof Player ? EconomyTransactionInitiator.Type.PLAYER : EconomyTransactionInitiator.Type.SERVER, (commandContext.getSender() instanceof Player p) ? p.getUniqueId() : commandContext.getSender()))
                                    .build()
                    ).join();
                    commandContext.getSender().sendMessage("New Balance: " + currency.format(res2.getResult(), null));
                })
        );
        commandManager.command(builder.literal("add")
                .literal("currency")
                .argument(StringArgument.quoted("identifier"))
                .argument(StringArgument.quoted("symbol"))
                .argument(CharArgument.of("decimal_character"))
                .argument(StringArgument.quoted("singular"))
                .argument(StringArgument.quoted("plural"))
                .argument(IntegerArgument.of("precision"))
                .argument(BooleanArgument.optional("isPrimary", false))
                .argument(CharArgument.optional("grouping", "\0"))
                .handler(commandContext -> {
                    final String identifier = commandContext.get("identifier");
                    final String symbol = commandContext.get("symbol");
                    final char decimal = commandContext.get("decimal_character");
                    final Character grouping = '\0' != (char) commandContext.get("grouping") ? commandContext.get("grouping") : null;
                    final String singular = commandContext.get("singular");
                    final String plural = commandContext.get("plural");
                    final int precision = commandContext.get("precision");
                    final boolean isPrimary = commandContext.get("isPrimary");

                    final Currency currency = new Currency(
                            identifier,
                            symbol,
                            decimal,
                            grouping,
                            singular,
                            plural,
                            precision,
                            isPrimary
                    );
                    economy.registerCurrency(currency).thenAccept(res -> {
                        if (TriState.TRUE.equals(res.getResult())) {
                            commandContext.getSender().sendMessage("Successfully added");
                        } else {
                            commandContext.getSender().sendMessage("Error When added :shrug:");
                        }
                    });
                })
        );
    }

    @Nullable
    private static OfflinePlayer getOfflinePlayer(CommandContext<CommandSender> commandContext) {
        final String playerName = commandContext.get("player");
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(playerName);
        if (player == null) {
            if (commandContext.getSender() instanceof Player p) player = p;
            else {
                commandContext.getSender().sendMessage("No player specified...");
                return null;
            }
        }
        return player;
    }

    @Nullable
    private Currency getCurrency(CommandContext<CommandSender> commandContext) {
        final Currency currency;
        if (!commandContext.contains("currency")) currency = (Currency) economy.getPrimaryCurrency();
        else {
            final String currencyName = commandContext.get("currency");
            var optional = economy.findCurrency(currencyName);
            if (optional.isPresent()) currency = (Currency) optional.get();
            else {
                commandContext.getSender().sendMessage("Wrong currency...");
                return null;
            }
        }
        return currency;
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public EconomyProvider getEconomy() {
        return economy;
    }
}
