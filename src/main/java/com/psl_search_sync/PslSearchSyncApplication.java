package com.psl_search_sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PslSearchSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(PslSearchSyncApplication.class, args);
	}

}
