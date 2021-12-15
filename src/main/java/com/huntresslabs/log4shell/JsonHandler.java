package com.huntresslabs.log4shell;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.gson.Gson;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.util.*;

public class JsonHandler implements HttpHandler {

    private Cache<String, List<String>> cache;

    public JsonHandler(Cache<String, List<String>> cache) {
        this.cache = cache;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        // Grab the UUID
        String uuid = Optional.ofNullable(exchange.getQueryParameters().get("uuid").getFirst()).orElse("");

        // Make sure this UUID is real
        List<String> hits = cache.getIfPresent(uuid);
        if (hits == null) {
            HTTPServer.notFoundHandler(exchange);
            return;
        }

        Gson gson = new Gson();
        Collection<Map<String, String>> entries = new ArrayList<Map<String, String>>();

        for (String hit : hits) {
            if (hit == "exists") continue;

            // Parse out datetime and IP
            String[] values = hit.split("/");
            if (values.length != 2) continue;

            // Append to results
            Map<String, String> entry = new HashMap<String, String>();
            entry.put("ip", values[0]);
            entry.put("timestamp", values[1]);

            entries.add(entry);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("uuid", uuid);
        result.put("hits", entries);

        // Send the response w/ HTML type
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(gson.toJson(result));
    }
}
