package fr.dragorn421.safewallet.save;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import fr.dragorn421.safewallet.SafeWalletPlugin;
import fr.dragorn421.safewallet.Wallet;
import fr.dragorn421.safewallet.YAMLConfigHandler;

public class YamlWalletSave extends WalletSave
{

	final private YAMLConfigHandler configHandler;

	public YamlWalletSave() throws IOException
	{
		super(WalletSaveType.YAML, true);
		this.configHandler = new YAMLConfigHandler(SafeWalletPlugin.get(), "wallets_save.yml");
	}

	synchronized private void setNoSave(final Wallet wallet)
	{
		final ConfigurationSection conf = this.configHandler.getConfig();
		conf.set(wallet.getOwner().toString(), wallet.getItems());
	}

	@Override
	synchronized public void save(final Wallet wallet)
	{
		this.setNoSave(wallet);
		this.configHandler.save();
	}

	@Override
	synchronized public void saveAll(final Collection<Wallet> wallets)
	{
		wallets.forEach(this::setNoSave);
		this.configHandler.save();
	}

	@Override
	synchronized public Wallet load(final UUID owner)
	{
		final ConfigurationSection conf = this.configHandler.getConfig();
		final Object items = conf.get(owner.toString());
		if(items == null)
			return null;
		final ItemStack[] itemsArray = YamlWalletSave.getArray(items, () -> new ItemStack[0]);
		if(itemsArray == null)
		{
			SafeWalletPlugin.get().getLogger().warning("Unexpected value found as items of uuid " + owner + ": " + items);
			return null;
		}
		final Wallet wallet = new Wallet(owner, itemsArray);
		return wallet;
	}

	@SuppressWarnings("unchecked")
	static public <T> T[] getArray(final Object obj, final Supplier<T[]> arraySupplier)
	{
		if(obj == null)
			return null;
		final T[] array;
		if(obj instanceof List)
			array = ((List<T>) obj).toArray(arraySupplier.get());
		else if(obj instanceof ItemStack[])
			array = (T[]) obj;
		else
			return null;
		return array;
	}

}
