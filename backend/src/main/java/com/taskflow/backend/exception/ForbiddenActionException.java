package com.taskflow.backend.exception;

public class ForbiddenActionException extends RuntimeException {
    public ForbiddenActionException(String msg) {
        super(msg);
    }
}
