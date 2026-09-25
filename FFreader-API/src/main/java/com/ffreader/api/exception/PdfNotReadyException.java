package com.ffreader.api.exception;

import java.util.UUID;

public class PdfNotReadyException extends RuntimeException {
    public PdfNotReadyException(UUID id) {
        super("PDF for fanfic " + id + " is not ready yet");
    }
}
