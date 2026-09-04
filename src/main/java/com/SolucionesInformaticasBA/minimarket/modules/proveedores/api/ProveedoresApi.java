package com.SolucionesInformaticasBA.minimarket.modules.proveedores.api;

import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorRequest;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;

public interface ProveedoresApi {
    ProveedorResponse crear(ProveedorRequest request);
    /** Solo proveedores en pie: 404 si está dado de baja. */
    ProveedorResponse getById(UUID id);

    /**
     * Resuelve el proveedor aunque esté dado de baja, con {@code deletedAt} cargado. Es lo que
     * usan el historial de compras y el catálogo: una baja no debe borrar de la pantalla a
     * quién se le compró, solo impedir operaciones nuevas.
     */
    ProveedorResponse getByIdIncluyendoBajas(UUID id);

    /**
     * @param incluirBajas suma los proveedores dados de baja, que vienen con {@code deletedAt}
     *        cargado. Es cómo el front encuentra el que hay que restaurar: en el listado
     *        normal no aparecen.
     */
    List<ProveedorResponse> getAll(boolean incluirBajas);

    ProveedorResponse update(UUID id, ProveedorRequest request);

    /**
     * Baja lógica: el proveedor deja de poder usarse en compras nuevas y de asignarse a un
     * producto, pero sigue apareciendo en el historial y puede volver con {@link #restaurar}.
     */
    void delete(UUID id);

    /** Vuelve a habilitar un proveedor dado de baja, sobre la fila original. */
    ProveedorResponse restaurar(UUID id);

    /** Solo proveedores en pie: es la validación de compras y del alta de productos. */
    boolean existsById(UUID id);
}
