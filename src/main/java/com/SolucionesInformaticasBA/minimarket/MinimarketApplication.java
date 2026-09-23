package com.SolucionesInformaticasBA.minimarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// El barrido de reservas de stock vencidas corre periódicamente: ver ReservaStockScheduler.
@EnableScheduling
@SpringBootApplication
public class MinimarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(MinimarketApplication.class, args);
	}
}
