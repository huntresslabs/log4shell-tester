package com.huntresslabs.log4shell;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.time.Instant;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;

import org.jboss.logging.Logger;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * LDAP Server Thread which caches validated requests
 **/
public class LDAPServer
{
    private static final Logger logger = Logger.getLogger(LDAPServer.class);

    private static void log_attempt(String address, String uuid, Boolean valid) {
        logger.infof("ldap query with %s uuid \"%s\" received from %s; dropping request.", valid ? "valid" : "invalid", uuid, address);
    }

    public static void run(String host, int port, RedisClient redis) {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(
                "dc=example,dc=com"
            );

            config.setListenerConfigs(new InMemoryListenerConfig(
                "listen",
                InetAddress.getByName(host),
                port,
                ServerSocketFactory.getDefault(),
                SocketFactory.getDefault(),
                (SSLSocketFactory)SSLSocketFactory.getDefault()
            ));

            config.addInMemoryOperationInterceptor(new InMemoryOperationInterceptor() {
                @Override
                public void processSearchResult ( InMemoryInterceptedSearchResult result ) {
                    String key = result.getRequest().getBaseDN();
                    StatefulRedisConnection<String, String> connection = redis.connect();

                    try {
                        RedisCommands<String, String> commands = connection.sync();
                        LDAPListenerClientConnection conn;

                        // Send an error response regardless
                        result.setResult(new LDAPResult(0, ResultCode.OPERATIONS_ERROR));

                        // This is a gross reflection block to get the client address
                        try {
                            Field field = result.getClass().getSuperclass().getDeclaredField("clientConnection");
                            field.setAccessible(true);

                            conn = (LDAPListenerClientConnection)field.get(result);
                        } catch ( Exception e2 ) {
                            e2.printStackTrace();
                            return;
                        }

                        // Build the resulting value, storing the UTC timestamp and the requestor address
                        String when = Instant.now().toString();
                        String addr = conn.getSocket().getInetAddress().toString().replaceAll("^/", "");
                        String value = addr + "/" + when;
                        Boolean valid = (commands.exists(key) != 0);

                        // Log any requests
                        log_attempt(addr, key, valid);

                        // Ignore requests with invalid UUIDs
                        if ( ! valid ) {
                            return;
                        }

                        // Store this result
                        commands.lpush(key, value);

                        // Keys expire after 30 minutes from creation...
                        // commands.expire(key, 1800);
                    } finally {
                        connection.close();
                    }
                }
            });
            InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);

            ds.startListening();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
