# Mapcess - CMPE354 Group 1 Project

Welcome to the Mapcess repository. This document provides step-by-step instructions to build, configure, and deploy the application (Backend, Web Frontend, and Mobile) from scratch.

Even if you are completely unfamiliar with Docker or this specific application stack, you will be able to run this project seamlessly by following the instructions below.

---

## 📋 Prerequisites

Before you begin, ensure you have the following installed on your machine:
* **Docker**: Required to run the Backend, Web Frontend, and Database.
* **Git**: To clone this repository.
* **Flutter SDK**: Required ONLY if you want to build and run the Mobile application.

---

## ⚙️ 1. Environment Variables Configuration

The Docker infrastructure relies on environment variables to securely set up the database and backend. You must create an environment configuration file and provide your own credentials and API keys before starting the setup.

1. Open the root directory of this project (`bounswe2026group1`).
2. Create a new file exactly named: `.env`
3. Copy the following template into your `.env` file and **replace the placeholder values** with your actual credentials:

```properties
# --- Database Configuration ---
POSTGRES_DB=mapcess
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<YOUR_SECURE_DATABASE_PASSWORD>

# --- Backend Security Keys ---
# JWT Secret Key must be at least 32 characters long plain text or Base64 encoded.
JWT_SECRET_KEY=<YOUR_STRONG_JWT_SECRET_KEY>

# The API key required for clients to authenticate their requests.
APP_API_KEY=<YOUR_APP_API_KEY>

# --- 3rd Party Services ---
# Required for map routing, AWS S3 file uploads, etc.
# Leave them blank ONLY if you are not testing these specific features.
ORS_API_KEY=<YOUR_OPEN_ROUTE_SERVICE_API_KEY>
AWS_BUCKET_NAME=<YOUR_AWS_S3_BUCKET_NAME>
AWS_REGION=<YOUR_AWS_REGION>
AWS_ACCESS_KEY=<YOUR_AWS_ACCESS_KEY>
AWS_SECRET_KEY=<YOUR_AWS_SECRET_KEY>
```
> **Note:** The `docker-compose.local.yml` file will automatically read these values and inject them into the containers securely.

---

## 🐳 2. Running Web & Backend via Docker

This project uses Docker Compose to containerize the **PostgreSQL Database**, **Spring Boot Backend**, and **React (Vite) Frontend** simultaneously.

### Step-by-Step Deployment:

1. Open your terminal or command prompt.
2. Navigate to the root folder of this repository:
   ```bash
   cd path/to/bounswe2026group1
   ```
3. Run the following command to build the images and start the containers. *(This might take a few minutes during the first run as it downloads basic images and compiles the Java backend).*
   ```bash
   docker compose -f docker-compose.local.yml up --build -d
   ```
   *The `-d` flag runs the containers in detached mode (background).*

### Verify Deployment:
Once the command finishes and services start, you can access the applications locally in your web browser:
* 🌐 **Web Frontend:** [http://localhost:5173](http://localhost:5173) *(Features Hot-Reload)*
* ⚙️ **Backend API:** [http://localhost:8080/api/reports](http://localhost:8080/api/reports) *(Test Endpoint)*
* 🗄️ **Database:** `localhost:5432` *(Credentials match your `.env` file)*

### Stopping the Services:
When you are done testing, you can stop all containers gracefully by running:
```bash
docker compose -f docker-compose.local.yml down
```

---

## 📱 3. Running the Mobile Application

The mobile application is built using **Flutter**. Since mobile apps are executed in an emulator or a physical device, it does not run inside the Docker Compose network.

### Step-by-Step Deployment:

1. Ensure **Docker is running** (Section 2) so the Mobile app can communicate with the local backend.
2. Open a terminal and navigate to the mobile directory:
   ```bash
   cd path/to/bounswe2026group1/mobile
   ```
3. Fetch the required Flutter dependencies:
   ```bash
   flutter pub get
   ```
4. Start the application on your connected device or running emulator (iOS Simulator / Android Emulator):
   ```bash
   flutter run
   ```

> **Important Note for Mobile Local Testing:**
> If you are using an **Android Emulator**, it accesses your computer's localhost via `10.0.2.2`.
> If you are using an **iOS Simulator**, it accesses localhost via `127.0.0.1`.
> Ensure the API Base URL inside your Flutter source code points to the correct IP address corresponding to your emulator for seamless backend communication.

---

## 🛠️ Troubleshooting & Logs

If something isn't working as expected, you can view the live logs of the containers via the terminal:

* **Backend Logs:** `docker logs -f mapcess_backend_dev`
* **Frontend Logs:** `docker logs -f mapcess_frontend_dev`
* **Database Logs:** `docker logs -f mapcess_db_dev`

*(Press `Ctrl + C` to exit the log viewer).*
