package com.emc.rest.smart;

public class SmartClientException extends RuntimeException {

    private ErrorType errorType = ErrorType.Unknown;

    public SmartClientException(String message) {
        super(message);
    }

    public SmartClientException(String message, Throwable cause) {
        super(message, cause);
    }
    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    public enum ErrorType {
        Client, // 4xx
        Service, // 5xx
        Unknown
    }

    public boolean isServerError() {
        return this.getErrorType().equals(SmartClientException.ErrorType.Service);
    }

}
