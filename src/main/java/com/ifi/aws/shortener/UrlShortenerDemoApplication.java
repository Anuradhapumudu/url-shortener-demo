package com.ifi.aws.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UrlShortenerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(UrlShortenerDemoApplication.class, args);
	}
}
