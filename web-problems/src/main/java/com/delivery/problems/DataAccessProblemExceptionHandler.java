package com.delivery.problems;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * {@link ProblemExceptionHandler} for services with a database: a constraint violation or a concurrent update is a
 * {@code 409}, and an unreachable or too slow database is a {@code 503} with {@code Retry-After}, not a generic 500.
 * Services override {@code dataConflict} / {@code concurrentUpdateDetail} to word the message for their domain.
 */
public abstract class DataAccessProblemExceptionHandler extends ProblemExceptionHandler {

    private static final String RETRY_AFTER_SECONDS = "5";

    /** The 409 problem for a violated constraint; override to word it (or type it) for the service's domain. */
    protected ProblemDetail dataConflict(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with existing data");
        problem.setType(ProblemTypes.DATA_CONFLICT);
        problem.setTitle("Data conflict");
        return problem;
    }

    protected String concurrentUpdateDetail() {
        return "The resource was changed concurrently, reload it and retry";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataConflict(DataIntegrityViolationException ex, WebRequest request) {
        return handleExceptionInternal(ex, dataConflict(ex), new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleConcurrentUpdate(OptimisticLockingFailureException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, concurrentUpdateDetail());
        problem.setType(ProblemTypes.CONCURRENT_UPDATE);
        problem.setTitle("Concurrent update");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
            QueryTimeoutException.class})
    public ResponseEntity<Object> handleDatabaseUnavailable(Exception ex, WebRequest request) {
        logger.warn("Database unavailable: " + ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable, please try again later");
        problem.setType(ProblemTypes.DATABASE_UNAVAILABLE);
        problem.setTitle("Database unavailable");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.SERVICE_UNAVAILABLE, request);
    }
}
