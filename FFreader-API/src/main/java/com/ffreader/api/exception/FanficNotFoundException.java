package com.ffreader.api.exception;

import java.util.UUID;

public class FanficNotFoundException extends RuntimeException {
    public FanficNotFoundException(UUID id) {
        super("Fanfic not found: " + id);
    }
}
