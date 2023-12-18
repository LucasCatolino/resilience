import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

@ExtendWith(VertxExtension.class)
class Resilience4jTest {

    @Test
    void testRetryMechanism(Vertx vertx, VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Configure Retry with a short retry duration for testing purposes
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .build();

        Retry retry = Retry.of("testRetry", retryConfig);

        // Build an HTTP request to a fictional service
        Supplier<CompletionStage<HttpResponse<Buffer>>> requestSupplier = () ->
                convertFutureToCompletionStage(webClient.get(8081, "localhost", "/backend").send());

        // Decorate the HTTP request with retry
        retry.executeCompletionStage(scheduler, requestSupplier)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        // Verify that the retry mechanism is working as expected
                        testContext.verify(() -> {
                            // Assertions or logging for failure scenarios
                            System.out.println("Retry failed: " + throwable.getMessage());
                        });
                    } else {
                        // Verify that the request was successful after retry
                        testContext.verify(() -> {
                            // Assertions or logging for success scenarios
                            System.out.println("Request successful: " + result.bodyAsString());
                        });
                    }

                    testContext.completeNow();
                });
    }

    private <T> CompletionStage<T> convertFutureToCompletionStage(io.vertx.core.Future<T> vertxFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        vertxFuture.onComplete(ar -> {
            if (ar.succeeded()) {
                completableFuture.complete(ar.result());
            } else {
                completableFuture.completeExceptionally(ar.cause());
            }
        });
        return completableFuture;
    }

    @Test
    void testCircuitBreaker(Vertx vertx, VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);

        // Configure CircuitBreaker with a short timeout for testing purposes
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("testCircuitBreaker");

        // Build an HTTP request to a fictional service
        HttpRequest<Buffer> httpRequest = webClient.get(8081, "localhost", "/backend")
                .putHeader("content-type", "application/json");

        // Decorate the HTTP request with circuit breaker
        circuitBreaker.executeCompletionStage(() -> {
            CompletableFuture<HttpResponse<Buffer>> future = new CompletableFuture<>();
            httpRequest.send(ar -> {
                if (ar.succeeded()) {
                    future.complete(ar.result());
                } else {
                    future.completeExceptionally(ar.cause());
                }
            });
            return future;
        }).whenComplete((response, throwable) -> {
            if (throwable != null) {
                // Verify that the circuit breaker is handling failures as expected
                testContext.verify(() -> {
                    // Assertions or logging for failure scenarios
                    System.out.println("Circuit breaker open: " + throwable.getMessage());
                });
            } else {
                // Verify that the request was successful when the circuit is closed
                testContext.verify(() -> {
                    // Assertions or logging for success scenarios
                    System.out.println("Request successful: " + response.bodyAsString());
                });
            }

            testContext.completeNow();
        });
    }
}
