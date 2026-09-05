package com.SolucionesInformaticasBA.minimarket.shared;

/**
 * Límites y mensajes de los listados paginados, en un solo lugar.
 *
 * <p>El techo estaba declarado como constante privada en cada controlador que paginaba. Con
 * nueve endpoints paginados eso son nueve definiciones del mismo número, y basta con que una
 * quede desactualizada para que dos listados de la misma API acepten tamaños distintos.
 *
 * <p>Los mensajes van acá por lo mismo: los tres errores de paginación tienen que decir lo
 * mismo en todos los endpoints. Son constantes y no un enum porque {@code @Min} y {@code @Max}
 * exigen constantes de compilación.
 */
public final class Paginacion {

    /** Techo del tamaño de página de todo listado paginado de la API. */
    public static final int MAX_PAGE_SIZE = 100;

    public static final String PAGE_MIN = "El número de página no puede ser negativo";
    public static final String SIZE_MIN = "El tamaño de página debe ser al menos 1";
    public static final String SIZE_MAX = "El tamaño de página no puede superar " + MAX_PAGE_SIZE;

    private Paginacion() {
    }
}
