package com.zhwesh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class YcSecuritiesApplication {

	public static void main(String[] args) {
		SpringApplication.run(YcSecuritiesApplication.class, args);
	}

}
