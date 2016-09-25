package fr.dragorn421.safewallet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import fr.dragorn421.safewallet.save.WalletSave;
import fr.dragorn421.safewallet.save.WalletSaveType;

public class SafeWalletPlugin extends JavaPlugin// implements Listener
{

	static private SafeWalletPlugin instance;

	private YAMLConfigHandler yamlConfigHandler;

	final private Map<UUID, Wallet> wallets = new HashMap<>();
	final private Map<Inventory, Wallet> inventories = new HashMap<>();

	private Messages messages;
	private Permission useWalletPermission;
	private WalletSave walletSave;
	private ItemStack walletItem;
	private int walletSize;
	private ItemList itemList;

	public String databaseUrl;
	public String databaseUser;
	public String databasePassword;

	@Override
	public void onEnable()
	{
		SafeWalletPlugin.instance = this;
		try {
			this.yamlConfigHandler = new YAMLConfigHandler(this);
		} catch (final IOException e) {
			Bukkit.getScheduler().runTaskLater(this, new Runnable() {
				@Override
				public void run()
				{
					Bukkit.getPluginManager().disablePlugin(SafeWalletPlugin.instance);
				}
			}, 0L);
			throw new IllegalStateException("Unable to load configuration.", e);
		}
		this.loadConfig();
		Bukkit.getPluginManager().registerEvents(new ListenerImpl(), this);
		Bukkit.getScheduler().runTaskLater(this, () -> {
			Bukkit.getOnlinePlayers().forEach(this::checkWallet);
		}, 0L);
		super.getLogger().info(super.getName() + " enabled!");
	}

	@Override
	public void onDisable()
	{
		this.saveWallets(null);
		super.getLogger().info(super.getName() + " disabled!");
	}

	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
	{
		switch(command.getName())
		{
		case "getwallet":
			if(sender instanceof Player)
				this.checkWallet((Player) sender);
			else
				sender.sendMessage("Only players can do that.");
			return true;
		case "safewallet":
			if(args.length == 0)
				return false;
			switch(args[0].toLowerCase())
			{
			case "reload":
				this.reloadConfig();
				this.loadConfig();
				sender.sendMessage("Configuration reloaded!");
				return true;
			case "save":
				final long start = System.currentTimeMillis();
				sender.sendMessage("Saving wallets...");
				this.saveWallets(() -> {
					Bukkit.getScheduler().runTask(this, () -> {
						sender.sendMessage("Saved wallets. (took " + (System.currentTimeMillis() - start) + " ms)");
					});
				});
				return true;
			case "setwalletitem":
				if(sender instanceof Player)
				{
					final Player player = (Player) sender;
					final ItemStack item = player.getInventory().getItemInMainHand();
					if(item != null && item.getType() != Material.AIR)
					{
						Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().remove(this.walletItem));
						this.walletItem = item.clone();
						Bukkit.getOnlinePlayers().forEach(this::checkWallet);
						final ItemMeta meta = item.getItemMeta();
						if(meta.hasDisplayName())
							meta.setDisplayName(meta.getDisplayName().replace(ChatColor.COLOR_CHAR, '&'));
						item.setItemMeta(meta);
						this.getConfig().set("wallet-item", item);
						this.saveConfig();
						sender.sendMessage("The wallet item was changed.");
					}
					else
						sender.sendMessage("You must hold an item in your hand.");
				}
				else
					sender.sendMessage("Only players can do that.");
				return true;
			}
			return false;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String label, final String[] args)
	{
		if(args.length != 1)
			return Collections.emptyList();
		final String arg = args[0].toLowerCase();
		return Stream.of("reload", "save", "setwalletitem").filter(str -> str.startsWith(arg)).collect(Collectors.toList());
	}

	@Override
	public FileConfiguration getConfig()
	{
		return this.yamlConfigHandler.getConfig();
	}

	@Override
	public void reloadConfig()
	{
		this.yamlConfigHandler.reloadConfigSilent();
	}

	@Override
	public void saveConfig()
	{
		this.yamlConfigHandler.save();
	}

	private void loadConfig()
	{
		boolean modified = false;
		final ConfigurationSection config = this.getConfig();
		this.messages = new Messages();
		modified = this.messages.fromConfig(config) || modified;
		this.walletItem = config.getItemStack("wallet-item");
		if(this.walletItem == null)
		{
			this.walletItem = new ItemStack(Material.MINECART, 1);
			final ItemMeta itemMeta = Bukkit.getItemFactory().getItemMeta(this.walletItem.getType());
			itemMeta.setDisplayName((ChatColor.GOLD + "Wallet").replace(ChatColor.COLOR_CHAR, '&'));
			itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			this.walletItem.setItemMeta(itemMeta);
			this.walletItem.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
			config.set("wallet-item", this.walletItem.clone());
			modified = true;
		}
		final ItemMeta meta = this.walletItem.getItemMeta();
		if(meta.hasDisplayName())
			meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', meta.getDisplayName()));
		this.walletItem.setItemMeta(meta);
		if(!config.isInt("wallet-size"))
		{
			config.set("wallet-size", 18);
			modified = true;
		}
		this.walletSize = config.getInt("wallet-size");
		if(!config.isConfigurationSection("item-list"))
		{
			config.createSection("item-list");
			modified = true;
		}
		this.itemList = new ItemList();
		modified = this.itemList.load(config.getConfigurationSection("item-list")) || modified;
		// database
		if(!config.isString("database-url"))
		{
			config.set("database-url", "");
			modified = true;
		}
		this.databaseUrl = config.getString("database-url");
		if(!config.isString("database-user"))
		{
			config.set("database-user", "");
			modified = true;
		}
		this.databaseUser = config.getString("database-user");
		if(!config.isString("database-password"))
		{
			config.set("database-password", "");
			modified = true;
		}
		this.databasePassword = config.getString("database-password");
		// wallet save type
		WalletSaveType saveType = WalletSaveType.getType(config.getString("wallet-save"));
		if(saveType == null)
		{
			saveType = WalletSaveType.YAML;
			config.set("wallet-save", saveType.name());
			modified = true;
		}
		// if no wallet save or different new one
		if(this.walletSave == null || this.walletSave.getType() != saveType)
		{
			// if in condition because new one is different
			if(this.walletSave != null)
				this.getLogger().info("Replacing existing wallet save handler with a new one. (" + this.walletSave.getType() + " -> " + saveType.name() + ")");
			final WalletSave newWalletSave = saveType.create();
			if(newWalletSave == null)
				this.getLogger().severe("Could not create the wallet save handler. (" + saveType.name() + ")");
			else
				this.walletSave = newWalletSave;
			if(this.walletSave == null)
				this.getLogger().severe("No wallet save handler is set! Expect some errors.");
		}
		// if new wallet save is the same
		else if(this.walletSave != null)
			this.getLogger().info("Existing wallet save handler is the same as the new type. (" + this.walletSave.getType() + " = " + saveType.name() + ")");
		if(modified)
			this.saveConfig();
	}

	private void saveWallets(final Runnable callback)
	{
		if(this.walletSave.getCanRunAsync() && callback != null)
		{
			final Collection<Wallet> wallets = new ArrayList<>(this.wallets.values());
			this.wallets.clear();
			new Thread(() -> {
				this.walletSave.saveAll(wallets);
				callback.run();
			}, "Wallet Save Thread").start();
		}
		else
		{
			this.walletSave.saveAll(this.wallets.values());
			if(callback != null)
				callback.run();
		}
	}

	public Messages getMessages()
	{
		return this.messages;
	}

	public Permission getUseWalletPermission()
	{
		if(this.useWalletPermission == null)
			this.useWalletPermission = Bukkit.getPluginManager().getPermission("safewallet.usewallet");
		return this.useWalletPermission;
	}

	public ItemStack getWalletItem()
	{
		return this.walletItem;
	}

	public ItemList getItemList()
	{
		return this.itemList;
	}

	public void checkWallet(final Player player)
	{
		if(!player.getInventory().contains(this.walletItem))
		{
			if(player.hasPermission(this.getUseWalletPermission()))
				player.getInventory().addItem(this.walletItem);
		}
		else if(!player.hasPermission(this.getUseWalletPermission()))
				player.getInventory().remove(this.walletItem);
	}

	public Wallet getWallet(final UUID uuid)
	{
		return this.wallets.get(uuid);
	}

	public Wallet getOrCreateWallet(final UUID uuid)
	{
		return this.wallets.computeIfAbsent(uuid, u -> {
			Wallet wallet = this.walletSave.load(uuid);
			if(wallet == null)
				wallet = new Wallet(uuid, this.walletSize);
			return wallet;
		});
	}

	public void setInventoryWallet(final Inventory inventory, final Wallet wallet)
	{
		if(wallet == null)
			this.inventories.remove(inventory);
		else
			this.inventories.put(inventory, wallet);
	}

	public Wallet getInventoryWallet(final Inventory inventory)
	{
		return this.inventories.get(inventory);
	}

	static public SafeWalletPlugin get()
	{
		return SafeWalletPlugin.instance;
	}

}
