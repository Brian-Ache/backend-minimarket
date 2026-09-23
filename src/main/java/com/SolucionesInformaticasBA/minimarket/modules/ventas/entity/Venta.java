package com.SolucionesInformaticasBA.minimarket.modules.ventas.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.OrigenVenta;

import jakarta.persistence.*;

@Entity
@Table(name = "ventas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Venta implements Persistable<UUID> {

    /**
     * UUIDv7, y siempre asignado a mano: lo genera el backend con {@code Uuid7.nuevo()} en la
     * venta online, y lo trae el front en la venta offline —el ticket ya existía en el
     * dispositivo antes de que el backend supiera de él—. Por eso no hay {@code @GeneratedValue}:
     * la estrategia UUID de Hibernate produce v4, que como PK fragmenta el índice de InnoDB.
     */
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal total;

    @Builder.Default
    @Column(nullable = true)
    private Boolean cobrada = false;

    @Column(name = "fecha_cobro", nullable = true)
    private LocalDateTime fechaCobro;

    @Column(name = "metodo_pago", nullable = true, length = 20)
    private String metodoPago;

    @Column(name = "monto_recibido", nullable = true, precision = 12, scale = 2)
    private BigDecimal montoRecibido;

    @Column(name = "id_sesion", nullable = true)
    private UUID idSesion;

    /**
     * Cuándo ocurrió el ticket, no cuándo se insertó la fila. En una venta offline la manda el
     * front y puede ser de hace dos días; en la online la pone el servicio con el reloj del
     * servidor. Sin {@code @CreationTimestamp} a propósito: esa anotación pisa el valor en cada
     * INSERT, y un lote de veinte tickets quedaría todo fechado en el mismo segundo.
     */
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Cuándo se anuló. En una anulación offline también la manda el front. */
    @Builder.Default
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt = null;

    /** Cuándo llegó a MySQL. Null en las ventas online, que llegan en el momento. */
    @Column(name = "sincronizado_en")
    private LocalDateTime sincronizadoEn;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 10)
    private OrigenVenta origen = OrigenVenta.ONLINE;

    /** Qué caja generó el ticket ("caja-01"). Viene a nivel del lote de sincronización. */
    @Column(name = "dispositivo", length = 50)
    private String dispositivo;

    /**
     * Quién sincronizó, tomado del JWT. No es el vendedor: con una terminal y cambio de turno,
     * el que está logueado cuando vuelve internet no es el que hizo los tickets encolados.
     * Guardar los dos es lo que deja el par a la vista.
     */
    @Column(name = "id_usuario_sync")
    private UUID idUsuarioSync;

    /**
     * La venta se guardó pero dejó una discrepancia que alguien tiene que mirar: stock que hubo
     * que regularizar, o un total declarado que no coincidió con el recalculado. No es un error:
     * el ticket es válido y ya está sincronizado.
     */
    @Builder.Default
    @Column(name = "requiere_revision", nullable = false)
    private boolean requiereRevision = false;

    /**
     * Si la fila todavía no está en la base.
     *
     * <p>Sin esto, {@code save()} de Spring Data decide que una entidad es nueva mirando si su
     * id es null. Como acá el id lo asignamos nosotros, toda venta le parecía ya existente y
     * cada {@code save()} pasaba por {@code merge()}: un SELECT que no devuelve nada antes de
     * cada INSERT. En un ticket de diez líneas son once consultas de más, y en un lote de
     * sincronización de cien tickets, más de mil.
     *
     * <p>El flag es {@code @Transient} —no es una columna— y se apaga solo en cuanto la fila se
     * persiste o se lee de la base.
     */
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
