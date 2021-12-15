# Huntress Log4Shell Testing Application

This repo holds the source for the HTTP and LDAP servers hosted [here](https://log4shell.huntress.com).
Both services are hosted under one Java application built here with
maven.

We have released the source code of this application to promote
transparency, and let researchers verify for themselves that our
service does nothing nefarious.

> :warning: **The application does not trigger any remote code execution**.

> :bangbang: **This tool is intended for use by authorized persons or researchers only.** You should only test systems on which you have explicit permission or authority. If you find vulnerable applications or libraries, you should exercise [responsible disclosure](https://www.cisa.gov/coordinated-vulnerability-disclosure-process).

## How does it work?

In short, the Log4Shell vulnerability normally works by injecting a JNDI LDAP string
into your logs, which triggers Log4j to reach out to the specified LDAP server looking
for more information. In a malicious scenario, the LDAP server can then serve code back
to the victim machine which will be automatically executed in-memory.

This application has two parts. The first is an HTTP server which generates a random
UUID which uniquely identifies your session/testing, and presents you with a payload
which can be used to test for the Log4Shell vulnerability.

You can then paste this payload into various inputs on your application (form fields,
input boxes, User-Agent strings, etc.). If the application is vulnerable, it will reach
out to our LDAP server.

The second part of this application is the LDAP server itself. This LDAP server is
run out of the same process. After receiving a connection from a vulnerable client,
it will immediately respond with an LDAP Operation Error. **No code is sent from
our LDAP server to your client**. You can see this interaction in `LDAPServer.java`.
After sending the error, the LDAP server will simply log the UTC timestamp and the
remote IP address in the cache for you to lookup later.

If any of your clients do reach out, you can view the timestamps and external IP
addresses at your specific "view" URL (presented through a button on the index
page).

All entries in the cache have a 30 minute time-out. This means that 30 minutes after
your last request, all results will be gone from the cache forever.

## Building

You can build a JAR file with the following command:

```sh
mvn clean package
```

You will then have a file named `target/log4shell-jar-with-dependencies.jar`
which contains all required dependencies as well as the testing application.

## Running

The JAR file can be executed directly. Configuration can be passed via command
line arguments or a YAML configuration file. The hostname argument is used to
construct the testing payloads given to the user and must be an IP address or
resolvable domain name which can be reached from the victim server or application
which you are testing.

``` sh
# Help/usage details
$ java -jar target/log4shell-jar-with-dependencies.jar --help
Usage: log4shell-tester [-hV] [-c=<config_file>] [--hostname=<hostname>]
                        [--http-host=<http_host>] [--http-port=<http_port>]
                        [--ldap-host=<ldap_host>] [--ldap-port=<ldap_port>]
Execute the Huntress Log4Shell-Tester HTTP and LDAP servers.
  -c, --config=<config_file>
                  Path to YAML configuration file (overrides commandline
                    options).
  -h, --help      Show this help message and exit.
      --hostname=<hostname>
                  The publicly routable IP address or resolvable hostname of
                    the server (default: 127.0.0.1).
      --http-host=<http_host>
                  IP address on which to listen for HTTP connections (default:
                    127.0.0.1)
      --http-port=<http_port>
                  Port to listen for HTTP connections (default: 8000)
      --ldap-host=<ldap_host>
                  IP address on which to listen for LDAP connections (default:
                    0.0.0.0)
      --ldap-port=<ldap_port>
                  Port to listen for LDAP connections (default: 1389)
  -V, --version   Print version information and exit.
  
# Example invocation listening on 127.0.0.1 for HTTP (default).
#   This is recommended if running publicly so you can setup
#   a proxy like nginx to handle SSL publicly.
$ java -jar target/log4shell-jar-with-dependencies.jar \
   --hostname my-log4shell-tester.something.com \
   --http-host 127.0.0.1 \
   --http-port 8000 \
   --ldap-host 0.0.0.0 \
   --ldap-port 1389 \
   
# Example invocation allowing HTTP inbound externally
$ java -jar target/log4shell-jar-with-dependencies.jar \
   --hostname my-log4shell-tester.something.com \
   --http-host 0.0.0.0 \
   --http-port 8000 \
   --ldap-host 0.0.0.0 \
   --ldap-port 1389 \
   
# Example invocation with a configuration file .
$ java -jar target/log4shell-jar-with-dependencies.jar \
   --config /path/to/log4shell/config.yaml
```

## Configuration File

The configuration file is a YAML document that provides the same options as the
command line arguments, but in `snake_case` instead of `kabab-case`. An example
configuration file with the default values looks like:

> :warning: Any configurations specified in the configuration file will override command-line arguments.
   
``` yaml
# These represent the default values
http_host: 127.0.0.1
http_port: 8000
ldap_host: 0.0.0.0
ldap_port: 1389
hostname: 127.0.0.1
```

