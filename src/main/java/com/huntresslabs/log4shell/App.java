package com.huntresslabs.log4shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import io.lettuce.core.RedisClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import redis.embedded.RedisServer;

/**
 * Main application code
 */
@Command(name = "log4shell-tester", mixinStandardHelpOptions = true, version = "0.1.0", description = "Execute the Huntress Log4Shell-Tester HTTP and LDAP servers.")
public class App implements Callable<Integer> {
    private static final Logger logger = Logger.getLogger(App.class);

    @Option(names = {"--hostname"}, defaultValue = "127.0.0.1", description = "The publicly routable IP address or resolvable hostname of the server (default: 127.0.0.1).")
    private String hostname;

    @Option(names = {"--http-port"}, defaultValue = "8000", description = "Port to listen for HTTP connections (default: 8000)")
    private int http_port;

    @Option(names = {"--ldap-port"}, defaultValue = "1389", description = "Port to listen for LDAP connections (default: 1389)")
    private int ldap_port;

    @Option(names = {"--ldap-host"}, defaultValue = "0.0.0.0", description = "IP address on which to listen for LDAP connections (default: 0.0.0.0)")
    private String ldap_host;

    @Option(names = {"--http-host"}, defaultValue = "127.0.0.1", description = "IP address on which to listen for HTTP connections (default: 127.0.0.1)")
    private String http_host;

    @Option(names = {"--redis-url"}, defaultValue = "redis://localhost:6379", description = "Connection string for the Redis cache server (default: redis://localhost:6379)")
    private String redis_url;

    @Option(names = {"-c", "--config"}, description = "Path to YAML configuration file (overrides commandline options).")
    private File config_file;

    @Option(names = {"--useEmbeddedRedis"}, description = "Use redis server embedded in Java jar?", defaultValue = "false")
    private boolean useEmbeddedRedis;

    @Option(names = {"--embeddedRedisPort"}, defaultValue = "6379")
    private int embeddedRedisPort;

    public static void main(String[] args) {
        new CommandLine(new App()).execute(args);
    }

    public static String readResource(String name) {
        ClassLoader cLoader = App.class.getClassLoader();

        return new BufferedReader(new InputStreamReader(cLoader.getResourceAsStream(name))).lines().collect(Collectors.joining("\n"));
    }

    // Parse configuration options from a YAML file instead
    private void parseConfig() {

        Yaml yaml = new Yaml();
        Map<String, Object> config;

        try {
            config = yaml.load(new FileInputStream(config_file));
        } catch (FileNotFoundException e) {
            logger.errorf("config: %s: not found", config_file);
            System.exit(1);
            return;
        }

        try {
            this.http_host = (String) config.getOrDefault("http_host", this.http_host);
        } catch (ClassCastException e) {
            logger.error("http_host: must be a string");
            System.exit(1);
        }

        try {
            this.http_port = (Integer) config.getOrDefault("http_port", this.http_port);
        } catch (ClassCastException e) {
            logger.error("http_port: must be an integer");
            System.exit(1);
        }

        try {
            this.ldap_host = (String) config.getOrDefault("ldap_host", this.ldap_host);
        } catch (ClassCastException e) {
            logger.error("ldap_host: must be a string");
            System.exit(1);
        }

        try {
            this.ldap_port = (Integer) config.getOrDefault("ldap_port", this.ldap_port);
        } catch (ClassCastException e) {
            logger.error("ldap_port: must be an integer");
            System.exit(1);
        }

        try {
            this.redis_url = (String) config.getOrDefault("redis_url", this.redis_url);
        } catch (ClassCastException e) {
            logger.error("redis_url: must be a string");
            System.exit(1);
        }

        try {
            this.hostname = (String) config.getOrDefault("hostname", this.hostname);
        } catch (ClassCastException e) {
            logger.error("hostname: must be a string");
            System.exit(1);
        }
    }

    @Override
    public Integer call() throws Exception {

        // Parse the configuration file
        if (config_file != null) {
            logger.info("parsing configuration file");
            parseConfig();
        }

        // Construct the LDAP url
        String ldap_url = "ldap://" + hostname + ":" + ldap_port;

        RedisServer redisserver = null; //from https://github.com/ozimov/embedded-redis

        if (useEmbeddedRedis) {
            System.out.printf("Starting an embedded Redis server on %d%n", embeddedRedisPort);
            redisserver = new RedisServer(embeddedRedisPort);

            try {
                redisserver.start();
            } catch (java.lang.RuntimeException re) {
                System.out.println("Possible problem due to https://github.com/kstyrc/embedded-redis/issues/51");
                System.out.println("If you are running windows, please allocate 10GB of pagefile and reboot.");
                System.out.println("[WIN-R -> sysdm.cpl] to open dialog.");
                throw re;
            }
        }


        // Create the redis connection manager
        logger.info("connecting to redis database");
        RedisClient redis = RedisClient.create(redis_url);

        //test connection early
        redis.connect();

        // Run the HTTP server
        logger.infof("starting http server listening on %s:%d", http_host, http_port);
        HTTPServer.run(http_host, http_port, redis, ldap_url);

        // Run the LDAP server
        logger.infof("starting ldap server listening on %s:%d", ldap_host, ldap_port);
        LDAPServer.run(ldap_host, ldap_port, redis);

        return 0;
    }
}
