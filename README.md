# AI Ticket System

AI-powered intelligent ticket management system.

## Tech Stack

- **Backend**: Spring Boot 3.3.x (Java 21)
- **AI Framework**: LangChain4j 0.35.x
- **LLM**: MiniMax Token Plan
- **Vector DB**: pgvector (PostgreSQL 16)
- **Cache**: Redis 7.x
- **Message Queue**: Kafka 3.x
- **Gateway**: Spring Cloud Gateway
- **Frontend**: React + TypeScript + Ant Design (coming soon)

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  API Gateway (8080)              │
└─────────────────────┬───────────────────────────┘
                      │
    ┌─────────────────┼─────────────────┐
    │                 │                 │
    ▼                 ▼                 ▼
┌─────────┐     ┌──────────┐     ┌──────────┐
│  User   │     │  Ticket  │     │    AI    │
│ Service │     │ Service  │     │ Service  │
│  (8081) │     │  (8082)  │     │  (8083)  │
└────┬────┘     └────┬─────┘     └────┬─────┘
     │               │                │
     └───────────────┼────────────────┘
                     ▼
        ┌────────────────────────┐
        │   PostgreSQL (5432)   │
        │   + pgvector          │
        └────────────────────────┘
```

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d
```

### 2. Build Project

```bash
./mvnw clean install
```

### 3. Run Services

```bash
# Terminal 1: User Service
cd user-service && ../mvnw spring-boot:run

# Terminal 2: Ticket Service
cd ticket-service && ../mvnw spring-boot:run

# Terminal 3: AI Service
cd ai-service && ../mvnw spring-boot:run

# Terminal 4: API Gateway
cd api-gateway && ../mvnw spring-boot:run
```

### 4. Access Services

- Gateway: http://localhost:8080
- User Service: http://localhost:8081
- Ticket Service: http://localhost:8082
- AI Service: http://localhost:8083

## API Endpoints

### Auth
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/login` - User login

### Users
- `GET /api/v1/users` - List users
- `GET /api/v1/users/{id}` - Get user
- `GET /api/v1/users/handlers` - List handlers
- `PUT /api/v1/users/{id}/skills` - Update user skills

### Tickets
- `POST /api/v1/tickets` - Create ticket
- `GET /api/v1/tickets` - List tickets
- `GET /api/v1/tickets/{id}` - Get ticket
- `PUT /api/v1/tickets/{id}` - Update ticket
- `DELETE /api/v1/tickets/{id}` - Delete ticket
- `POST /api/v1/tickets/{id}/comment` - Add comment
- `PUT /api/v1/tickets/{id}/assign` - Assign handler
- `PUT /api/v1/tickets/{id}/resolve` - Resolve ticket
- `PUT /api/v1/tickets/{id}/close` - Close ticket

### AI
- `POST /api/v1/ai/classify` - Classify ticket content
- `POST /api/v1/ai/priority` - Evaluate priority
- `POST /api/v1/ai/recommend-handler` - Recommend handler
- `POST /api/v1/ai/summary` - Generate summary
- `POST /api/v1/ai/suggest-reply` - Suggest reply
- `POST /api/v1/ai/embedding` - Generate embedding

### Notifications
- `GET /api/v1/notifications/subscribe` - Subscribe to notifications (SSE)

## Environment Variables

```bash
# MiniMax API Key (required for AI features)
MINIMAX_API_KEY=your_api_key_here
```

## Project Structure

```
ai-ticket-system/
├── api-gateway/        # API Gateway service
├── user-service/       # User & auth service
├── ticket-service/     # Ticket management service
├── ai-service/         # AI processing service
├── common/             # Shared code
├── db/
│   └── schema.sql      # Database schema
├── docker-compose.yml  # Local development setup
└── pom.xml            # Parent POM
```
