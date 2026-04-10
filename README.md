# TaskFlow Backend (Java 21)

A containerized task management service built with Spring Boot 3.2.

## Tech Stack
- **Runtime:** Java 21 (Temurin)
- **Framework:** Spring Boot 3.2.x
- **Persistence:** PostgreSQL 15 + Flyway Migrations
- **Security:** Spring Security + JWT (HS512)
- **Build:** Maven (Multi-stage Docker)

## Getting Started

### 1. Environment Setup
Copy the example env file and fill in your secrets:
```bash
cp .env.example .env