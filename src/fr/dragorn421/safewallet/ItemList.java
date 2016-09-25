package fr.dragorn421.safewallet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

public class ItemList
{

	private boolean blacklist;
	private Set<Material> types;
	private Set<MaterialData> typesWithData;
	private Set<ItemStack> items;

	@SuppressWarnings({ "unchecked", "deprecation" })
	public boolean load(final ConfigurationSection cs)
	{
		boolean modified = false;
		if(!cs.isBoolean("is-blacklist"))
		{
			cs.set("is-blacklist", true);
			modified = true;
		}
		this.blacklist = cs.getBoolean("is-blacklist");
		if(!cs.isList("types"))
		{
			cs.set("types", Arrays.asList(Material.STONE.name()));
			modified = true;
		}
		this.types = cs.getStringList("types").stream()
				.map(str -> {
					final Material type = Material.getMaterial(str);
					if(type == null)
						SafeWalletPlugin.get().getLogger().warning("Unknown type " + str);
					return type;
				})
				.filter(m -> m != null)
				.collect(Collectors.toSet());
		if(!cs.isList("types-and-data"))
		{
			cs.set("types-and-data", Arrays.asList(Material.WOOL.name() + ":0"));
			modified = true;
		}
		this.typesWithData = cs.getStringList("types-and-data").stream()
				.map(str -> {
					final String[] args = str.split(":", 2);
					if(args.length == 1)
					{
						SafeWalletPlugin.get().getLogger().warning("Invalid type:data format " + str);
						return null;
					}
					final Material type = Material.getMaterial(args[0]);
					if(type == null)
					{
						SafeWalletPlugin.get().getLogger().warning("Unknown type " + args[0]);
						return null;
					}
					final byte data;
					try {
						data = Byte.parseByte(args[1]);
					} catch(final IllegalArgumentException e) {
						SafeWalletPlugin.get().getLogger().warning("Invalid data value " + args[1]);
						return null;
					}
					return new MaterialData(type, data);
				})
				.filter(m -> m != null)
				.collect(Collectors.toSet());
		if(!cs.isList("items"))
		{
			cs.set("items", Arrays.asList(SafeWalletPlugin.get().getWalletItem()));
			modified = true;
		}
		this.items = new HashSet<>((List<ItemStack>) cs.getList("items"));
		return modified;
	}

	public boolean isAllowed(final ItemStack item)
	{
		if(item.getType() == Material.AIR)
			return true;
		if(this.types.contains(item.getType()) || this.typesWithData.contains(item.getData()) || this.items.contains(item))
			return !this.blacklist;
		else
			return this.blacklist;
	}

}
