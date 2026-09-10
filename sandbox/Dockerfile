FROM eclipse-temurin:21-jdk-alpine

RUN addgroup -S sandbox && adduser -S sandbox -G sandbox

WORKDIR /projet

USER sandbox

CMD ["ls", "-la"]