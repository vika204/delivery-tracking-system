package com.delivery.problems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

/**
 * Central exception handling for every HTTP service. Subclass it in a {@code @RestControllerAdvice} and add only the
 * service's own domain exceptions; everything else is handled here the same way everywhere:
 * <ul>
 *   <li>Spring MVC's own exceptions (bad JSON, missing header, wrong type, unsupported media type, ...) are
 *       answered by {@link ResponseEntityExceptionHandler} as RFC 9457 problem details;</li>
 *   <li>request validation failures become {@code 400 urn:delivery:problem:validation-failed} with a field list;</li>
 *   <li>anything unexpected becomes {@code 500 urn:delivery:problem:internal-error} with a fixed message, never the
 *       exception text or a stack trace;</li>
 *   <li>every problem has {@code application/problem+json}, a non-blank {@code type} and a {@code title}.</li>
 * </ul>
 * Do not declare another {@code @ExceptionHandler} for {@code Exception} in a subclass (the mapping would be
 * ambiguous); override {@link #internalErrorDetail()} to change the text instead.
 */
public abstract class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI BLANK_TYPE = URI.create("about:blank");

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Message returned to callers for unexpected failures. */
    protected String internalErrorDetail() {
        return "An unexpected error occurred";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, internalErrorDetail());
        problem.setType(ProblemTypes.INTERNAL_ERROR);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, bodyValidationProblem(ex, status, errors), headers, status, request);
    }

    /** The problem for an invalid request body; override to tailor type or detail for particular request types. */
    protected ProblemDetail bodyValidationProblem(
            MethodArgumentNotValidException ex, HttpStatusCode status, List<FieldViolation> errors) {
        return validationProblem(status, errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream().flatMap(result -> {
            if (result instanceof ParameterErrors fields) {
                return fields.getFieldErrors().stream()
                        .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())));
            }
            return result.getResolvableErrors().stream()
                    .map(error -> new FieldViolation(
                            String.valueOf(result.getMethodParameter().getParameterName()),
                            String.valueOf(error.getDefaultMessage())));
        }).toList();
        return handleExceptionInternal(ex, validationProblem(status, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpHeaders problemHeaders = new HttpHeaders();
        problemHeaders.putAll(headers);
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, problemHeaders, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            complete(problem, statusCode);
        }
        return response;
    }

    private static ProblemDetail validationProblem(HttpStatusCode status, List<FieldViolation> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setType(ProblemTypes.VALIDATION_FAILED);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Fills in the type and title Spring leaves blank ({@code about:blank}, no title) from the status. */
    static void complete(ProblemDetail problem, HttpStatusCode status) {
        if (problem.getType() == null || BLANK_TYPE.equals(problem.getType())) {
            problem.setType(typeFor(status));
        }
        if (problem.getTitle() == null) {
            HttpStatus known = HttpStatus.resolve(status.value());
            problem.setTitle(known != null ? known.getReasonPhrase() : "HTTP " + status.value());
        }
    }

    static URI typeFor(HttpStatusCode status) {
        return ProblemTypes.of(switch (status.value()) {
            case 400 -> "bad-request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 406 -> "not-acceptable";
            case 409 -> "conflict";
            case 413 -> "payload-too-large";
            case 415 -> "unsupported-media-type";
            case 422 -> "unprocessable";
            case 429 -> "too-many-requests";
            case 500 -> "internal-error";
            case 502 -> "bad-gateway";
            case 503 -> "service-unavailable";
            case 504 -> "gateway-timeout";
            default -> "http-" + status.value();
        });
    }

    /** One invalid field in a validation problem. */
    public record FieldViolation(String field, String message) {
    }
}
