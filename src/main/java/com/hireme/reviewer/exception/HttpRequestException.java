package com.hireme.reviewer.exception;

public final class HttpRequestException extends ApplicationException {
    private static final long serialVersionUID = 1L;
    private final boolean retryable;

    public HttpRequestException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public HttpRequestException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
