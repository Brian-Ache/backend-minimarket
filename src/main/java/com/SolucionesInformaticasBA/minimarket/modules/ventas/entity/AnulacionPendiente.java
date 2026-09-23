package com.SolucionesInformaticasBA.minimarket.modules.ventas.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Una anulación que llegó antes que el ticket que anula.
 *
 * <p>Pasa cuando el lote se corta entre los dos eventos: el vendedor creó el ticket y lo anuló
 * cinco minutos después, pero la sincronización mandó primero el `ANULAR` —o el `CREAR` falló y
 * se reintenta en el lote siguiente—. La fila espera acá hasta que la venta llegue, y en el
 * mismo momento en que se persiste el `CREAR` se aplica.
 *
 * <p><b>No tiene FK a ventas, a propósito.</b> La venta puede no existir todavía, que es
 * exactamente la única razón por la que esta tabla existe. La PK es el uuid del ticket, así que
 * un `ANULAR` repetido no puede dejar dos filas.
 */
@Entity
@Table(name = "anulaciones_pendientes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnulacionPendiente implements Persistable<UUID> {

    /** El uuid del ticket que todavía no llegó. Lo pone el front, como el de la venta. */
    @Id
    @Column(name = "id_venta")
    private UUID idVenta;

    /** Cuándo se anuló en el local. Es la fecha que va a quedar en {@code ventas.deleted_at}. */
    @Column(name = "anulado_en", nullable = false)
    private LocalDateTime anuladoEn;

    /** Quién anuló. Es un hecho de hace dos días, igual que el vendedor del ticket. */
    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    @Column(name = "motivo", length = 255)
    private String motivo;

    /** Cuándo llegó esta fila a MySQL, que no es lo mismo que cuándo se anuló. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public UUID getId() {
        return idVenta;
    }

    /** Ver {@link Venta#isNew()}: con el id asignado a mano hay que decirlo explícitamente. */
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
