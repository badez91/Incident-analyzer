package com.aira.common.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable: " + cause.getMessage(), cause);
    }
}
