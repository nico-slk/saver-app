package com.saver.inflation;

import java.util.List;

/**
 * Fuente de inflación oficial de una moneda. Para sumar otra (ej. EUR): implementar, registrarla
 * en {@link InflationConfiguration} y agregar la moneda a las validaciones.
 */
public interface InflationProvider {

    /** Moneda ISO (ARS, USD). */
    String currency();

    /** Nombre corto guardado como origen del dato. */
    String source();

    /** Serie mensual disponible. Lanza {@link InflationFetchException} si la fuente no responde. */
    List<MonthlyRate> fetch();
}
