package com.huntresslabs.log4shell;

import io.lettuce.core.RedisClient;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.JBossLoggingAccessLogReceiver;

public class HTTPServer
{
    public static void run(String host, int port, RedisClient redis, String ldap_url) {
        // Setup our routes
        HttpHandler routes = new RoutingHandler()
            .get("/", new IndexHandler(redis, ldap_url))
            .get("/view/{uuid}", new ViewHandler(redis, ldap_url))
            .get("/json/{uuid}", new JsonHandler(redis))
            .setFallbackHandler(HTTPServer::notFoundHandler);

        HttpHandler root = new AccessLogHandler(
            routes,
            new JBossLoggingAccessLogReceiver(),
            "combined",
            HTTPServer.class.getClassLoader()
        );

        // Start the server
        Undertow server = Undertow.builder()
            .addHttpListener(port, host, root)
            .build();

        server.start();
    }

    // 404 not found handler
    public static void notFoundHandler(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send("Not found.");
    }
}
