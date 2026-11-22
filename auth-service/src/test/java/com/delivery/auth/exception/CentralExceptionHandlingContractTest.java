package com.delivery.auth.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Whatever a controller throws, auth-service answers with a typed problem+json and never leaks the cause. */
class CentralExceptionHandlingContractTest {

    private final MockMvc mvc = standaloneSetup(new Thrower()).setControllerAdvice(new UserExceptionHandler()).build();

    @Test
    void unexpectedFailureIsATypedInternalErrorWithoutTheCause() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:internal-error"))
                .andExpect(content().string(not(containsString("secret-cause"))));
    }

    @Test
    void unreachableDatabaseIsServiceUnavailableWithRetryAfter() throws Exception {
        mvc.perform(get("/db-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:database-unavailable"));
    }

    @Test
    void missingRequiredHeaderIsATypedBadRequest() throws Exception {
        mvc.perform(get("/needs-header"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:bad-request"));
    }

    @RestController
    static class Thrower {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret-cause");
        }

        @GetMapping("/db-down")
        String dbDown() {
            throw new DataAccessResourceFailureException("jdbc:postgresql://db refused");
        }

        @GetMapping("/needs-header")
        String needsHeader(@org.springframework.web.bind.annotation.RequestHeader("X-User-Id") long id) {
            return String.valueOf(id);
        }
    }
}
