package com.eip.simulator.service;

import com.eip.common.enums.SimulationScenario;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ScenarioGenerator {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final Map<SimulationScenario, List<String>> EXCEPTION_TEMPLATES = new EnumMap<>(SimulationScenario.class);
    private static final Map<SimulationScenario, List<String>> LOG_TEMPLATES = new EnumMap<>(SimulationScenario.class);

    static {
        EXCEPTION_TEMPLATES.put(SimulationScenario.DATABASE_UNAVAILABLE, List.of(
                "org.postgresql.util.PSQLException: Connection to localhost:5432 refused",
                "com.zaxxer.hikari.pool.HikariPool$PoolInitializationException: Failed to initialize pool",
                "org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection",
                "java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms",
                "org.postgresql.util.PSQLException: FATAL: too many connections for role \"app_user\""
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.SERVICE_TIMEOUT, List.of(
                "java.net.SocketTimeoutException: Read timed out",
                "io.netty.handler.timeout.ReadTimeoutException: null",
                "org.springframework.web.reactive.function.client.WebClientRequestException: Connection timed out after 5000ms",
                "java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 30000ms",
                "feign.RetryableException: Read timed out executing GET http://downstream-service/api/data"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.NULL_POINTER_EXCEPTION, List.of(
                "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"str\" is null",
                "java.lang.NullPointerException: Cannot invoke \"Object.toString()\" because the return value of \"getUser()\" is null",
                "java.lang.NullPointerException: null",
                "java.lang.NullPointerException: Cannot read field \"id\" because \"entity\" is null",
                "java.lang.NullPointerException: Cannot invoke \"java.util.List.size()\" because \"list\" is null"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.MEMORY_EXHAUSTION, List.of(
                "java.lang.OutOfMemoryError: Java heap space",
                "java.lang.OutOfMemoryError: GC overhead limit exceeded",
                "java.lang.OutOfMemoryError: Metaspace",
                "java.lang.OutOfMemoryError: Direct buffer memory",
                "java.lang.OutOfMemoryError: unable to create native thread"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.CONNECTION_REFUSED, List.of(
                "java.net.ConnectException: Connection refused (Connection refused)",
                "io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: localhost/127.0.0.1:9090",
                "org.apache.http.conn.HttpHostConnectException: Connect to downstream-service:8080 failed",
                "java.net.ConnectException: Connection refused: no further information",
                "reactor.netty.http.client.PrematureCloseException: Connection prematurely closed BEFORE response"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.LATENCY_SPIKE, List.of(
                "WARN: Request processing time exceeded threshold: 5000ms (actual: 12345ms)",
                "WARN: Slow query detected: SELECT * FROM orders WHERE status='pending' took 8500ms",
                "WARN: Circuit breaker half-open for service downstream-api",
                "ERROR: Response time SLA violated: p99=15000ms (threshold=3000ms)"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.THREAD_DEADLOCK, List.of(
                "ERROR: Deadlock detected between thread-pool-1-thread-5 and thread-pool-1-thread-12",
                "WARN: Thread pool exhausted: activeThreads=200, maxPoolSize=200, queueSize=5000",
                "ERROR: java.util.concurrent.RejectedExecutionException: Task rejected from ThreadPoolExecutor",
                "WARN: Thread blocked for 60000ms waiting for lock on com.eip.service.OrderService"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.AUTHENTICATION_FAILURE, List.of(
                "org.springframework.security.authentication.BadCredentialsException: Bad credentials",
                "io.jsonwebtoken.ExpiredJwtException: JWT expired at 2024-01-15T10:30:00Z",
                "org.springframework.security.access.AccessDeniedException: Access is denied",
                "io.jsonwebtoken.MalformedJwtException: Invalid JWT token",
                "org.springframework.security.oauth2.server.resource.InvalidBearerTokenException: Invalid token"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.DISK_FULL, List.of(
                "java.io.IOException: No space left on device",
                "ERROR: Failed to write to /var/log/application.log: No space left on device",
                "org.h2.mvstore.MVStoreException: Writing failed for file /data/db.mv.db [90]",
                "ERROR: Kafka broker rejected produce request: CORRUPT_MESSAGE (disk full)"
        ));

        EXCEPTION_TEMPLATES.put(SimulationScenario.NETWORK_PARTITION, List.of(
                "java.net.UnknownHostException: Unable to resolve host \"service-discovery.internal\"",
                "java.net.NoRouteToHostException: No route to host (Host unreachable)",
                "io.grpc.StatusRuntimeException: UNAVAILABLE: io exception",
                "ERROR: DNS resolution failed for downstream-service.cluster.local",
                "ERROR: Network unreachable: Failed to connect to 10.0.0.5:8080"
        ));

        // Log templates
        LOG_TEMPLATES.put(SimulationScenario.DATABASE_UNAVAILABLE, List.of(
                "INFO  [HikariPool-1] Starting connection pool validation",
                "WARN  [HikariPool-1] Connection validation failed - attempting reconnect",
                "ERROR [HikariPool-1] Failed to validate connection: Connection reset",
                "ERROR [TransactionManager] Could not open JDBC Connection for transaction",
                "WARN  [HealthCheck] Database health check failed: Connection refused"
        ));

        LOG_TEMPLATES.put(SimulationScenario.SERVICE_TIMEOUT, List.of(
                "INFO  [HttpClient] Sending request to downstream service",
                "WARN  [HttpClient] Request taking longer than expected (>3000ms)",
                "ERROR [HttpClient] Request timed out after 30000ms",
                "WARN  [CircuitBreaker] Failure threshold reached, opening circuit",
                "INFO  [Retry] Attempting retry 1/3 for downstream call"
        ));

        LOG_TEMPLATES.put(SimulationScenario.NULL_POINTER_EXCEPTION, List.of(
                "INFO  [RequestHandler] Processing incoming request",
                "DEBUG [UserService] Looking up user by ID: null",
                "ERROR [UserService] Unexpected null reference in user lookup",
                "ERROR [ExceptionHandler] Unhandled NullPointerException in request processing",
                "WARN  [Monitor] Increased NPE rate detected in UserService"
        ));

        LOG_TEMPLATES.put(SimulationScenario.MEMORY_EXHAUSTION, List.of(
                "WARN  [GCMonitor] GC pause time increasing: 500ms -> 2000ms -> 5000ms",
                "WARN  [HeapMonitor] Heap usage at 85% (6.8GB/8GB)",
                "WARN  [HeapMonitor] Heap usage at 95% (7.6GB/8GB)",
                "ERROR [HeapMonitor] Heap usage CRITICAL: 99% - OOM imminent",
                "ERROR [JVM] OutOfMemoryError: Java heap space"
        ));

        LOG_TEMPLATES.put(SimulationScenario.CONNECTION_REFUSED, List.of(
                "INFO  [ServiceDiscovery] Resolving endpoint for downstream-service",
                "WARN  [HttpClient] Connection attempt 1 failed: Connection refused",
                "WARN  [HttpClient] Connection attempt 2 failed: Connection refused",
                "ERROR [HttpClient] All connection attempts exhausted",
                "ERROR [CircuitBreaker] Circuit opened for downstream-service"
        ));
    }

    public List<String> generateLogs(String scenario, String targetService, int lineCount) {
        SimulationScenario scenarioEnum;
        try {
            scenarioEnum = SimulationScenario.valueOf(scenario.toUpperCase());
        } catch (IllegalArgumentException e) {
            scenarioEnum = SimulationScenario.NULL_POINTER_EXCEPTION;
        }

        List<String> exceptions = EXCEPTION_TEMPLATES.getOrDefault(scenarioEnum,
                EXCEPTION_TEMPLATES.get(SimulationScenario.NULL_POINTER_EXCEPTION));
        List<String> logs = LOG_TEMPLATES.getOrDefault(scenarioEnum,
                LOG_TEMPLATES.get(SimulationScenario.NULL_POINTER_EXCEPTION));

        List<String> generatedLines = new ArrayList<>();
        Instant baseTime = Instant.now().minusSeconds(lineCount);

        for (int i = 0; i < lineCount; i++) {
            Instant logTime = baseTime.plusMillis(i * 1000L + ThreadLocalRandom.current().nextInt(500));
            String timestamp = LOG_TIME_FORMAT.format(logTime);

            if (i < logs.size()) {
                generatedLines.add(String.format("%s %s [%s] %s", timestamp, logs.get(i).split("\\s+")[0],
                        targetService, logs.get(i)));
            } else if (i % 3 == 0) {
                // Inject exceptions periodically
                String exception = exceptions.get(ThreadLocalRandom.current().nextInt(exceptions.size()));
                generatedLines.add(String.format("%s ERROR [%s] %s", timestamp, targetService, exception));
                generatedLines.add(String.format("    at com.eip.%s.service.CoreService.process(CoreService.java:%d)",
                        targetService.replace("-", "."), ThreadLocalRandom.current().nextInt(50, 300)));
                generatedLines.add(String.format("    at com.eip.%s.controller.ApiController.handle(ApiController.java:%d)",
                        targetService.replace("-", "."), ThreadLocalRandom.current().nextInt(20, 100)));
            } else {
                String log = logs.get(ThreadLocalRandom.current().nextInt(logs.size()));
                generatedLines.add(String.format("%s %s [%s] %s", timestamp,
                        log.contains("ERROR") ? "ERROR" : log.contains("WARN") ? "WARN " : "INFO ",
                        targetService, log));
            }
        }

        return generatedLines;
    }

    public List<String> getExceptionsForScenario(String scenario) {
        try {
            SimulationScenario scenarioEnum = SimulationScenario.valueOf(scenario.toUpperCase());
            return EXCEPTION_TEMPLATES.getOrDefault(scenarioEnum, List.of());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
