package com.saver.inflation;

public class InflationFetchException extends RuntimeException {
    public InflationFetchException(String message) { super(message); }
    public InflationFetchException(String message, Throwable cause) { super(message, cause); }
}
