package com.saver.inflation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** GET/POST de JSON con el HttpClient del JDK (sin dependencias extra). */
class HttpJsonClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http;
    private final ObjectMapper json;

    HttpJsonClient(HttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    JsonNode get(URI uri) {
        return send(HttpRequest.newBuilder(uri).GET());
    }

    JsonNode post(URI uri, Object body) {
        try {
            return send(HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))));
        } catch (IOException e) {
            throw new InflationFetchException("Cannot serialize request", e);
        }
    }

    private JsonNode send(HttpRequest.Builder builder) {
        HttpRequest req = builder.timeout(TIMEOUT).header("Accept", "application/json")
                .header("User-Agent", "saver-app").build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new InflationFetchException(req.uri().getHost() + " responded " + res.statusCode());
            }
            return json.readTree(res.body());
        } catch (IOException e) {
            throw new InflationFetchException(req.uri().getHost() + " unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InflationFetchException("Interrupted", e);
        }
    }
}
