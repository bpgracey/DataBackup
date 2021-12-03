package com.challenge.flipboard.DataBackup.config;

public class Output {
	private String destination;
	private String filepath;
	private S3 s3;

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public String getFilepath() {
		return filepath;
	}

	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}

	public S3 getS3() {
		return s3;
	}

	public void setS3(S3 s3) {
		this.s3 = s3;
	}
}
