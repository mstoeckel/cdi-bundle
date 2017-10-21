package com.cognodyne.dw.cdi;

public class CircularDependencyDetectedException extends RuntimeException {
    private static final long serialVersionUID = 6367736400309110165L;

    public CircularDependencyDetectedException() {
        super();
    }

    public CircularDependencyDetectedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public CircularDependencyDetectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public CircularDependencyDetectedException(String message) {
        super(message);
    }

    public CircularDependencyDetectedException(Throwable cause) {
        super(cause);
    }
}
