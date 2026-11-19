package com.delivery.webui.controller.support;

import com.delivery.webui.controller.CourierDashboardApiController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Помилки бекенд-сервісів для BFF-контролерів: 4xx (валідація, прострочена пропозиція тощо)
 * передаються сторінці з причиною й деталями полів, решта — 503.
 */
@RestControllerAdvice(assignableTypes = CourierDashboardApiController.class)
public class UpstreamProblemAdvice {

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail rejected(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || !status.is4xxClientError()) {
            return unavailable();
        }
        ProblemDetail upstream = readProblem(ex);
        String detail = upstream != null && upstream.getDetail() != null ? upstream.getDetail() : status.getReasonPhrase();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (upstream != null) {
            problem.setType(upstream.getType());
            Map<String, Object> properties = upstream.getProperties();
            if (properties != null && properties.containsKey("errors")) {
                problem.setProperty("errors", properties.get("errors"));
            }
        }
        return problem;
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail unreachable(RestClientException ex) {
        return unavailable();
    }

    private static ProblemDetail readProblem(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(ProblemDetail.class);
        } catch (RuntimeException unreadableBody) {
            return null;
        }
    }

    private static ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable");
    }
}
