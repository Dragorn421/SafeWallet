package fr.dragorn421.safewallet.save;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.UUID;

import javax.sql.DataSource;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.sqlite.SQLiteDataSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import fr.dragorn421.safewallet.SafeWalletPlugin;
import fr.dragorn421.safewallet.Wallet;

public class SqlWalletSave extends WalletSave
{

	final private DataSource dataSource;

	public SqlWalletSave(final WalletSaveType type)
	{
		super(type, true);
		final SafeWalletPlugin pl = SafeWalletPlugin.get();
		final String	url = pl.databaseUrl,
						user = pl.databaseUser,
						password = pl.databasePassword;
		System.out.println(url);
		System.out.println(user);
		System.out.println(password);
		DataSource dataSource = null;
		switch(type)
		{
		case MYSQL:
			MysqlDataSource mysqlDS = new MysqlDataSource();
			mysqlDS.setURL(url);
			mysqlDS.setUser(user);
			mysqlDS.setPassword(password);
			dataSource = mysqlDS;
			break;
		case SQLITE:
			SQLiteDataSource sqliteDS = new SQLiteDataSource();
			sqliteDS.setUrl(url);
			dataSource = sqliteDS;
			break;
		default:
			throw new IllegalArgumentException("Can't create a SqlWalletSave with type " + type);
		}
		this.dataSource = dataSource;
		this.createTable(type);
	}

	private void createTable(final WalletSaveType type)
	{
		/*
//SQLITE (works)
CREATE TABLE `wallets` (
`id`	INTEGER PRIMARY KEY AUTOINCREMENT,
`owneruuidmost`	TEXT NOT NULL,
`owneruuidleast`	TEXT NOT NULL,
`items`	TEXT NOT NULL
);
		 */
		String query;
		switch(type)
		{
		case MYSQL://TODO all stuff mysql-related is untested!
			query = "CREATE TABLE wallets (id INT PRIMARY KEY NOT NULL, owneruuidmost bigint(20) unsigned NOT NULL, owneruuidleast bigint(20) unsigned NOT NULL, items TEXT NOT NULL)";
			break;
		case SQLITE:
			query = "CREATE TABLE `wallets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `owneruuidmost` TEXT NOT NULL, `owneruuidleast` TEXT NOT NULL, `items` TEXT NOT NULL)";
			break;
		default:
			return;
		}
		try (final Connection conn = this.dataSource.getConnection()) {
			final ResultSet tables = conn.getMetaData().getTables(null, null, "wallets", null);
			if(tables.next())
				return;
			try (final Statement stmt = conn.createStatement()) {
				stmt.execute(query);
			}
		} catch(final SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	synchronized public void save(final Wallet wallet)
	{
		try (final Connection conn = this.dataSource.getConnection()) {
			final String uuidMost = Long.toUnsignedString(wallet.getOwner().getMostSignificantBits());
			final String uuidLeast = Long.toUnsignedString(wallet.getOwner().getLeastSignificantBits());
			boolean insertRow;
			try (final PreparedStatement prepared = conn.prepareStatement(
					"SELECT COUNT(*) FROM wallets WHERE owneruuidmost = ? AND owneruuidleast = ?")) {
				prepared.setString(1, uuidMost);
				prepared.setString(2, uuidLeast);
				try (final ResultSet results = prepared.executeQuery()) {
					if(results.next())
						insertRow = results.getInt(1) == 0;
					else
						insertRow = true;
				}
			}
			int count;
			if(insertRow)
			{
				try (final PreparedStatement prepared = conn.prepareStatement(
						"INSERT INTO wallets (owneruuidmost,owneruuidleast,items) VALUES (?,?,?)")) {
					prepared.setString(1, uuidMost);
					prepared.setString(2, uuidLeast);
					prepared.setString(3, this.itemsToString(wallet.getItems()));
					count = prepared.executeUpdate();
				}
			}
			else
			{
				try (final PreparedStatement prepared = conn.prepareStatement(
						"UPDATE wallets SET items = ? WHERE owneruuidmost = ? AND owneruuidleast = ?")) {
					prepared.setString(1, this.itemsToString(wallet.getItems()));
					prepared.setString(2, uuidMost);
					prepared.setString(3, uuidLeast);
					count = prepared.executeUpdate();
				}
			}
			if(count != 1)
				SafeWalletPlugin.get().getLogger().warning(
						"The query that was supposed to save wallet of " + wallet.getOwner()
						+ " modified " + count + " row(s) when trying to " + (insertRow?"insert":"update")
						+ " 1 row. Some data may have been lost.");
		} catch(final SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	synchronized public void saveAll(final Collection<Wallet> wallets)
	{
		wallets.forEach(this::save);
	}

	@Override
	synchronized public Wallet load(final UUID owner)
	{
		final ItemStack[] items;
		try (final Connection conn = this.dataSource.getConnection()) {
			final String uuidMost = Long.toUnsignedString(owner.getMostSignificantBits());
			final String uuidLeast = Long.toUnsignedString(owner.getLeastSignificantBits());
			try (final PreparedStatement prepared = conn.prepareStatement(
					"SELECT items FROM wallets WHERE owneruuidmost = ? AND owneruuidleast = ?")) {
				prepared.setString(1, uuidMost);
				prepared.setString(2, uuidLeast);
				try (final ResultSet results = prepared.executeQuery()) {
					if(results.next())
						items = this.itemsFromString(results.getString(1));
					else
						items = null;
				}
			}
		} catch(final SQLException e) {
			e.printStackTrace();
			return null;
		}
		if(items == null)
			return null;
		return new Wallet(owner, items);
	}

	private ItemStack[] itemsFromString(final String string)
	{
		final JsonObject root;
		try {
			root = new JsonParser().parse(string).getAsJsonObject();
		} catch(final JsonSyntaxException e) {
			e.printStackTrace();
			return null;
		}
		final YamlConfiguration conf = new YamlConfiguration();
		try {
			conf.loadFromString(root.get("items").getAsString());
		} catch (final InvalidConfigurationException e) {
			e.printStackTrace();
			return null;
		}
		return YamlWalletSave.getArray(conf.get("items"), () -> new ItemStack[0]);
	}

	private String itemsToString(final ItemStack[] items)
	{
		final YamlConfiguration conf = new YamlConfiguration();
		conf.set("items", items);
		final JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("items", conf.saveToString());
		return root.toString();
	}

}
