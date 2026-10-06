import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple test client to verify the load balancer is distributing
 * requests across servers correctly.
 *
 * Usage:
 *   javac LoadTester.java
 *   java LoadTester wrr 20
 *   java LoadTester least 20
 *
 * Make sure the reverse proxy (port 8080) and all 3 backend
 * servers (8081, 8082, 8083) are already running before this.
 */
public class LoadTester {

    public static void main(String[] args) throws Exception {

        String algorithm = args.length > 0 ? args[0] : "wrr";
        int totalRequests = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        String baseUrl = "http://localhost:8080/hello?algorithm=" + algorithm;

        System.out.println("Firing " + totalRequests + " requests using algorithm: " + algorithm);
        System.out.println("Target: " + baseUrl);
        System.out.println("----------------------------------------");

        HttpClient client = HttpClient.newHttpClient();

        // Tracks how many responses came from each server name
        Map<String, AtomicInteger> serverHitCount = new ConcurrentHashMap<>();
        AtomicInteger failures = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        for (int i = 1; i <= totalRequests; i++) {
            final int requestId = i;

            pool.submit(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl))
                            .GET()
                            .build();

                    long start = System.currentTimeMillis();

                    HttpResponse<String> response =
                            client.send(request, HttpResponse.BodyHandlers.ofString());

                    long duration = System.currentTimeMillis() - start;

                    String body = response.body();
                    String serverName = extractServerName(body);

                    serverHitCount
                            .computeIfAbsent(serverName, k -> new AtomicInteger(0))
                            .incrementAndGet();

                    System.out.println(
                            "Request #" + requestId +
                                    " -> " + serverName +
                                    " (" + duration + "ms)"
                    );

                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.out.println("Request #" + requestId + " FAILED: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        System.out.println("----------------------------------------");
        System.out.println("SUMMARY (algorithm=" + algorithm + ")");
        System.out.println("----------------------------------------");

        serverHitCount.forEach((server, count) ->
                System.out.println(server + " handled " + count.get() + " requests")
        );

        if (failures.get() > 0) {
            System.out.println("Failed requests: " + failures.get());
        }

        if (serverHitCount.size() > 1) {
            System.out.println("\nResult: Load IS being distributed across multiple servers.");
        } else if (serverHitCount.size() == 1) {
            System.out.println("\nResult: All requests went to ONE server only — check your algorithm/config.");
        } else {
            System.out.println("\nResult: No successful responses — check that all 4 apps are running.");
        }
    }

    // Pulls "SERVER-1" / "SERVER-2" / "SERVER-3" out of the response body text
    private static String extractServerName(String body) {
        if (body == null) return "UNKNOWN";

        for (String candidate : new String[]{"SERVER-1", "SERVER-2", "SERVER-3"}) {
            if (body.contains(candidate)) {
                return candidate;
            }
        }
        return "UNKNOWN";
    }
}