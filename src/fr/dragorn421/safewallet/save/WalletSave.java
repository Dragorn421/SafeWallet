package fr.dragorn421.safewallet.save;

import java.util.Collection;
import java.util.UUID;

import fr.dragorn421.safewallet.Wallet;

public abstract class WalletSave
{

	final private WalletSaveType type;
	final private boolean canRunAsync;

	public WalletSave(final WalletSaveType type, final boolean canRunAsync)
	{
		this.type = type;
		this.canRunAsync = canRunAsync;
	}

	abstract public Wallet load(final UUID owner);

	abstract public void save(final Wallet wallet);

	abstract public void saveAll(final Collection<Wallet> wallets);

	final public WalletSaveType getType()
	{
		return this.type;
	}

	final public boolean getCanRunAsync()
	{
		return this.canRunAsync;
	}

}
