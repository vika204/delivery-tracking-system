package com.delivery.problems;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DataAccessProblemExceptionHandlerTest {

    private final MockMvc mvc = standaloneSetup(new Controller()).setControllerAdvice(new Handler()).build();

    @Test
    void constraintViolationIsAConflictWithoutSqlDetails() throws Exception {
        mvc.perform(get("/constraint"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:data-conflict"))
                .andExpect(content().string(not(containsString("uk_users_email"))));
    }

    @Test
    void concurrentUpdateIsAConflict() throws Exception {
        mvc.perform(get("/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:concurrent-update"));
    }

    @Test
    void anUnreachableDatabaseIs503WithRetryAfterNotA500() throws Exception {
        for (String path : new String[] {"/down", "/no-tx", "/slow"}) {
            mvc.perform(get(path))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "5"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("urn:delivery:problem:database-unavailable"))
                    .andExpect(content().string(not(containsString("jdbc"))));
        }
    }

    @Test
    void subclassCanRewordTheConflict() throws Exception {
        MockMvc custom = standaloneSetup(new Controller()).setControllerAdvice(new Handler() {
            @Override
            protected org.springframework.http.ProblemDetail dataConflict(DataIntegrityViolationException ex) {
                var problem = super.dataConflict(ex);
                problem.setDetail("Email already used");
                return problem;
            }
        }).build();

        custom.perform(get("/constraint")).andExpect(jsonPath("$.detail").value("Email already used"));
    }

    @RestController
    static class Controller {
        @GetMapping("/constraint")
        String constraint() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_users_email");
        }

        @GetMapping("/lock")
        String lock() {
            throw new OptimisticLockingFailureException("stale");
        }

        @GetMapping("/down")
        String down() {
            throw new DataAccessResourceFailureException("jdbc:postgresql://db:5432 refused");
        }

        @GetMapping("/no-tx")
        String noTx() {
            throw new CannotCreateTransactionException("jdbc connection failed");
        }

        @GetMapping("/slow")
        String slow() {
            throw new QueryTimeoutException("jdbc timeout");
        }
    }

    @RestControllerAdvice
    static class Handler extends DataAccessProblemExceptionHandler {
    }
}
