# FIX Protocol Trading Simulator

A FIX 4.4 trading simulator built with [QuickFIX/J](http://www.quickfixj.org/)
and Spring Boot 3.

> Work in progress.

## Goal

Submit orders through a REST API, route them to a mock exchange over a real
FIX 4.4 session, and track order state as execution reports come back.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| FIX Version | FIX 4.4 |
| Build | Maven 3.8+ |

## Run

```bash
mvn spring-boot:run
```
