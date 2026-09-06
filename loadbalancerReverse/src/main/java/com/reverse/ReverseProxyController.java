package com.reverse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class ReverseProxyController {

    private final WebClient webClient = WebClient.create();

    private final String[] servers = {
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8083"
    };

    // Number of active connections for each server
    private final AtomicInteger[] activeConnections = {
            new AtomicInteger(0),
            new AtomicInteger(0),
            new AtomicInteger(0)
    };

    // Used to break ties between servers with equal connections
    private int lastSelectedServer = -1;

    @GetMapping("/hello")
    public String hello() {

        // Find server with least active connections
        int serverIndex = getLeastConnectionServer();

        String server = servers[serverIndex];

        // Increase connection count
        activeConnections[serverIndex].incrementAndGet();

        System.out.println(
                "Forwarding request to: " + server +
                        " | Active connections: " +
                        activeConnections[serverIndex].get()
        );

        try {

            return webClient
                    .get()
                    .uri(server + "/hello")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } finally {

            // Decrease connection count after request finishes
            activeConnections[serverIndex].decrementAndGet();

            System.out.println(
                    "Request completed: " + server +
                            " | Active connections: " +
                            activeConnections[serverIndex].get()
            );
        }
    }

    private synchronized int getLeastConnectionServer() {

        int leastConnections = Integer.MAX_VALUE;

        // Find the minimum number of active connections
        for (int i = 0; i < activeConnections.length; i++) {

            int connections = activeConnections[i].get();

            if (connections < leastConnections) {
                leastConnections = connections;
            }
        }

        // Find a server having the minimum connections
        // and rotate between equally loaded servers
        for (int i = 1; i <= activeConnections.length; i++) {

            int index = (lastSelectedServer + i) % activeConnections.length;

            if (activeConnections[index].get() == leastConnections) {

                lastSelectedServer = index;

                System.out.println(
                        "LEAST CONECTION CHECK -> " +
                                "S1=" + activeConnections[0].get() +
                                " S2=" + activeConnections[1].get() +
                                " S3=" + activeConnections[2].get() +
                                " | SELECTED=S" + (index + 1)
                );

                return index;
            }
        }

        return 0;
    }
}