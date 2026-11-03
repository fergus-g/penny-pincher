package com.pennypincher.backend;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	/** Irish time decides which month is "this month"; tests swap in a fixed clock. */
	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Europe/Dublin"));
	}

}
