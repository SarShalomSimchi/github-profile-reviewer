package com.hireme.reviewer.exception;

public final class AiReviewException extends ApplicationException {
    private static final long serialVersionUID = 1L;
    public enum Reason {
        AUTHENTICATION_FAILED,
        RATE_LIMITED,
        INVALID_REQUEST,
        INVALID_RESPONSE,
        API_ERROR,
        NETWORK_ERROR
    }

    private final Reason reason;
    private final boolean retryable;

    public AiReviewException(Reason reason, String message, boolean retryable) {
        super(message);
        this.reason = reason;
        this.retryable = retryable;
    }

    public AiReviewException(Reason reason, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.reason = reason;
        this.retryable = retryable;
    }

    public Reason reason() {
        return reason;
    }

    public boolean retryable() {
        return retryable;
    }
}
