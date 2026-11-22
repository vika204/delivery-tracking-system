package com.delivery.problems;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProblemExceptionHandlerTest {

    private final MockMvc mvc = standaloneSetup(new Controller()).setControllerAdvice(new Handler()).build();

    @Test
    void invalidBodyIsAValidationProblemWithTheFieldList() throws Exception {
        mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void unexpectedFailureNeverLeaksItsMessage() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret-internal-detail"))));
    }

    @Test
    void springMvcErrorsGetTypedProblemsToo() throws Exception {
        mvc.perform(get("/header"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:bad-request"))
                .andExpect(jsonPath("$.title").value("Bad Request"));

        mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:bad-request"));

        mvc.perform(post("/things").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:unsupported-media-type"));

        mvc.perform(post("/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:method-not-allowed"));
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() throws Exception {
        mvc.perform(get("/teapot"))
                .andExpect(status().isIAmATeapot())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(418))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:http-418"));
    }

    @Test
    void subclassCanWordTheInternalErrorDetail() throws Exception {
        MockMvc custom = standaloneSetup(new Controller()).setControllerAdvice(new Handler() {
            @Override
            protected String internalErrorDetail() {
                return "Try again later";
            }
        }).build();

        custom.perform(get("/boom")).andExpect(jsonPath("$.detail").value("Try again later"));
    }

    @RestController
    static class Controller {
        record Thing(@NotBlank String name) {
        }

        @PostMapping("/things")
        String create(@Valid @RequestBody Thing thing) {
            return thing.name();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @GetMapping("/header")
        String header(@RequestHeader("X-User-Id") long userId) {
            return String.valueOf(userId);
        }

        @GetMapping("/teapot")
        String teapot() {
            throw new ResponseStatusException(HttpStatusCode.valueOf(418), "short and stout");
        }
    }

    @RestControllerAdvice
    static class Handler extends ProblemExceptionHandler {
    }
}
