package com.SolucionesInformaticasBA.minimarket.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Uuid7Test {

    @Test
    @DisplayName("el uuid generado es versión 7 y variante RFC")
    void versionYVariante() {
        UUID uuid = Uuid7.nuevo();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2); // "10" binario, la variante RFC 9562
        assertThat(Uuid7.esV7(uuid)).isTrue();
    }

    @Test
    @DisplayName("dos uuid consecutivos quedan ordenados: es la razón de ser del v7")
    void dosConsecutivosQuedanOrdenados() {
        UUID primero = Uuid7.nuevo();
        UUID segundo = Uuid7.nuevo();

        // Sin signo: comparar UUID con compareTo trata las mitades como long con signo, y
        // eso da vuelta el orden cuando el bit más alto cambia. InnoDB ordena por los bytes.
        assertThat(Long.compareUnsigned(primero.getMostSignificantBits(),
                segundo.getMostSignificantBits())).isNegative();
    }

    @Test
    @DisplayName("mil uuid seguidos quedan ordenados, aunque caigan en el mismo milisegundo")
    void milSeguidosQuedanOrdenados() {
        List<UUID> generados = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            generados.add(Uuid7.nuevo());
        }

        // Justo lo que el timestamp solo no garantiza: mil llamadas entran en pocos
        // milisegundos, así que el orden dentro de cada uno lo sostiene el contador.
        for (int i = 1; i < generados.size(); i++) {
            assertThat(Long.compareUnsigned(
                    generados.get(i - 1).getMostSignificantBits(),
                    generados.get(i).getMostSignificantBits()))
                .as("el uuid %d no es mayor que el anterior", i)
                .isNegative();
        }
        assertThat(new HashSet<>(generados)).hasSize(1000);
    }

    @Test
    @DisplayName("los 48 bits altos son el epoch en milisegundos")
    void losBitsAltosSonLaFecha() {
        long antes = System.currentTimeMillis();
        UUID uuid = Uuid7.nuevo();
        long despues = System.currentTimeMillis();

        long msDelUuid = uuid.getMostSignificantBits() >>> 16;

        assertThat(msDelUuid).isBetween(antes, despues);
        // Y que eso sea una fecha de verdad, no un número que casualmente entra en el rango.
        assertThat(Instant.ofEpochMilli(msDelUuid)).isBetween(
                Instant.ofEpochMilli(antes), Instant.ofEpochMilli(despues));
    }

    @Test
    @DisplayName("no repite ids con varios hilos generando a la vez")
    void sinColisionesEnParalelo() throws InterruptedException {
        int hilos = 8;
        int porHilo = 500;
        Set<UUID> generados = java.util.Collections.synchronizedSet(new HashSet<>());
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch terminaron = new CountDownLatch(hilos);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    for (int j = 0; j < porHilo; j++) {
                        generados.add(Uuid7.nuevo());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    terminaron.countDown();
                }
            });
        }
        largada.countDown();
        assertThat(terminaron.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(generados).hasSize(hilos * porHilo);
    }

    @Test
    @DisplayName("esV7 rechaza el v4, que es lo que genera un front distraído")
    void esV7RechazaOtrasVersiones() {
        assertThat(Uuid7.esV7(UUID.randomUUID())).isFalse();
        assertThat(Uuid7.esV7(null)).isFalse();
        assertThat(Uuid7.esV7(UUID.fromString("00000000-0000-1000-8000-000000000000"))).isFalse();
    }
}
