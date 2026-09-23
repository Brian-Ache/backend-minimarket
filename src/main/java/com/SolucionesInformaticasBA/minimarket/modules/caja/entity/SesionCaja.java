package com.SolucionesInformaticasBA.minimarket.modules.caja.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import org.springframework.data.domain.Persistable;

import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sesiones_caja")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SesionCaja implements Persistable<UUID> {
    /**
     * UUIDv7 asignado a mano, como el de {@code Venta}: lo genera el backend al abrir el turno
     * con conexión y lo trae el front cuando el turno abrió sin ella —el turno ya existía en el
     * dispositivo antes de que el backend supiera de él—.
     */
    @Id
    private UUID id;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "saldo_inicial", nullable = false)
    private float saldoInicial;

    @Column(name = "saldo_final")
    private Float saldoFinal;

    @Column(name = "saldo_esperado")
    private Float saldoEsperado;

    private Float diferencia;

    /**
     * Reparto del efectivo al cerrar: cuánto se retira y cuánto queda en la caja para el turno
     * siguiente. Null en los cortes anteriores a que esto se registrara, que es "no se sabe" y
     * no "no se retiró nada".
     */
    @Column(name = "monto_retirado")
    private Float montoRetirado;

    @Column(name = "saldo_dejado")
    private Float saldoDejado;

    /**
     * Cuánto se apartó lo contado al abrir de lo que había dejado el cierre anterior. No impide
     * abrir —el comercio tiene que poder trabajar— pero deja el faltante o el sobrante
     * registrado en vez de perderlo entre dos turnos.
     */
    @Column(name = "diferencia_apertura")
    private Float diferenciaApertura;

    // Desglose del arqueo, congelado al cerrar. Un corte es un documento contable:
    // se guarda como quedó, no se recalcula al consultarlo.
    @Column(name = "total_ventas")
    private Float totalVentas;

    @Column(name = "cantidad_ventas")
    private Integer cantidadVentas;

    @Column(name = "total_compras")
    private Float totalCompras;

    @Column(name = "cantidad_compras")
    private Integer cantidadCompras;

    @Column(name = "total_entradas_manuales")
    private Float totalEntradasManuales;

    @Column(name = "total_salidas_manuales")
    private Float totalSalidasManuales;

    @Column(length = 255)
    private String observaciones;

    @Column(name = "id_usuario_apertura", nullable = false)
    private UUID idUsuarioApertura;

    @Column(name = "id_usuario_cierre")
    private UUID idUsuarioCierre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSesion estado;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt = null;

    /** Ver {@code Venta#isNew()}: con el id asignado a mano hay que decirlo explícitamente. */
    @Builder.Default
    @Transient
    private boolean nuevo = true;

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PrePersist
    void yaEstaEnLaBase() {
        this.nuevo = false;
    }
}
