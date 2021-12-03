package com.challenge.flipboard.DataBackup.backup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.challenge.flipboard.DataBackup.config.Output;
import com.challenge.flipboard.DataBackup.config.S3;
import com.challenge.flipboard.DataBackup.config.Settings;
import com.challenge.flipboard.DataBackup.config.Source;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

import alex.mojaki.s3upload.MultiPartOutputStream;
import alex.mojaki.s3upload.StreamTransferManager;

public class Backup {
	private final Logger LOG = LoggerFactory.getLogger(Backup.class);

	private static final int COLUMN_NAME = 4;
	private static final int IS_GENERATED = 24;

	private Settings settings;

	public Backup() {
		super();
		this.settings = Settings.getSettings();
	}

	public Backup(Settings settings) {
		super();
		this.settings = settings;
	}

	public void process() {
		final Source source = settings.getSource();
		final Output output = settings.getOutput();

		// make connection
		final Properties properties = new Properties();
		properties.put("user", source.getUser());
		properties.put("password", source.getPassword());
		LOG.info("Connecting to {}", source.getUrl());
		try (final Connection conn = DriverManager.getConnection(source.getUrl(), properties);
				final PipedOutputStream outputStream = new PipedOutputStream();
				final PipedInputStream inputStream = new PipedInputStream(outputStream)) {
			final Writer writer = new OutputStreamWriter(outputStream);

			// get just the data columns - not calculated columns
			final String table = source.getTable();
			final int limit = source.getLimit();
			LOG.info("Retrieving data for table {}", table);
			if (limit > 0) LOG.info("*** Using limit {}", limit);
			final ResultSet rs = makeResultSet(conn, table, limit);

			// set up conversion thread
			new Thread(new Converter(writer, rs)).start();

			// set up output on this thread
			final String destination = output.getDestination();
			switch (destination) {
			case "s3":
				outputToS3(output, inputStream);
				break;
			case "file":
				outputToFile(output, inputStream);
				break;
			case "console":
			default: // console
				outputToConsole(inputStream);
			}

		} catch (Exception e) {
			LOG.error("Failed to backup", e);
		}

	}

	private void outputToConsole(final PipedInputStream inputStream) {
		LOG.info("Writing to console");
		final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		final Stream<String> results = reader.lines();
		results.forEach(line -> System.out.println(line));
	}

	private void outputToFile(final Output output, final PipedInputStream inputStream) throws IOException {
		final String filepath = output.getFilepath();
		LOG.info("Opening file {}", filepath);
		final File file = new File(filepath);
		Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private void outputToS3(final Output output, final PipedInputStream inputStream) {
		final S3 s3 = output.getS3();
		final String bucket = s3.getBucket();
		final String key = s3.getKey();
		LOG.info("Connecting to AWS");
		final AmazonS3Client client = (AmazonS3Client) AmazonS3ClientBuilder.standard().build(); // TODO fix!
		LOG.info("Connecting to S3 bucket {}", bucket);
		final StreamTransferManager manager = new StreamTransferManager(bucket, key, client);
		final MultiPartOutputStream multiPartOutputStream = manager.getMultiPartOutputStreams().get(0);
		final OutputStreamWriter outputWriter = new OutputStreamWriter(multiPartOutputStream);
		final BufferedReader readerForS3 = new BufferedReader(new InputStreamReader(inputStream));
		final Stream<String> resultsForS3 = readerForS3.lines();
		resultsForS3.forEach(line -> {
			try {
				outputWriter.append(line);
			} catch (IOException e) {
				LOG.warn("Write to S3 failed", e);
			}
		});
		LOG.info("Disconnecting from S3");
		;
		multiPartOutputStream.close();
		manager.complete();
		client.shutdown();
	}

	private ResultSet makeResultSet(final Connection conn, final String table, int limit) throws Exception {
		final ResultSet columns = conn.getMetaData().getColumns(null, null, table, null);
		final List<String> colNames = new ArrayList<>();
		while (columns.next()) {
			if ("NO".equalsIgnoreCase(columns.getString(IS_GENERATED))) {
				colNames.add(columns.getString(COLUMN_NAME));
			}
		}
		if (colNames.isEmpty())
			throw new Exception("No columns found");

		// Make SELECT statement
		final StringBuilder sqlSelect = new StringBuilder();
		sqlSelect.append("SELECT ");
		boolean first = true;
		for (String colName : colNames) {
			if (first)
				first = false;
			else
				sqlSelect.append(", ");
			sqlSelect.append(colName);
		}
		sqlSelect.append(" FROM ");
		sqlSelect.append(table);
		// limit rows for testing (note: this only works for MySQL - syntax is different for other dialects
		// using a dialect-independent SQL statement builder would make this simpler!
		if (limit > 0) {
			sqlSelect.append(" LIMIT ");
			sqlSelect.append(limit);
		}
		sqlSelect.append(";");
		LOG.info("Query to use: {}", sqlSelect);

		// execute query
		final ResultSet rs = conn.createStatement().executeQuery(sqlSelect.toString());
		return rs;
	}

}

class Converter implements Runnable {
	private final Logger LOG = LoggerFactory.getLogger(Converter.class);

	private Writer writer;
	private ResultSet rs;

	public Converter(Writer writer, ResultSet rs) {
		super();
		this.writer = writer;
		this.rs = rs;
	}

	@Override
	public void run() {
		convertRecordSetToCSV(writer, rs);
	}

	private void convertRecordSetToCSV(final Writer writer, final ResultSet rs) {
		ICSVWriter csvWriter = new CSVWriterBuilder(writer).build();
		try {
			csvWriter.writeAll(rs, true);
		} catch (Exception e) {
			LOG.error("Failed in CSV conversion", e);
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				LOG.warn("Output writer failed to close", e);
			}
		}
	}

}