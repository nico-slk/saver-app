package com.saver.exchange;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cotizaciones del dólar ({@code app.exchange.*}) desde dolarapi.com (sin clave).
 * {@code enabled=false}: no se descarga nada y cada movimiento en otra moneda pide la cotización a mano.
 */
@ConfigurationProperties(prefix = "app.exchange")
public record ExchangeProperties(boolean enabled, boolean syncOnStartup, String dolarApiUrl) {}
