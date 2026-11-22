package com.delivery.auth.exception;

import com.delivery.problems.DataAccessProblemExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler extends DataAccessProblemExceptionHandler {

    @Override
    protected ProblemDetail dataConflict(DataIntegrityViolationException ex) {
        ProblemDetail problem = super.dataConflict(ex);
        problem.setDetail("The profile conflicts with existing data. Check that the email address is not already in use.");
        return problem;
    }

    @Override
    protected String internalErrorDetail() {
        return "Profile data is temporarily unavailable. Please try again later.";
    }
}
