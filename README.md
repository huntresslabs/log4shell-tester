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
remote IP address in the Redis cache for you to lookup later.

If any of your clients do reach out, you can view the timestamps and external IP
addresses at your specific "view" URL (presented through a button on the index
page).

All entries in the cache of a 30 minute time-out. This means that 30 minutes after
your last request, all results will be gone from the Redis cache forever.

## Building

You can build a JAR file with the following command:

```sh
mvn clean package
```

You will then have a file named `target/log4shell-jar-with-dependencies.jar`
which contains all required dependencies as well as the testing application.

## Runtime Requirements

The application is self-contained in the generated JAR file, however it does
require a Redis cache server at runtime. The URL for the redis cache is
specified through command line arguments. The cache server will hold valid
UUIDs for users as well as track known "hits" for the LDAP endpoint.

## Running

The JAR file has no defined Main Class, however you can run the `App` class directly
with the following arguments:

```sh
java -cp target/log4shell-jar-with-dependencies.jar \
         com.huntress.log4shell.App \
         <public_hostname> \
         <http_port> \
         <ldap_port> \
         [<redis_url>]
```

The application requires the public hostname because it generates a payload
to test the Log4Shell vulnerability which must include the publicly resolvable
hostname or IP address of the testing instance itself. The HTTP and LDAP ports
are fairly self-explanatory. The Redis URL is optional, and defaults to
`redis://localhost:6379`.
