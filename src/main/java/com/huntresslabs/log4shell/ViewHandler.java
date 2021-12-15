package com.huntresslabs.log4shell;

import com.github.benmanes.caffeine.cache.Cache;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.util.List;
import java.util.Optional;

public class ViewHandler implements HttpHandler {

    private Cache<String, List<String>> cache;
    private String url;
    private String viewHTML;

    public ViewHandler(Cache<String, List<String>> cache, String url) {
        this.cache = cache;
        this.url = url;
        this.viewHTML = App.readResource("view.html");
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

        // Build the page :sob:
        StringBuilder body = new StringBuilder();

        // Iterate over all hits
        for (String hit : hits) {
            if (hit == "exists") continue;

            // Parse out datetime and IP
            String[] values = hit.split("/");
            if (values.length != 2) continue;

            // Append to results
            body.append("<tr><td>" + values[0] + "</td><td>" + values[1] + "</td></tr>");
        }

        String response = viewHTML.replace("BODY", body.toString());
        response = response.replace("UUID", uuid);
        response = response.replace("PAYLOAD", "${jndi:" + this.url + "/" + uuid + "}");

        // Send the response w/ HTML type
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(response);
    }
}
