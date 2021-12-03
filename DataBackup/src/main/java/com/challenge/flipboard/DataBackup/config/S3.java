package com.challenge.flipboard.DataBackup.config;

public class S3 {
	private String bucket;
	private String key;

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}
}
