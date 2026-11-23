package com.saver.exchange;

/** Tipos de dólar (pesos por dólar). Fuente: dolarapi.com. */
public enum ExchangeRateType {
    OFICIAL("oficial"), MEP("bolsa"), BLUE("blue"), TARJETA("tarjeta"), CCL("contadoconliqui"),
    MAYORISTA("mayorista"), CRIPTO("cripto");

    private final String dolarApiKey;

    ExchangeRateType(String dolarApiKey) { this.dolarApiKey = dolarApiKey; }

    public static ExchangeRateType fromDolarApi(String casa) {
        for (ExchangeRateType t : values()) if (t.dolarApiKey.equals(casa)) return t;
        return null;
    }
}
