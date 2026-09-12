package com.SolucionesInformaticasBA.minimarket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta el contexto completo, así que necesita MySQL corriendo. Es el único test que no
 * es unitario, y por eso lleva el tag {@code integracion}: el CI lo excluye con
 * {@code -DexcludedGroups=integracion}. Todo test que necesite base va con el mismo tag.
 */
@Tag("integracion")
@SpringBootTest
class MinimarketApplicationTests {

	@Test
	void contextLoads() {
	}

}
