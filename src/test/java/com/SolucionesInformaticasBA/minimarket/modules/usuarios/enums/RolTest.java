package com.SolucionesInformaticasBA.minimarket.modules.usuarios.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RolTest {

    @Test
    @DisplayName("la jerarquía es SUPERADMIN > ADMIN > EMPLEADO")
    void jerarquia() {
        assertThat(Rol.SUPERADMIN.mandaSobre(Rol.ADMIN)).isTrue();
        assertThat(Rol.SUPERADMIN.mandaSobre(Rol.EMPLEADO)).isTrue();
        assertThat(Rol.ADMIN.mandaSobre(Rol.EMPLEADO)).isTrue();

        assertThat(Rol.ADMIN.mandaSobre(Rol.SUPERADMIN)).isFalse();
        assertThat(Rol.EMPLEADO.mandaSobre(Rol.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("ningún rol manda sobre su propio nivel: por eso un SUPERADMIN no crea otro")
    void nadieMandaSobreSuPropioNivel() {
        for (Rol rol : Rol.values()) {
            assertThat(rol.mandaSobre(rol)).isFalse();
        }
    }

    @Test
    void mandaSobreNullEsFalse() {
        assertThat(Rol.SUPERADMIN.mandaSobre(null)).isFalse();
    }

    @Test
    @DisplayName("cada rol ejerce el propio y los de abajo")
    void rolesQueEjerce() {
        assertThat(Rol.SUPERADMIN.rolesQueEjerce())
                .containsExactlyInAnyOrder(Rol.SUPERADMIN, Rol.ADMIN, Rol.EMPLEADO);
        assertThat(Rol.ADMIN.rolesQueEjerce())
                .containsExactlyInAnyOrder(Rol.ADMIN, Rol.EMPLEADO);
        assertThat(Rol.EMPLEADO.rolesQueEjerce())
                .containsExactly(Rol.EMPLEADO);
    }
}
