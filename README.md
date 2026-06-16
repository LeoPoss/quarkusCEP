# DeclareCEP Artifact

Companion repository contains the full implementation of the unified CEP engine, the web frontend, and replication instructions.

## Artifact Structure

```
.
├── backend/          Java/Quarkus CEP engine (Esper EPL translation, constraint management)
├── frontend/         Next.js 16 web frontend (process visualization, signal injection, task analysis)
├── docker-compose.yml
└── README.md
```

## Abstract

We collapse event abstraction and declarative process execution into a single CEP engine, removing the preprocessing middleware layer found in prior architectures. MP-Declare constraints translate directly to Esper EPL queries over three abstraction tiers: atomic events, constraint-level events, and process-level events. The engine handles real-time compliance monitoring, automated constraint enforcement through background task execution, and interactive signal injection for debugging.

## Key Claims

1. **Unified Engine Architecture.** Event abstraction and constraint enactment coexist within one Esper runtime, reducing architectural complexity and end-to-end latency compared to multi-engine designs.
2. **Direct MP-Declare Translation.** Declarative constraints (Precedence, Response, Not-CoExistence, etc.) are systematically translated to EPL queries without intermediate DSLs or middleware queues.
3. **Automated Enforcement.** Activation events trigger downstream task injection and lifecycle-logged background execution.

## Three-Tier Event Abstraction

1. **Atomic Events** — raw IoT/system events; activation/target patterns detected per constraint.
2. **Constraint Level** — stateless per-constraint status (activation, fulfillment, violation) via continuous EPL queries.
3. **Process Level** — real-time instance-wide state: currently allowed tasks, fulfilled/violated constraints.

## Technologies

| Layer    | Stack                                              |
|----------|----------------------------------------------------|
| Backend  | Java 21, Quarkus, Esper CEP                        |
| Frontend | Next.js 16 (React 19), HeroUI v3, Tailwind CSS v4, TanStack Query |
| Modeling | MP-Declare                                         |

## Replication

### Prerequisites

- JDK 21+
- Gradle
- Node.js + bun
- Docker (optional)

### Running Locally

```bash
# Backend
cd backend && ./gradlew quarkusDev        # → http://localhost:8080

# Frontend
cd frontend && bun install && bun dev     # → http://localhost:3000
```

### Docker

```bash
cd backend && ./gradlew build
cd .. && docker compose up --build
```

## License

GNU GPLv3. See LICENSE.
