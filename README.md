# squirrel
Continuous integration server

Squirrel is a Nut companion server.
It manages projects building and status.

## Build
```
nut build
```
or
```
mvn clean install
```

## Run
```
nut run
```
or
```
mvn exec:java
```
or
```
java -jar target/squirrel-1.0-SNAPSHOT-jar-with-dependencies.jar ab.squirrel.main
```

## Specifications

Project status

status (color), name, last success, duration, last failure, duration 

## Server API

- GET 
- PUT

