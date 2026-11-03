package com.ffreader.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gives every error the same JSON body. The base class maps Spring MVC's own
 * exceptions (bad input, wrong method, unknown route...) to their proper status.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(FanficNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(FanficNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, new HttpHeaders(), ex.getMessage());
    }

    @ExceptionHandler(PdfNotReadyException.class)
    public ResponseEntity<Object> handleNotReady(PdfNotReadyException ex) {
        return build(HttpStatus.CONFLICT, new HttpHeaders(), ex.getMessage());
    }

    @ExceptionHandler(FanficBusyException.class)
    public ResponseEntity<Object> handleBusy(FanficBusyException ex) {
        return build(HttpStatus.CONFLICT, new HttpHeaders(), ex.getMessage());
    }

    @ExceptionHandler(InvalidFanficUrlException.class)
    public ResponseEntity<Object> handleInvalidUrl(InvalidFanficUrlException ex) {
        return build(HttpStatus.BAD_REQUEST, new HttpHeaders(), ex.getMessage());
    }

    @ExceptionHandler(FanficScrapeException.class)
    public ResponseEntity<Object> handleScrapeError(FanficScrapeException ex) {
        return build(HttpStatus.BAD_GATEWAY, new HttpHeaders(), ex.getMessage());
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<Object> handleQueueFull(TaskRejectedException ex) {
        log.warn("Parse queue is full: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, new HttpHeaders(), "Too many stories are queued. Try again later.");
    }

    /** The details stay in the server log: exception messages can contain SQL, file paths or connection URLs. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(), "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ex.getBody().setDetail(message);
        return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
    }

    /** Every exception the base class handles ends up here. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body,
                                                          HttpHeaders headers,
                                                          HttpStatusCode statusCode,
                                                          WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : status.getReasonPhrase();
        return build(status, headers, message);
    }

    private ResponseEntity<Object> build(HttpStatus status, HttpHeaders headers, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}
