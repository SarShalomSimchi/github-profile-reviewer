package com.hireme.reviewer.exception;

public abstract class ApplicationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
