## D1: Partial-update API on AgentRegistry

**Choice:** Default method on AgentRegistry interface
**Alternatives:**
- Abstract method — forces all implementations to provide their own, but breaks NoOpAgentRegistry and downstream impls
- Static utility helper — zero API change but implementations can never optimize the path
**Rationale:** A default method on the interface provides a read-modify-write implementation (findById → rebuild goals → register) that works everywhere, while allowing InMemoryAgentRegistry and JpaAgentRegistry to override with optimized paths. NoOpAgentRegistry in engine-runtime-core inherits a safe default (findById returns empty → no-op).
**Trade-offs:** Default path is not atomic for JPA (read-modify-write), but the window is tiny and consequences are transient — the overwritten change re-applies on the next tick. JPA override can use a direct SQL UPDATE on the goals table for atomicity.
**Sources:** eidos-api AgentRegistry.java, JpaAgentRegistry.java, InMemoryAgentRegistry.java, NoOpAgentRegistry (engine-runtime-core)
**Exploration:** quick
**Status:** captured

## D2: GoalLifecycleProvider implementation location

**Choice:** blocks (blocks-core module)
**Alternatives:**
- neocortex bridge module — keeps impl near SPI but crosses the neocortex→eidos boundary, which doesn't exist today
- eidos bridge module — same boundary problem in reverse (eidos→neocortex)
**Rationale:** blocks-core already depends on both eidos-api and neocortex mindmap-api. It's the composition root that wires them together. The implementation is a thin adapter (~10 lines) that reads AgentGoal.lifecycleState from AgentDescriptor and maps to the status string expected by GoalResolutionPhase.sync().
**Trade-offs:** A neocortex SPI implementation living in blocks is architecturally inverted (provider in a consumer), but this is the standard SPI-provider pattern — the SPI defines the contract, the integrating layer provides it.
**Sources:** GoalLifecycleProvider.java (mindmap-api), NoOpGoalLifecycleProvider.java (mindmap), SocialAvatarCognition.java (blocks), GoalResolutionPhase.java (mindmap-intelligence)
**Exploration:** quick
**Status:** captured
