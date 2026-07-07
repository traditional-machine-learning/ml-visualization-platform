# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Machine Learning Algorithm Visualization Platform - An interactive educational platform for traditional ML algorithms (linear/logistic regression, decision trees, SVM, K-Means, PCA, Random Forest). Features step-by-step training visualization, decision boundary rendering, and an AI teaching assistant.

## Development Commands

### Backend (Spring Boot + Java 17)

```bash
cd 源代码/backend

# Run locally (requires MySQL)
mvn spring-boot:run

# Build JAR
mvn clean package -DskipTests
```

### Frontend (Angular 17)

```bash
cd 源代码/frontend

# Install dependencies
npm install

# Development server with proxy to backend
npm start          # or: ng serve --proxy-config proxy.conf.json

# Production build
npm run build

# Run tests
ng test
```

### Docker (Full Stack)

```bash
# Start all services (MySQL + backend + frontend)
cd 源代码
docker-compose up -d --build

# View logs
docker-compose logs -f backend

# Stop services
docker-compose down
```

Services run on:
- Frontend: http://localhost:80 (or 4200 dev)
- Backend: http://localhost:8080
- MySQL: localhost:3306

## Architecture

### Backend (Spring Boot 3.2)

**Tech Stack**: Spring Boot 3.2, MyBatis 3.0, MySQL 8.0, WebFlux (for AI API calls)

**Key Components**:
- `controller/` - REST endpoints, CORS configured for `/*`
- `service/TrainingSimulationService.java` - Core algorithm implementations (gradient descent, K-Means EM, decision tree splitting, PCA, Random Forest)
- `service/AiAssistantService.java` - LLM integration via WebClient
- `model/` - JPA entities (Algorithm, Dataset, TrainingSession)
- `repository/` - MyBatis @Mapper interfaces with @Select/@Insert annotations

**Data Flow**: Controller → Service → Repository → MySQL
- Training sessions are stateful (step-by-step execution, can pause/resume)
- Model data and metrics stored as JSON in training_session table

### Frontend (Angular 17)

**Key Architecture**:
- `experiment/experiment.component.ts` - Main orchestrator, owns training state machine
- `services/api.service.ts` - HTTP client with backend endpoints
- `services/learning-guide-data.service.ts` - Mock data fallback when API unavailable
- `components/visualization-canvas/` - ECharts rendering for decision boundaries and scatter plots

**State Management**: Component-level with services for shared state (ExperimentContextService)

**Dual Mode**: Frontend automatically falls back to local mock data if backend is unavailable, enabling standalone demos.

## Database Schema

Three main tables managed by MyBatis:
- `algorithm` - Stores ML algorithm definitions with JSON parameters
- `dataset` - Pre-loaded datasets with JSON data_content for visualization
- `training_session` - Active training state with JSON metrics/model_data

Schema initialized via `schema.sql` on startup. Pre-populated with Iris, Boston Housing, and synthetic datasets.

## Algorithm Implementations

All ML algorithms are simulated implementations in `TrainingSimulationService.java`:
- Linear/Logistic Regression: Gradient descent with configurable learning rate
- K-Means: EM-style iterative centroid updates
- Decision Tree: Greedy splitting on information gain/Gini impurity
- SVM: Simplified decision boundary optimization (supports linear/RBF/polynomial kernels)
- PCA: Covariance matrix eigen-decomposition
- Random Forest: Bootstrap sampling + feature randomization + ensemble voting

## AI Assistant

Powered by external LLM API configured in `application.yml` (endpoint, model, api-key). Uses WebClient to make async requests. Context includes current algorithm, dataset, training step, and loss values.

## Key Files

- `源代码/docker-compose.yml` - Full stack orchestration
- `源代码/backend/src/main/resources/application.yml` - Database + LLM configuration
- `源代码/backend/src/main/resources/schema.sql` - Database schema
- `源代码/frontend/src/app/experiment/experiment.component.ts` - Main UI logic
- `源代码/frontend/src/app/components/visualization-canvas/` - Chart rendering
