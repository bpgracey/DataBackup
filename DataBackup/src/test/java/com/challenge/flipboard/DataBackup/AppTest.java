package com.challenge.flipboard.DataBackup;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.challenge.flipboard.DataBackup.backup.Backup;
import com.challenge.flipboard.DataBackup.config.Settings;

/**
 * Unit test for simple App.
 */
public class AppTest {
	@Test
	public void checkConfig() {
		final Settings settings = Settings.getSettings("testConfig.conf");
		assertEquals("Settings failed to load", "testUrl", settings.getSource().getUrl());
	}
	
	@Test
	public void checkConversion() {
		final Settings settings = Settings.getSettings("testConversion.conf");
		final Backup backup = new Backup(settings);
		backup.process();
	}
}
