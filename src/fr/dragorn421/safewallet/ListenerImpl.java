package fr.dragorn421.safewallet;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class ListenerImpl implements Listener
{

	@EventHandler(ignoreCancelled=false,priority=EventPriority.LOWEST)
	public void onPlayerInteract(final PlayerInteractEvent e)
	{
		if(!e.hasItem())
			return;
		if(!(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK))
			return;
		if(SafeWalletPlugin.get().getWalletItem().equals(e.getItem()))
		{
			e.setCancelled(true);
			final Player player = e.getPlayer();
			if(!player.hasPermission(SafeWalletPlugin.get().getUseWalletPermission()))
			{
				SafeWalletPlugin.get().checkWallet(player);
				return;
			}
			final Wallet wallet = SafeWalletPlugin.get().getOrCreateWallet(player.getUniqueId());
			player.openInventory(wallet.getInventory(player.getName()));
		}
		// backup solution for item equality
/*		if(!(wallet.getType() == item.getType()
			&& wallet.getData().getData() == item.getData().getData()
			&& wallet.hasItemMeta() == item.hasItemMeta()))
			return;
		if(wallet.hasItemMeta())
		{
			final ItemMeta walletMeta = wallet.getItemMeta();
			final ItemMeta itemMeta = item.getItemMeta();
			if(!(walletMeta.hasDisplayName() == itemMeta.hasDisplayName()
					&& (walletMeta.hasDisplayName()?walletMeta.getDisplayName().equals(itemMeta.getDisplayName()):true)))
					return;
			if(!(walletMeta.hasLore() == itemMeta.hasLore()
					&& (walletMeta.hasLore()?walletMeta.getLore().equals(itemMeta.getLore()):true)))
					return;
			if(!(walletMeta.hasEnchants() == itemMeta.hasEnchants()
					&& (walletMeta.hasEnchants()?walletMeta.getEnchants().equals(itemMeta.getEnchants()):true)))
					return;
			if(!walletMeta.getItemFlags().equals(itemMeta.getItemFlags()))
				return;
		}//*/
	}

	@EventHandler
	public void onInventoryClose(final InventoryCloseEvent e)
	{
		final Wallet wallet = SafeWalletPlugin.get().getInventoryWallet(e.getInventory());
		if(wallet == null)
			return;
		wallet.onInventoryClose();
	}

	@EventHandler(ignoreCancelled=true,priority=EventPriority.HIGHEST)
	public void onEntityDamage(final EntityDamageEvent e)
	{
		if(e.getEntity().getType() != EntityType.PLAYER)
			return;
		final Player player = (Player) e.getEntity();
		if(player.getHealth() - e.getDamage() > 0)
			return;
		player.getInventory().remove(SafeWalletPlugin.get().getWalletItem());
	}

	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent e)
	{
		SafeWalletPlugin.get().checkWallet(e.getPlayer());
	}

	@EventHandler
	public void onPlayerRespawn(final PlayerRespawnEvent e)
	{
		SafeWalletPlugin.get().checkWallet(e.getPlayer());
	}

	@EventHandler
	public void onInventoryClickEvent(final InventoryClickEvent e)
	{
		if(e.getAction() == InventoryAction.NOTHING)
			return;
		final Wallet wallet = SafeWalletPlugin.get().getInventoryWallet(e.getInventory());
/*	/	System.out.println(" === InventoryClickEvent === ");
		System.out.println("slot type="+e.getSlotType());
		System.out.println("raw slot="+e.getRawSlot());
		System.out.println("slot="+e.getSlot());
		System.out.println("action="+e.getAction());
		System.out.println("getCurrentItem="+e.getCurrentItem());
		System.out.println("getCursor="+e.getCursor());//*/
		if(wallet == null)
			return;
		final boolean inTopInventory = e.getRawSlot() < e.getInventory().getSize();
		if(inTopInventory && (e.getAction() == InventoryAction.HOTBAR_SWAP || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD))
			e.setCancelled(true);
		// if interacting somewhere in the other inventory or moving items
		if((inTopInventory || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)
			// and interacting with forbidden item in any way
			&& ((e.getCurrentItem() != null && !SafeWalletPlugin.get().getItemList().isAllowed(e.getCurrentItem()))
					|| (e.getCursor() != null && !SafeWalletPlugin.get().getItemList().isAllowed(e.getCursor()))))
		{
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onInventoryDrag(final InventoryDragEvent e)
	{
		final Wallet wallet = SafeWalletPlugin.get().getInventoryWallet(e.getInventory());
		if(wallet == null)
			return;
		// if dragging forbidden item and dragging it somewhere in the forbidden inventory
		if(e.getRawSlots().stream().anyMatch(slot -> slot < e.getInventory().getSize())
				&& !SafeWalletPlugin.get().getItemList().isAllowed(e.getOldCursor()))
		{
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerPickupItem(final PlayerPickupItemEvent e)
	{
		if(e.getItem().getItemStack().isSimilar(SafeWalletPlugin.get().getWalletItem()))
		{
			e.setCancelled(true);
			e.getItem().remove();
			SafeWalletPlugin.get().checkWallet(e.getPlayer());
		}
	}

}
