package com.ffreader.api.exception;

/** The client sent a bad link, as opposed to the site failing while it was being scraped. */
public class InvalidFanficUrlException extends FanficScrapeException {
    public InvalidFanficUrlException(String url) {
        super("Not a recognizable fanfiction.net story URL: " + url);
    }
}
