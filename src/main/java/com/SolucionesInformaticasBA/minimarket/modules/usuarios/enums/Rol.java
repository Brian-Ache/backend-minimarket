package com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Roles del sistema, en jerarquía {@code SUPERADMIN > ADMIN > EMPLEADO}.
 *
 * <p>La jerarquía es estricta: un rol manda sobre los de nivel menor, nunca sobre uno de su
 * mismo nivel. Un ADMIN no puede entonces bloquear ni dar de baja a otro ADMIN, que es
 * justamente lo que hace del SUPERADMIN una llave maestra y no un ADMIN más.
 */
public enum Rol {

    /**
     * Dueño del sistema. Administra la instalación y gestiona a los ADMIN: los da de alta, los
     * bloquea y los da de baja.
     *
     * <p>No se puede crear por API —ningún rol manda sobre su propio nivel—: el SUPERADMIN
     * llega por el seed de la base ({@code 01_seed.sql}). Es a propósito: si el alta de
     * superadmins fuera un endpoint, alcanzaría con tomar una sesión de superadmin para
     * fabricarse otro y volver irreversible el compromiso.
     */
    SUPERADMIN(2),

    /** Dueño del comercio: paga el servicio y da de alta a sus empleados. */
    ADMIN(1),

    /** Operario: vende, cobra, compra y mueve stock, pero no administra el negocio. */
    EMPLEADO(0);

    private final int nivel;

    Rol(int nivel) {
        this.nivel = nivel;
    }

    /** Verdadero si este rol está estrictamente por encima de {@code otro} en la jerarquía. */
    public boolean mandaSobre(Rol otro) {
        return otro != null && this.nivel > otro.nivel;
    }

    /**
     * Roles que ejerce quien tiene este rol: el propio y todos los de nivel menor.
     *
     * <p>Lo usa el filtro JWT para armar las authorities, de modo que un SUPERADMIN pase
     * también los {@code hasRole('ADMIN')} sin que cada regla tenga que enumerar los roles
     * superiores.
     */
    public List<Rol> rolesQueEjerce() {
        return Arrays.stream(values())
                .filter(r -> r.nivel <= this.nivel)
                .toList();
    }
}
