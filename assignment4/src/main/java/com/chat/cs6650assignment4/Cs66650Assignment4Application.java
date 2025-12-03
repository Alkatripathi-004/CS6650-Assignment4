package com.chat.cs6650assignment4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableRetry
@EnableCaching
@EnableAsync
public class Cs66650Assignment4Application {

	public static void main(String[] args) {
		SpringApplication.run(Cs66650Assignment4Application.class, args);
	}

}
