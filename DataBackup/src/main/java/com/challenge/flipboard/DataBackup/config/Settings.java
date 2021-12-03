package com.challenge.flipboard.DataBackup.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;

/**
 * Singleton to hold immutable application settings
 * 
 * @author bpgra
 *
 */
public class Settings {
	private static final String OUTPUT = "output";
	private static final String SOURCE = "source";
	public static final String APP_ROOT = "DataBackup";
	private static Settings instance;

	public static Settings getSettings(Config config) {
		if (instance == null)
			instance = new Settings(config);
		return instance;
	}

	/**
	 * For testing - allows loading specific config files, wiping previous settings
	 * 
	 * @param file
	 * @return
	 */
	public static Settings getSettings(String file) {
		ConfigFactory.invalidateCaches();
		final Config newConf = ConfigFactory.load(file)
				.withFallback(ConfigFactory.defaultReference().getConfig(APP_ROOT));
		instance = null;
		return getSettings(newConf);
	}

	public static Settings getSettings() {
		return getSettings(ConfigFactory.load().getConfig(APP_ROOT));
	}

	private Source source;
	private Output output;

	private Settings(Config config) {
		this.source = ConfigBeanFactory.create(config.getConfig(SOURCE), Source.class);
		this.output = ConfigBeanFactory.create(config.getConfig(OUTPUT), Output.class);
	}

	public static String getAppRoot() {
		return APP_ROOT;
	}

	public Source getSource() {
		return source;
	}

	public Output getOutput() {
		return output;
	}
}
