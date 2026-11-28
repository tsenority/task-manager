package com.taskmanager.exception;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({UserNotFoundException.class, TaskNotFoundException.class})
    public ResponseEntity<Object> handleNotFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleInvalidRequest(IllegalArgumentException exception) {
        if ("Task status is required for update".equals(exception.getMessage())) {
            return response(
                    HttpStatus.BAD_REQUEST,
                    "Request validation failed.",
                    List.of(fieldError("status", "Status is required for updates.")));
        }
        if ("Task priority is required for update".equals(exception.getMessage())) {
            return response(
                    HttpStatus.BAD_REQUEST,
                    "Request validation failed.",
                    List.of(fieldError("priority", "Priority is required for updates.")));
        }
        return response(HttpStatus.BAD_REQUEST, "Invalid task request.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.is5xxServerError()) {
                logger.error("Unexpected failure while processing an HTTP request", exception);
            }
            String detail = status.is5xxServerError()
                    ? "An unexpected error occurred."
                    : "Request could not be processed.";
            return response(status, detail, List.of());
        }

        logger.error("Unexpected failure while processing an HTTP request", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparing(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "")))
                .map(error -> fieldError(
                        error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value.")))
                .toList();
        return response(status, "Request validation failed.", fieldErrors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return response(status, "Invalid request body", List.of(), headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (status.is5xxServerError()) {
            logger.error("Unexpected failure while processing an HTTP request", exception);
        }
        String detail = status.is5xxServerError()
                ? "An unexpected error occurred."
                : "Request could not be processed.";
        return super.handleExceptionInternal(
                exception, problem(status, detail, List.of()), headers, status, request);
    }

    private ResponseEntity<Object> response(
            HttpStatusCode status, String detail, List<Map<String, String>> fieldErrors) {
        return ResponseEntity.status(status).body(problem(status, detail, fieldErrors));
    }

    private ResponseEntity<Object> response(
            HttpStatusCode status,
            String detail,
            List<Map<String, String>> fieldErrors,
            HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers).body(problem(status, detail, fieldErrors));
    }

    private ProblemDetail problem(
            HttpStatusCode status, String detail, List<Map<String, String>> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }
}
