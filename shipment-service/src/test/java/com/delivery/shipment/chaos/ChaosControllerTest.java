package com.delivery.shipment.chaos;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** The fault-injection switch itself must be safe to operate: validated input, observable state, full reset. */
class ChaosControllerTest {

    private final ChaosSettings settings = new ChaosSettings();
    private final MockMvc mvc = standaloneSetup(new ChaosController(settings)).build();

    @Test
    void startsInert() throws Exception {
        mvc.perform(get("/internal/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureRate").value(0.0))
                .andExpect(jsonPath("$.latencyMs").value(0));
    }

    @Test
    void setsFailureRateAndLatency() throws Exception {
        mvc.perform(post("/internal/chaos/fail").param("rate", "0.25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.failureRate").value(0.25));
        mvc.perform(post("/internal/chaos/latency").param("ms", "250"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latencyMs").value(250));

        assertThat(settings.failureRate()).isEqualTo(0.25);
        assertThat(settings.latencyMs()).isEqualTo(250);
    }

    @Test
    void rejectsOutOfRangeValuesWithoutChangingState() throws Exception {
        mvc.perform(post("/internal/chaos/fail").param("rate", "1.5")).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/chaos/fail").param("rate", "-0.1")).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/chaos/latency").param("ms", "60001")).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/chaos/latency").param("ms", "-1")).andExpect(status().isBadRequest());

        assertThat(settings.failureRate()).isZero();
        assertThat(settings.latencyMs()).isZero();
    }

    @Test
    void acceptsBoundaryValues() throws Exception {
        mvc.perform(post("/internal/chaos/fail").param("rate", "1")).andExpect(status().isOk());
        mvc.perform(post("/internal/chaos/latency").param("ms", "60000")).andExpect(status().isOk());

        assertThat(settings.failureRate()).isEqualTo(1.0);
        assertThat(settings.latencyMs()).isEqualTo(60_000);
    }

    @Test
    void deleteRestoresSteadyState() throws Exception {
        settings.setFailureRate(1.0);
        settings.setLatencyMs(5_000);

        mvc.perform(delete("/internal/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureRate").value(0.0))
                .andExpect(jsonPath("$.latencyMs").value(0));
    }
}
