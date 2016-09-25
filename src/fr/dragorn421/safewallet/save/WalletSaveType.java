package fr.dragorn421.safewallet.save;

import java.io.IOException;

public enum WalletSaveType
{

	MYSQL,
	SQLITE,
	YAML;

	public WalletSave create()
	{
		switch(this)
		{
		case MYSQL:
		case SQLITE:
			return new SqlWalletSave(this);
		case YAML:
			try {
				return new YamlWalletSave();
			} catch (final IOException e) {
				e.printStackTrace();
			}
			return null;
		}
		throw new AssertionError();
	}

	public static WalletSaveType getType(final String name)
	{
		if(name == null)
			return null;
		try {
			return WalletSaveType.valueOf(name.toUpperCase());
		} catch(final IllegalArgumentException e) {
			return null;
		}
	}

}
