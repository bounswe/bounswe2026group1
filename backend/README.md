# Backend

Spring Boot 4.0 / Java 21 / PostgreSQL

## Prerequisites

- Java 21+
- Docker & Docker Compose

## Setup

1. Copy the example env file and fill in your credentials:
   ```bash
   cp .env.example .env
   ```
   Edit `.env`:
   ```
   POSTGRES_DB=bounswedb
   POSTGRES_USER=your_username
   POSTGRES_PASSWORD=your_password
   ```

2. Start the database:
   ```bash
   docker compose up -d
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```
   On Windows:
   ```bash
   mvnw.cmd spring-boot:run
   ```

> `.env` is loaded automatically by both Docker Compose and the application. Never commit `.env` — use `.env.example` as the template.

The app will be available at `http://localhost:8080`.

## Running Tests

```bash
./mvnw test
```
