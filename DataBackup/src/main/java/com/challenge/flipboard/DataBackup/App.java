package com.challenge.flipboard.DataBackup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.challenge.flipboard.DataBackup.backup.Backup;
import com.challenge.flipboard.DataBackup.config.Settings;

/**
 * Hello world!
 *
 */
public class App {
	private static Logger LOG = LoggerFactory.getLogger(App.class);
	
	public static void main(String[] args) {
		LOG.info("Starting application");
		// get settings
		final Settings settings = Settings.getSettings();
		
		// run backup process
		final Backup backup = new Backup(settings);
		backup.process();
	}
}
