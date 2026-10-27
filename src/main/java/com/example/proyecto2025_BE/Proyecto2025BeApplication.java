package com.example.proyecto2025_BE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class Proyecto2025BeApplication implements CommandLineRunner {
	
	@Value("${spring.profiles.active}")
    private String profile;

	public static void main(String[] args) {
		SpringApplication.run(Proyecto2025BeApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		log.info("Active profile: " + profile);
	}

}
