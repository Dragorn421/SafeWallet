package fr.dragorn421.safewallet;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Wallet
{

	final private UUID owner;

	private int size;
	private ItemStack items[];
	private Inventory inventory;

	public Wallet(final UUID owner, final ItemStack[] items)
	{
		this(owner, items.length);
		this.items = items;
	}

	public Wallet(final UUID owner, final int size)
	{
		this.owner = owner;
		this.size = size;
	}

	public UUID getOwner()
	{
		return this.owner;
	}

	public ItemStack[] getItems()
	{
		if(this.inventory != null)
			this.items = this.inventory.getContents();
		else if(this.items == null)
			this.items = new ItemStack[this.size];
		return this.items;
	}

	public Inventory getInventory(final String ownerName)
	{
		if(this.inventory == null)
		{
			this.inventory = Bukkit.createInventory(null, this.size, SafeWalletPlugin.get().getMessages().format("wallet-title", "player", ownerName));
			if(this.items != null)
				this.inventory.setContents(this.items);
			SafeWalletPlugin.get().setInventoryWallet(this.inventory, this);
		}
		return this.inventory;
	}

	public void onInventoryClose()
	{
		if(this.inventory != null)
		{
			this.items = this.inventory.getContents();
			this.inventory.clear();
			this.inventory = null;
		}
	}

}
