package at.hugo.bukkit.plugin.tresuryprovider;

import me.lokka30.treasury.api.common.misc.TriState;
import me.lokka30.treasury.api.economy.account.AccountPermission;
import me.lokka30.treasury.api.economy.currency.Currency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class SQLiteDatabase {
    private final DataSource dataSource;

    /**
     * the plugin who initiated the database, mostly used for logging
     */
    protected final SimpleTreasuryEconomyPlugin plugin;

    /**
     * @param plugin   The plugin that initiates the database, mostly used for logging
     * @param filePath The Path to where the SQLite file should be
     */
    public SQLiteDatabase(@NotNull final SimpleTreasuryEconomyPlugin plugin, @NotNull String filePath) {
        this.plugin = plugin;
        this.dataSource = createDataSource(filePath);
        createTables();
    }


    /**
     * Gets a connection from the DataSource to execute SQL queries on
     * With foreign keys turned on.
     *
     * @return the Database Connection
     * @throws SQLException gets thrown if a database access error occurs
     */
    protected Connection getConnection() throws SQLException {
        var connection = dataSource.getConnection();
        connection.prepareStatement("pragma foreign_keys = ON").executeUpdate();
        return connection;
    }

    private static SQLiteDataSource createDataSource(final @NotNull String filePath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + filePath);
        return dataSource;
    }

    private void createTables() {
        try (var con = getConnection()) {
            try (var s1 = con.prepareStatement("""
                    Create TABLE if not exists currencies (
                     id INTEGER not NULL,
                     string_id varchar(64) NOT NULL,
                     name_singular varchar(64) NOT NULL,
                     name_plural varchar(64) Not NULL,
                     currency_symbol varchar(16) NOT NULL,
                     decimal_symbol char(1) NOT NULL DEFAULT ".",
                     grouping_symbol char(1) DEFAULT NULL,
                     precision INTEGER NOT NULL,
                     is_primary Boolean NOT NULL DEFAULT false,
                     primary key(id) \s
                    );""")) {
                s1.execute();

            }
            try (var s1 = con.prepareStatement("""
                    Create TABLE if not exists player_balances (
                     currency_id INTEGER NOT NULL,
                     player_uuid binary(16) not NULL,
                     balance Binary(128),
                     player_uuid_text CHAR(36) generated always AS (\s
                       SUBSTR(hex(player_uuid),1,8) || '-' || SUBSTR(hex(player_uuid),9,4) || '-' || SUBSTR(hex(player_uuid),13,4) || '-' || SUBSTR(hex(player_uuid),17,4) || '-' || SUBSTR(hex(player_uuid),21,12)
                     ) virtual,
                     primary key(currency_id, player_uuid),
                     FOREIGN KEY (currency_id) REFERENCES currencies(id) ON DELETE CASCADE
                    );""")) {
                s1.execute();

            }
            try (var s1 = con.prepareStatement("""
                    Create TABLE if not exists account_balances (
                     currency_id INTEGER NOT NULL,
                     account_id CHAR(64) not NULL,
                     balance Binary(128),
                     primary key(currency_id, account_id),
                     FOREIGN KEY (currency_id) REFERENCES currencies(id) ON DELETE CASCADE
                    );""")) {
                s1.execute();

            }
            try (var s1 = con.prepareStatement("""
                    Create TABLE if not exists account_permissions (
                     account_id CHAR(64) not NULL,
                     permission int not NULL,
                     player_uuid binary(16) not NULL,
                     player_uuid_text CHAR(36) generated always AS (\s
                       SUBSTR(hex(player_uuid),1,8) || '-' || SUBSTR(hex(player_uuid),9,4) || '-' || SUBSTR(hex(player_uuid),13,4) || '-' || SUBSTR(hex(player_uuid),17,4) || '-' || SUBSTR(hex(player_uuid),21,12)
                     ) virtual,
                     primary key(account_id, player_uuid)
                    );""")) {
                s1.execute();

            }
            try (var s1 = con.prepareStatement("""
                    CREATE TRIGGER IF NOT EXISTS account_balances_delete_permissions_cleanup\s
                     AFTER DELETE ON account_balances
                     FOR EACH ROW
                     WHEN OLD.account_id NOT IN (SELECT account_id from account_balances)
                    BEGIN
                     DELETE FROM account_permissions where account_id = OLD.account_id;
                    END;""")) {
                s1.execute();

            }
            try (var s1 = con.prepareStatement("""
                    CREATE TRIGGER IF NOT EXISTS currencies_primary_update\s
                       AFTER UPDATE OF is_primary ON currencies
                       FOR EACH ROW
                       WHEN NEW.is_primary = true
                    BEGIN
                     UPDATE currencies SET is_primary = false where NEW.id != currencies.id;
                    END;""")) {
                s1.execute();
            }
            try (var s1 = con.prepareStatement("""
                    CREATE TRIGGER IF NOT EXISTS currencies_primary_insert\s
                       AFTER INSERT ON currencies
                       FOR EACH ROW
                       WHEN NEW.is_primary = true
                    BEGIN
                     UPDATE currencies SET is_primary = false where NEW.id != currencies.id;
                    END;""")) {
                s1.execute();
            }
            try (var s1 = con.prepareStatement("""
                    CREATE TRIGGER IF NOT EXISTS currencies_insert\s
                       AFTER INSERT ON currencies
                       FOR EACH ROw
                       WHEN NEW.string_id IS NULL
                    BEGIN
                     UPDATE currencies SET string_id = cast(id as text) WHERE NEW.id == currencies.id;
                    END;""")) {
                s1.execute();
            }
        } catch (
                SQLException e) {
            e.printStackTrace();
        }

    }

    public HashMap<String, Currency> loadCurrencies() {
        final HashMap<String, Currency> result = new HashMap<>();
        try (var con = getConnection();
             var statement = con.prepareStatement("SELECT * FROM currencies;");
             var rs = statement.executeQuery()
        ) {
            while (rs.next()) {
                final String id = rs.getString("string_id");
                final String singularName = rs.getString("name_singular");
                final String pluralName = rs.getString("name_plural");
                final String currencySymbol = rs.getString("currency_symbol");
                final char decimalSymbol = rs.getString("decimal_symbol").charAt(0);
                final String groupingSymbolS = rs.getString("grouping_symbol");
                final Character groupingSymbol;
                if (groupingSymbolS == null) groupingSymbol = null;
                else groupingSymbol = groupingSymbolS.charAt(0);
                final int precision = rs.getInt("precision");
                final boolean isPrimary = rs.getBoolean("is_primary");
                result.put(id, new at.hugo.bukkit.plugin.tresuryprovider.Currency(id, currencySymbol, decimalSymbol, groupingSymbol, singularName, pluralName, precision, isPrimary));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public boolean createCurrency(@Nullable String id, @NotNull String singularName, @NotNull String pluralName, @NotNull String currencySymbol, char decimalSymbol, @Nullable Character groupingSymbol, int precision, boolean isPrimary) {
        try (var con = getConnection();
             var statement = con.prepareStatement("INSERT INTO currencies (string_id, name_singular, name_plural, currency_symbol, decimal_symbol, grouping_symbol, precision, is_primary) VALUES(?,?,?,?,?,?,?,?);")
        ) {
            statement.setString(1, id);
            statement.setString(2, singularName);
            statement.setString(3, pluralName);
            statement.setString(4, currencySymbol);
            statement.setString(5, String.valueOf(decimalSymbol));
            statement.setString(6, groupingSymbol != null ? String.valueOf(groupingSymbol) : null);
            statement.setInt(7, precision);
            statement.setBoolean(8, isPrimary);
            statement.execute();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void deleteCurrency(String identifier) {
        try (var con = getConnection();
             var statement = con.prepareStatement("DELETE FROM currencies WHERE string_id = ?;")
        ) {
            statement.setString(1, identifier);
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean setPrimaryCurrency(String identifier) {
        try (var con = getConnection();
             var statement = con.prepareStatement("UPDATE currencies SET is_primary = true where string_id = ?;")
        ) {
            statement.setString(1, identifier);
            statement.execute();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public BigDecimal getBalance(UUID uuid, me.lokka30.treasury.api.economy.currency.Currency currency) {
        try (var con = getConnection();
             var statement = con.prepareStatement("SELECT balance FROM player_balances join currencies on currencies.id = currency_id where string_id = ? and player_uuid = ?;")
        ) {
            statement.setString(1, currency.getIdentifier());
            statement.setBytes(2, DatabaseUtils.convertUuidToBinary(uuid));
            try (var rs = statement.executeQuery()) {
                if (rs.next())
                    return DatabaseUtils.convertBytesToBigDecimal(rs.getBytes("balance"), currency.getPrecision());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Optional<BigDecimal> changeBalance(UUID uuid, String currencyId, BigDecimal amount) {
        return changeBalance(uuid, plugin.getEconomy().findCurrency(currencyId).get(), amount);
    }

    public Optional<BigDecimal> changeBalance(UUID uuid, me.lokka30.treasury.api.economy.currency.Currency currency, BigDecimal amount) {
        try (var con = getConnection();
             var s1 = con.prepareStatement("begin immediate transaction;");
             var s2 = con.prepareStatement("Select balance, precision from player_balances join currencies on currencies.id = currency_id where player_uuid = ? and string_id = ?;");
             var s3 = con.prepareStatement("INSERT INTO player_balances (currency_id, player_uuid, balance) values ((SELECT id from currencies where string_id = ?),?,?) ON CONFLICT (currency_id, player_uuid) DO UPDATE SET balance = excluded.balance;");
             var s4 = con.prepareStatement("commit;")
        ) {
            byte[] uuidBytes = DatabaseUtils.convertUuidToBinary(uuid);
            s2.setBytes(1, uuidBytes);
            s2.setString(2, currency.getIdentifier());
            s3.setString(1, currency.getIdentifier());
            s3.setBytes(2, uuidBytes);
            s1.execute();
            BigDecimal previousBalance = BigDecimal.ZERO;
            try (var rs = s2.executeQuery()) {
                if (rs.next()) {
                    previousBalance = DatabaseUtils.convertBytesToBigDecimal(rs.getBytes("balance"), currency.getPrecision());
                }
            }
            BigDecimal newBalance = previousBalance.add(amount);
            s3.setBytes(3, DatabaseUtils.convertBigDecimalToBinary(newBalance, currency.getPrecision()));
            s3.execute();
            s4.execute();
            return Optional.of(newBalance);
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public boolean setBalance(UUID uuid, Currency currency, BigDecimal amount) {
        try (var con = getConnection();
             var s1 = con.prepareStatement("INSERT INTO player_balances (currency_id, player_uuid, balance) values ((SELECT id from currencies where string_id = ?),?,?) ON CONFLICT (currency_id, player_uuid) DO UPDATE SET balance = excluded.balance;")
        ) {
            s1.setString(1, currency.getIdentifier());
            s1.setBytes(2, DatabaseUtils.convertUuidToBinary(uuid));
            s1.setBytes(3, DatabaseUtils.convertBigDecimalToBinary(amount, currency.getPrecision()));
            s1.execute();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAccount(UUID uuid) {
        try (var con = getConnection();
             var s1 = con.prepareStatement("DELETE FROM player_balances WHERE player_uuid = ?;")
        ) {
            s1.setBytes(1, DatabaseUtils.convertUuidToBinary(uuid));
            s1.execute();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Collection<String> getCurrenciesOf(UUID uuid) {
        HashSet<String> result = new HashSet<>();
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT string_id from currencies join player_balances on currency_id = currencies.id where player_uuid = ?;")
        ) {
            s1.setBytes(1, DatabaseUtils.convertUuidToBinary(uuid));
            try (var rs = s1.executeQuery()) {
                while (rs.next()) result.add(rs.getString("string_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public BigDecimal getBalance(String id, me.lokka30.treasury.api.economy.currency.Currency currency) {
        try (var con = getConnection();
             var statement = con.prepareStatement("SELECT balance FROM account_balances join currencies on currencies.id = currency_id where string_id = ? and where account_balances.id = ?;")
        ) {
            statement.setString(1, currency.getIdentifier());
            statement.setString(2, id);
            try (var rs = statement.executeQuery()) {
                if (rs.next())
                    return DatabaseUtils.convertBytesToBigDecimal(rs.getBytes("balance"), currency.getPrecision());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Optional<BigDecimal> changeBalance(String id, String currencyId, BigDecimal amount) {
        return changeBalance(id, plugin.getEconomy().findCurrency(currencyId).get(), amount);
    }

    public Optional<BigDecimal> changeBalance(String id, Currency currency, BigDecimal amount) {
        try (var con = getConnection();
             var s1 = con.prepareStatement("begin immediate transaction;");
             var s2 = con.prepareStatement("Select balance from account_balances join currencies on currencies.id = currency_id where account_id = ? and string_id = ?;");
             var s3 = con.prepareStatement("INSERT INTO account_balances (currency_id, account_id, balance) values ((SELECT id from currencies where string_id = ?),?,?) ON CONFLICT (currency_id, account_id) DO UPDATE SET balance = excluded.balance;");
             var s4 = con.prepareStatement("commit;")
        ) {
            s2.setString(1, id);
            s2.setString(2, currency.getIdentifier());
            s3.setString(1, currency.getIdentifier());
            s3.setString(2, id);
            s1.execute();
            BigDecimal previousBalance = BigDecimal.ZERO;
            try (var rs = s2.executeQuery()) {
                if (rs.next()) {
                    previousBalance = DatabaseUtils.convertBytesToBigDecimal(rs.getBytes("balance"), currency.getPrecision());
                }
            }
            BigDecimal newBalance = previousBalance.add(amount);
            s3.setBytes(3, DatabaseUtils.convertBigDecimalToBinary(newBalance, currency.getPrecision()));
            s3.execute();
            s4.execute();
            return Optional.of(newBalance);
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public boolean setBalance(String id, String currencyId, BigDecimal amount) {
        return setBalance(id, plugin.getEconomy().findCurrency(currencyId).get(), amount);
    }

    public boolean setBalance(String id, Currency currency, BigDecimal amount) {
        try (var con = getConnection();
             var s1 = con.prepareStatement("INSERT INTO account_balances (currency_id, account_id, balance) values ((SELECT id from currencies where string_id = ?),?,?) ON CONFLICT (currency_id, account_id) DO UPDATE SET balance = excluded.balance;")
        ) {
            s1.setString(1, currency.getIdentifier());
            s1.setString(2, id);
            s1.setBytes(3, DatabaseUtils.convertBigDecimalToBinary(amount, currency.getPrecision()));
            s1.execute();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAccount(String id) {
        try (var con = getConnection();
             var s1 = con.prepareStatement("DELETE FROM account_balances WHERE account_id = ?;")
        ) {
            s1.setString(1, id);
            s1.execute();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Collection<String> getCurrenciesOf(String id) {
        HashSet<String> result = new HashSet<>();
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT string_id from currencies join account_balances on currency_id = currencies.id where account_id = ?;")
        ) {
            s1.setString(1, id);
            try (var rs = s1.executeQuery()) {
                while (rs.next()) result.add(rs.getString("string_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public Collection<UUID> getMembers(String id) {
        HashSet<UUID> result = new HashSet<>();
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT DISTINCT player_uuid from account_permissions where account_id = ?;")
        ) {
            s1.setString(1, id);
            try (var rs = s1.executeQuery()) {
                while (rs.next()) result.add(DatabaseUtils.convertBytesToUUID(rs.getBytes("string_id")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void setPermission(UUID player, String identifier, boolean value, AccountPermission permission) {
        if (value) {
            try (var con = getConnection();
                 var s1 = con.prepareStatement("INSERT OR IGNORE INTO account_permissions (player_uuid, account_id, permission)  VALUES(?,?,?);")
            ) {
                s1.setBytes(1, DatabaseUtils.convertUuidToBinary(player));
                s1.setString(2, identifier);
                s1.setInt(3, permission.ordinal());
                s1.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            try (var con = getConnection();
                 var s1 = con.prepareStatement("DELETE FROM account_permissions where player_uuid = ? and account_id = ? and permission = ?;")
            ) {
                s1.setBytes(1, DatabaseUtils.convertUuidToBinary(player));
                s1.setString(2, identifier);
                s1.setInt(3, permission.ordinal());
                s1.execute();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public HashMap<AccountPermission, TriState> getPermissions(UUID player, String identifier) {
        HashMap<AccountPermission, TriState> result = new HashMap<>();
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT permission from account_permissions where player_uuid = ? and account_id = ?;")
        ) {
            s1.setBytes(1, DatabaseUtils.convertUuidToBinary(player));
            s1.setString(2, identifier);
            try (var rs = s1.executeQuery()) {
                while (rs.next()) result.put(AccountPermission.values()[rs.getInt("permission")], TriState.TRUE);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public HashMap<UUID, HashMap<AccountPermission, TriState>> getPermissionMap(String identifier) {
        HashMap<UUID, HashMap<AccountPermission, TriState>> result = new HashMap<>();
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT player_uuid, permission from account_permissions where account_id = ?;")
        ) {
            s1.setString(1, identifier);
            try (var rs = s1.executeQuery()) {
                while (rs.next()) {
                    final UUID player = DatabaseUtils.convertBytesToUUID(rs.getBytes("player_uuid"));
                    if (!result.containsKey(player)) result.put(player, new HashMap<>());
                    result.get(player).put(AccountPermission.values()[rs.getInt("permission")], TriState.TRUE);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public boolean hasPermission(UUID player, String identifier, AccountPermission permission) {
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT * from account_permissions where player_uuid = ? and account_id = ? and permission = ?;")
        ) {
            s1.setBytes(1, DatabaseUtils.convertUuidToBinary(player));
            s1.setString(2, identifier);
            s1.setInt(2, permission.ordinal());
            try (var rs = s1.executeQuery()) {
                if (rs.next()) return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Collection<UUID> getPlayerIds() {
        HashSet<UUID> result = new HashSet<>();
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT DISTINCT player_uuid from player_balances;")
        ) {
            try (var rs = s1.executeQuery()) {
                while (rs.next()) {
                    result.add(DatabaseUtils.convertBytesToUUID(rs.getBytes("player_uuid")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
    public Collection<String> getAccountIds() {
        HashSet<String> result = new HashSet<>();
        try (var con = getConnection();
             var s1 = con.prepareStatement("SELECT DISTINCT account_id from account_balances;")
        ) {
            try (var rs = s1.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("account_id"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
}
