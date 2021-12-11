FROM maven AS build

# Build the full JAR file
COPY . /log4shell
WORKDIR /log4shell
RUN mvn clean package

FROM openjdk:18-alpine

# Final image only contains the JAR file
COPY --from=build /log4shell/target/log4shell-jar-with-dependencies.jar /log4shell.jar

# Entrypoint, you must provide arguments
ENTRYPOINT ["java", "-cp", "/log4shell.jar", "com.huntresslabs.log4shell.App"]
