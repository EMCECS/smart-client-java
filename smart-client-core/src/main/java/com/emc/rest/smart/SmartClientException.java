package com.emc.rest.smart;

import javax.ws.rs.WebApplicationException;

public class SmartClientException extends WebApplicationException {

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

    public boolean isServerError() {
        return this.getErrorType().equals(SmartClientException.ErrorType.Service);
    }
    
    public enum ErrorType {
        Client, // 4xx
        Service, // 5xx
        Unknown
    }

}
