//https://www.youtube.com/watch?v=LsaXy7SRXMY
package ar.edu.itba.cleancode;

import java.time.Duration;
import java.util.logging.Logger;
import java.util.concurrent.Executors;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;


public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = Logger.getLogger(MainVerticle.class.getName());

    private CircuitBreaker circuitBreaker;
    private Retry retry;

    @Override
    public void start() {

        // Create a Vert.x web router
        Router router = Router.router(vertx);

        // Configure CircuitBreaker with a timeout of  seconds
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open circuit if failure rate is above 50%
                .waitDurationInOpenState(Duration.ofSeconds(5)) // Wait for 5 seconds in open state
                .slidingWindow(5, 3, SlidingWindowType.COUNT_BASED) // Buffer size in closed state
                .build();
        
        // Create the retry configuration
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3) // Number of retry attempts
                .waitDuration(Duration.ofMillis(500)) // Wait time between retries
                .build();

        // Create a retry instance with the created configuration
        retry = Retry.of("myRetry", retryConfig);
        
        // Create a circuit breaker with default configuration
        circuitBreaker = CircuitBreaker.ofDefaults("myCircuitBreaker");

        // Deploy the HTTP server with the router
        vertx.createHttpServer()
        .requestHandler(router)
        .listen(8080, result -> {
            if (result.succeeded()) {
                System.out.println("HTTP server started on port 8080");
            } else {
                System.err.println("Error starting HTTP server: " + result.cause());
            }
        });

        // Define a simple "Hello World" endpoint
        router.get("/hello").handler(ctx -> {
            retry.executeRunnable(() -> {
                circuitBreaker.decorateRunnable(() -> {
                    performOperation();
                    ctx.response()
                        .putHeader("content-type", "text/plain")
                        .end("Hello world!");
                    logger.info("Operation succeeded");
                }).run();
            });
        });

        // Schedule a task to reset the circuit breaker state periodically
        Vertx.currentContext().owner().setPeriodic(10000, timerId -> {
            circuitBreaker.reset(); // Reset the circuit breaker state every 10 seconds
            System.out.println("Circuit breaker state reset");
        });

    }

    private void performOperation() {
        // Simulate some potentially failing operation
        if (Math.random() < 0.5) {
            logger.info("Simulated failure");
            throw new RuntimeException("Simulated failure");
        }
    }

}
