package com.saver.inflation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Descarga de inflación oficial ({@code app.inflation.*}).
 *
 * <ul>
 *   <li>{@code enabled}: false = no se descarga nada (sin internet se usa la inflación manual o 0).</li>
 *   <li>{@code estimateMonths}: meses oficiales que se promedian para estimar la inflación futura.</li>
 *   <li>ARS: ArgentinaDatos (INDEC, sin clave). USD: BLS, IPC-U de EE.UU. (sin clave hasta 25 consultas/día;
 *       con {@code blsApiKey} hasta 500).</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "app.inflation")
public record InflationProperties(
        boolean enabled,
        boolean syncOnStartup,
        @Min(1) @Max(12) int estimateMonths,
        String argentinaDatosUrl,
        String blsUrl,
        String blsApiKey) {}
