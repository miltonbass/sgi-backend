package com.miltonbass.sgi_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling 
public class SgiBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SgiBackendApplication.class, args);
	}

}
