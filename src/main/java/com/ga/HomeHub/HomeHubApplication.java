package com.ga.HomeHub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HomeHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomeHubApplication.class, args);
	}

}
