package com.cognodyne.dw.cdi;

public class UnsatisfiedDependencyException extends RuntimeException {
    private static final long serialVersionUID = -1339055089071955653L;

    public UnsatisfiedDependencyException() {
        super();
    }

    public UnsatisfiedDependencyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public UnsatisfiedDependencyException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsatisfiedDependencyException(String message) {
        super(message);
    }

    public UnsatisfiedDependencyException(Throwable cause) {
        super(cause);
    }
}
