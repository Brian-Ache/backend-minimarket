package com.SolucionesInformaticasBA.minimarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MinimarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(MinimarketApplication.class, args);
	}
    ///////////////////////////////////////////
            //TEST PARA CREAR UN PRODUCTO
            /*
    @Bean
    CommandLineRunner testProducto(ProductoRepository repo, UsuarioRepository userRepo) {
        return args -> {

            Usuario user = userRepo.findById(1L).orElse(null);
            if (user != null) {
                Producto p = new Producto();
                p.setNombre("Fanta");
                p.setBarcode("1234");
                p.setManejaLotes(false);
                p.setStock(10);
                p.setCreadoPor(user);

                repo.save(p);
            }

            System.out.println("Productos: " + repo.count());
            
        };
    */
}
