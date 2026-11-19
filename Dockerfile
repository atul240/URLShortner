#step 1 : copy java image
FROM eclipse-temurin:17-jdk-alpine

#step 2 : application folder
WORKDIR /app

#step 3 : copy pom.xml
COPY pom.xml .

#step 4 : copy maven wrapper
COPY mvnw .
COPY .mvn .mvn

#step 5 : download dependencies
RUN ./mvnw dependency:go-offline -B

#step 6 : copy entire code
COPY src ./src

#step 7 : create jar
RUN ./mvnw package -DskipTests

#step 8 : expose port
EXPOSE 8080

#step 9 : run application
CMD ["java", "-jar", "target/URLShortner-4.1.2.jar"]