#!/bin/sh

if [ "$#" -ne 1 ]; then
  echo "error: usage: $0 [<user>]" >&2
  exit 1
fi

USER=$1

# Compile
mvn clean package -DskipTests

# Upload to /tmp
scp ./target/log4shell-jar-with-dependencies.jar "$USER@log4shell.huntress.com:/tmp/log4shell.jar"

# Move to /opt with sudo
ssh -t "$USER@log4shell.huntress.com" sudo mv /tmp/log4shell.jar /opt/log4shell.jar

# Restart the service
ssh -t "$USER@log4shell.huntress.com" sudo systemctl restart log4shell
