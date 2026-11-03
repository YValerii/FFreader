package com.ffreader.api.exception;

import java.util.UUID;

public class FanficBusyException extends RuntimeException {
    public FanficBusyException(UUID id) {
        super("Fanfic " + id + " is still being parsed. Try again when it has finished.");
    }
}
