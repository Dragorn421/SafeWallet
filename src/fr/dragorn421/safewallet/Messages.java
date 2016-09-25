package fr.dragorn421.safewallet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class Messages
{

	final private Map<String, String> messages;

	public Messages()
	{
		this.messages = new HashMap<>();
	}

	public boolean fromConfig(final ConfigurationSection config)
	{
		boolean save = false;
		if(!config.isString("messages-file"))
		{
			config.set("messages-file", "messages.yml");
			save = true;
		}
		Path messagesFile = new File(SafeWalletPlugin.get().getDataFolder(), config.getString("messages-file")).toPath().toAbsolutePath();
		if(!messagesFile.getParent().toFile().exists())
		{
			try {
				Files.createDirectories(messagesFile.getParent());
			} catch (final IOException e) {
				SafeWalletPlugin.get().getLogger().log(Level.WARNING, "Could not create directories leading to the messages file.", e);
				messagesFile = null;
			}
		}
		if(messagesFile != null)
		{
			if(!messagesFile.toFile().isFile())
			{
				try (	final BufferedReader reader = new BufferedReader(new InputStreamReader(SafeWalletPlugin.get().getResource("messages.yml"), StandardCharsets.UTF_8));
						final BufferedWriter writer = Files.newBufferedWriter(messagesFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
					String line;
					while((line = reader.readLine()) != null)
					{
						writer.write(line);
						writer.newLine();
					}
				} catch (final IOException e) {
					SafeWalletPlugin.get().getLogger().log(Level.WARNING, "Could not create the messages file.", e);
					messagesFile = null;
				}
			}
			if(messagesFile != null)
			{
				YamlConfiguration messagesCs = null;
				try {
					messagesCs = YamlConfiguration.loadConfiguration(Files.newBufferedReader(messagesFile, StandardCharsets.UTF_8));
				} catch (final IOException e) {
					SafeWalletPlugin.get().getLogger().log(Level.WARNING, "Could not load messages file " + messagesFile, e);
				}
				if(messagesCs != null)
					this.loadMessages(messagesCs);
			}
		}
		return save;
	}

	private void loadMessages(final ConfigurationSection cs)
	{
		for(final String key : cs.getKeys(false))
			this.messages.put(key, cs.getString(key).replace('&', ChatColor.COLOR_CHAR));
	}

	public void clearMessages()
	{
		this.messages.clear();
	}

	public String get(final String key)
	{
		final String str = this.messages.get(key);
		if(str == null)
		{
			SafeWalletPlugin.get().getLogger().warning("No message is set for key " + key);
			return key;
		}
		return str;
	}

	public String format(final String key, final Object ...format)
	{
		String str = this.messages.get(key);
		if(str == null)
		{
			SafeWalletPlugin.get().getLogger().warning("No message is set for key " + key);
			final StringBuilder sb = new StringBuilder(key);
			for(int i=0;(i+1)<format.length;i+=2)
			{
				if(!(format[i] instanceof String))
					throw new IllegalArgumentException("Key must be a String.");
				if(i != 0)
					sb.append(", ");
				sb.append('{');
				sb.append((String) format[i]);
				sb.append("}=");
				sb.append(Objects.toString(format[i+1]));
			}
			return sb.toString();
		}
		for(int i=0;(i+1)<format.length;i+=2)
		{
			if(!(format[i] instanceof String))
				throw new IllegalArgumentException("Key must be a String.");
			str = str.replace("{" + ((String) format[i]) + "}", Objects.toString(format[i+1]));
		}
		return str;
	}

}
