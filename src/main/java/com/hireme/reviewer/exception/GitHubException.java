package com.hireme.reviewer.exception;

public final class GitHubException extends ApplicationException {
    private static final long serialVersionUID = 1L;
    public enum Reason {
        USER_NOT_FOUND,
        RATE_LIMITED,
        INVALID_RESPONSE,
        API_ERROR
    }

    private final Reason reason;

    public GitHubException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GitHubException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
