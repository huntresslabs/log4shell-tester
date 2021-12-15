package com.huntresslabs.log4shell;

import com.github.benmanes.caffeine.cache.Cache;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IndexHandler implements HttpHandler {

    private Cache<String, List<String>> cache;
    private String url;
    private String indexHTML;

    public IndexHandler(Cache<String, List<String>> cache, String url) {
        this.cache = cache;
        this.url = url;
        this.indexHTML = App.readResource("index.html");
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        // Generate a random UUID
        String uuid = UUID.randomUUID().toString();

        // Store the GUID
        cache.put(uuid, new ArrayList<>());

        String response = indexHTML.replace("GUID", uuid);
        response = response.replace("PAYLOAD", "${jndi:" + this.url + "/" + uuid + "}");

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(response.toString());
    }
}
