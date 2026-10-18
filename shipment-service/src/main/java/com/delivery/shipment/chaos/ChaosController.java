package com.delivery.shipment.chaos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/internal/chaos")
@ConditionalOnProperty(name = "app.chaos.enabled", havingValue = "true")
public class ChaosController {

    private final ChaosSettings settings;

    public ChaosController(ChaosSettings settings) {
        this.settings = settings;
    }

    @PostMapping("/fail")
    public Map<String, Object> fail(@RequestParam double rate) {
        if (rate < 0 || rate > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rate must be between 0 and 1");
        }
        settings.setFailureRate(rate);
        return state();
    }

    @PostMapping("/latency")
    public Map<String, Object> latency(@RequestParam long ms) {
        if (ms < 0 || ms > 60_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ms must be between 0 and 60000");
        }
        settings.setLatencyMs(ms);
        return state();
    }

    @DeleteMapping
    public Map<String, Object> reset() {
        settings.reset();
        return state();
    }

    @GetMapping
    public Map<String, Object> state() {
        return Map.of("failureRate", settings.failureRate(), "latencyMs", settings.latencyMs());
    }
}
