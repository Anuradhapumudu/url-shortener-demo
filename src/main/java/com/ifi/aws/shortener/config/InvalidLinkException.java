package com.ifi.aws.shortener.config;

/** A client-correctable problem with the submitted URL. The message is safe to show to API callers. */
public class InvalidLinkException extends RuntimeException {

	public InvalidLinkException(String message) {
		super(message);
	}

	public InvalidLinkException(String message, Throwable cause) {
		super(message, cause);
	}
}
