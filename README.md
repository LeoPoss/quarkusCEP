# DeclareCEP: A Unified Engine for Declarative Process Specifications and Event Processing

## Project Overview
This implementation demonstrates a novel approach for integrating Business Process Management (BPM) and Complex Event Processing (CEP) to enable flexible, declarative process execution with high-frequency Internet of Things (IoT) data. It introduces a unified engine paradigm that leverages a single CEP engine for both event abstraction and the direct execution of MP-Declare models. This simplifies architectures, reduces latency, and enables responsive, event-driven execution by eliminating the need for dedicated preprocessing middleware.

The project consists of several key components:
1. A Java-based backend application integrating a CEP engine.
2. A React-based web frontend application for user interaction and visualization.
3. An event abstraction framework that translates declarative constraints into executable CEP queries.

## Features
- Unified Engine Paradigm: Integrates event abstraction and declarative process execution within a single CEP engine, eliminating separate middleware.
- Direct MP-Declare Execution: Enables direct enactment of MP-Declare models over high-frequency event streams (e.g., IoT data).
- Multi-Level Event Abstraction: Processes events across three abstraction layers: Atomic Events, Constraint Level Events, and Process Level Events.
- Real-time Process Compliance: Continuously monitors and enforces declarative constraints, ensuring process compliance in dynamic environments.
- Reduced Complexity & Latency: Streamlines system architecture by removing intermediary layers, leading to lower latency and simplified management.
- Flexible Process Execution: Supports runtime adaptation of process instances based on real-time event data, reacting to deviations and changing conditions.
- Separation of Concerns: Decouples process logic (declarative constraints) from event processing, allowing independent evolution.

## Conceptual Overview: How it Works
Our approach is founded on a three-tiered event abstraction framework that seamlessly integrates declarative process logic with real-time event streams:

1. **Atomic Events**: These are the lowest-level events, typically originating from IoT devices or system logs. Our system detects specific "activation" and "target" events from these raw streams for each defined declarative constraint.
2. **Constraint Level Events**: Upon detection of atomic activation/target events, the system generates higher-level "constraint level" events. This layer manages the stateless status of individual constraints, including their activation, fulfillment, and potential violation, by examining and querying the continuous flow of these abstract events. This layer is crucial for handling complex temporal relations and ambiguities inherent in declarative constraints.
3. **Process Level Events**: At the highest abstraction, "process level" events represent the real-time status of the overall process instance and its constituent constraints. This provides insights into the current state of the process, indicating which tasks are currently available or if any constraints have been fulfilled or violated.

This multi-level abstraction allows for the systematic translation of MP-Declare constraints into executable CEP queries (specifically Esper EPL), enabling the CEP engine to directly evaluate and enforce these constraints against incoming event data.

## Technologies Used
- Backend: Java, Quarkus, Esper CEP Engine
- Frontend: Next.js (React)
- Process Modeling: MP-Declare (declarative constraints)

## Setup and Running
To set up and run the DeclareCEP proof-of-concept locally:

### Prerequisites
- Java Development Kit (JDK): Version 21 or newer.
- Gradle: For building the backend.
- Node.js & npm/yarn: For building the frontend.
- Git: For cloning the repository.

### Installation and Setup
1. Clone this repository:
    ```console
    git clone https://anonymous.4open.science/r/quarkus-CEP
    cd quarkus-CEP
    ```

2. Start backend:
    ```console
    cd backend
    ./gradlew quarkusDev
    ```
   The backend server will typically run on http://localhost:8080.

3. Set up and run the frontend:
    ```console
    cd ../frontend
    bun install # or npm install
    bun dev # or npm run dev
    ```
    The frontend application will typically be accessible at http://localhost:3000.

## License
This project is licensed under the GNU GPLv3 License -- see the LICENSE file for details.
