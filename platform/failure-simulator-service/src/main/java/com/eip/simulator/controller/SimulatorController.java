package com.eip.simulator.controller;

import com.eip.common.dto.SimulationRequest;
import com.eip.common.dto.SimulationResult;
import com.eip.common.enums.SimulationScenario;
import com.eip.simulator.service.SimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simulate")
public class SimulatorController {

    private final SimulatorService simulatorService;

    public SimulatorController(SimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping
    public ResponseEntity<SimulationResult> simulate(@RequestBody SimulationRequest request) {
        SimulationResult result = simulatorService.simulate(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, String>>> listScenarios() {
        List<Map<String, String>> scenarios = Arrays.stream(SimulationScenario.values())
                .map(s -> Map.of(
                        "name", s.name(),
                        "description", getScenarioDescription(s)
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(scenarios);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<SimulationResult>> simulateBatch(@RequestBody List<SimulationRequest> requests) {
        List<SimulationResult> results = requests.stream()
                .map(simulatorService::simulate)
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    private String getScenarioDescription(SimulationScenario scenario) {
        return switch (scenario) {
            case DATABASE_UNAVAILABLE -> "Simulates database connection pool exhaustion and JDBC failures";
            case SERVICE_TIMEOUT -> "Simulates downstream service timeout with cascading failures";
            case CONNECTION_REFUSED -> "Simulates connection refused errors to downstream services";
            case NULL_POINTER_EXCEPTION -> "Simulates NullPointerException in business logic";
            case LATENCY_SPIKE -> "Simulates sudden increase in request processing time";
            case MEMORY_EXHAUSTION -> "Simulates JVM heap exhaustion and OutOfMemoryError";
            case THREAD_DEADLOCK -> "Simulates thread pool exhaustion and deadlock conditions";
            case AUTHENTICATION_FAILURE -> "Simulates JWT/OAuth authentication and authorization failures";
            case DISK_FULL -> "Simulates disk space exhaustion with I/O errors";
            case NETWORK_PARTITION -> "Simulates network partitioning with DNS and routing failures";
        };
    }
}
