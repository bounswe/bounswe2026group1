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
   JWT_SECRET_KEY=your_jwt_secret_key
   AWS_BUCKET_NAME=your_aws_bucket_name
   AWS_REGION=your_aws_region
   AWS_ACCESS_KEY=your_aws_access_key
   AWS_SECRET_KEY=your_aws_secret_key
   ORS_API_KEY=your_openrouteservice_key
   ```
2. Start the database:
   ```bash
   docker compose up -d db
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


## Common Issues 
1. `mvnw` Permission Denied
- **Cause:** File not executable
- **Fix:**
```bash
  chmod +x mvnw
```

2. JAVA_HOME Not Defined
- **Cause:** Java not installed
- **Fix:**
```bash
  sudo apt install openjdk-21-jdk
```
  Add to `~/.zshrc`:
```bash
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  export PATH=$JAVA_HOME/bin:$PATH
```

3. `.env` Not Loaded by Spring
- **Cause:** Spring `file:.env[.properties]` import not resolving correctly
- **Fix:** Export variables manually before running:
```bash
  export $(cat .env | xargs) && ./mvnw spring-boot:run
```

4. `WeakKeyException` — JWT Secret Too Short
- **Cause:** `JWT_SECRET_KEY` minimum is 256 bits
- **Fix:** Generate a secure key and update `.env`:
```bash
  openssl rand -base64 32
```
```properties
  JWT_SECRET_KEY=
```