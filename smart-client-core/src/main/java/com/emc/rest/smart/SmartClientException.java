package com.emc.rest.smart;

public class SmartClientException extends RuntimeException {

    public SmartClientException(String message) {
        super(message);
    }

    public SmartClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public enum ErrorType {
        Client, // 4xx
        Service, // 5xx
        Unknown
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    private ErrorType errorType = ErrorType.Unknown;

}
