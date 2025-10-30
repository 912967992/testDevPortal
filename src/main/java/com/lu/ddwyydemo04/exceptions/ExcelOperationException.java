package com.lu.ddwyydemo04.exceptions;

public class ExcelOperationException extends RuntimeException {
    private final int statusCode;

    public ExcelOperationException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}