package com.ffreader.api.domain;

public enum FanficStatus {
    /** Row created, waiting for the async job to pick it up. */
    PENDING,
    /** Async job is actively scraping chapters / building the PDF. */
    PARSING,
    /** All chapters fetched and PDF generated successfully. */
    PARSED,
    /** Something went wrong; see errorMessage. */
    FAILED
}
