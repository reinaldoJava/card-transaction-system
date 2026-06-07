package com.empresa.cardtransactionsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CardTransactionSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardTransactionSystemApplication.class, args);
	}

}
