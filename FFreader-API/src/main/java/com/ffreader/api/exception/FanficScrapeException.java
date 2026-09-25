package com.ffreader.api.exception;

public class FanficScrapeException extends RuntimeException {
    public FanficScrapeException(String message) {
        super(message);
    }

    public FanficScrapeException(String message, Throwable cause) {
        super(message, cause);
    }
}
