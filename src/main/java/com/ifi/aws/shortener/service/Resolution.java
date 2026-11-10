package com.ifi.aws.shortener.service;

/** Outcome of looking up a short code. */
public record Resolution(Status status, String longUrl) {

	public enum Status { FOUND, NOT_FOUND, GONE }

	public static Resolution found(String longUrl) {
		return new Resolution(Status.FOUND, longUrl);
	}

	public static Resolution notFound() {
		return new Resolution(Status.NOT_FOUND, null);
	}

	public static Resolution gone() {
		return new Resolution(Status.GONE, null);
	}
}
