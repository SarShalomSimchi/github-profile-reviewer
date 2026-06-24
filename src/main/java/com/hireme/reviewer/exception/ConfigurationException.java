package com.hireme.reviewer.exception;

public final class ConfigurationException extends ApplicationException {
    private static final long serialVersionUID = 1L;
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
