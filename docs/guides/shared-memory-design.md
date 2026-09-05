# Shared and Partitioned Memory — Multi-Agent Cognitive Spaces

Multi-agent memory for families, teams, and organisations. The defining design constraint: memory spaces are a **cross-cutting architectural property**, not a feature bolted on after the single-agent model is complete. Every API, every query model, every confidence/temporal/affective decision must account for visibility and perspective from the start.

---

## The Problem

The current neocortex model: **one tenant = one agent = fully isolated memory**. Every store method takes `tenantId` and enforces strict isolation. This works for a single autonomous agent.

For casehub-life (personal life automation for families), we need:

| Requirement | Example | Current support |
|---|---|---|
| **Private memory** | Health concerns, work stress, personal finances | Tenant isolation — works |
| **Shared memory** | Family calendar, household tasks, shared history | No — each tenant is a silo |
| **Selective sharing** | Alice told Bob about the job offer, but not the kids | No — all-or-nothing per tenant |
| **Perspectival memory** | Family dinner: Alice loved it (pleasure=0.9), teenager hated it (pleasure=-0.2) | No — PAD is on the node, not per-viewer |
| **Shared graph, private annotations** | "Grandma" node exists for the family, but each member has their own emotional relationship | No — one PAD per node |
| **Privacy-aware erasure** | Erasing Alice's data shouldn't erase shared family memories that mention her | Partial — `eraseEntityAcrossTenants` exists but doesn't distinguish shared vs private |
| **Temporal group membership** | Kids grow up, partners separate — access changes over time | No |

---

## Current Tenant Model — What We Have

| Capability | Where | Detail |
|---|---|---|
| Per-method `tenantId` | All stores | Strict isolation — every CRUD method is tenant-scoped |
| `eraseEntityAcrossTenants(name, Set<String> tenantIds)` | MindMapStore, CaseMemoryStore | Cross-tenant GDPR erasure — requires caller to supply tenant list |
| `discoverTenants()` | CaseMemoryStore | Programmatic tenant discovery — can find all tenants that have data |
| `MindMapQuery.tenantId` | MindMapStore | Query is always single-tenant |
| `NodeRef(scheme, id, qualifier)` | MindMapStore | Cross-store references — but not cross-tenant |
| `SubgraphType` | MindMapStore | Partitions knowledge by category, not by visibility |

**The gap:** There is no concept of a memory space that spans multiple tenants, no per-viewer annotations, and no cross-tenant query path. Within a tenant, #277 delivered principal-scoped visibility (principalId, sharedWith, callerPrincipalId) — queries can now return "my private + shared with me" in one call. The cross-tenant concern remains open.

---

## Design: Memory Spaces as a Cross-Cutting Concern

### Why Not a Late Phase

If we design unified confidence (Phase 1a), builder APIs (Phase 1b), temporal models (Phase 2), and affective systems (Phase 3) without accounting for memory spaces, we'll have to redo them all. Specifically:

- **Confidence** must be space-aware: shared knowledge has collective confidence (multiple observers strengthen it), private knowledge has individual confidence
- **Builders** must accept space parameters: `MindMapQuery.builder().spaces(PRIVATE, SHARED).build()`
- **Temporal queries** must work across spaces: "family calendar" is a shared temporal view
- **Affect** must support per-viewer overlays: the same node has different PAD for different agents

Memory spaces are like tenant isolation today — not a feature, but a property of the architecture.

### The Model

**Option A: Visibility Layer Above Tenant (Recommended)**

Keep `tenantId` as-is (one per agent). Add a **memory space** concept above the store layer:

```
┌──────────────────────────────────────────────────────┐
│                  Visibility Layer                     │
│  Resolves: which spaces does this agent see?          │
│  Merges: private results + shared results             │
│  Routes: where does this memory go?                   │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ alice-priv  │  │ smiths-family│  │ bob-priv   │  │
│  │ (tenantId)  │  │ (tenantId)   │  │ (tenantId) │  │
│  └─────────────┘  └──────────────┘  └────────────┘  │
│                                                      │
│  Each space IS a tenant. The visibility layer knows  │
│  which tenants belong to which agent's view.          │
└──────────────────────────────────────────────────────┘
```

- Each memory space is a tenant. Private space = individual tenant. Shared space = group tenant.
- The visibility layer maintains membership: `alice → [alice-priv, smiths-family]`
- Querying: the layer unions results from all spaces the agent belongs to
- Storing: the layer routes to the correct space based on sharing rules
- **Zero changes to store implementations** — they still see `tenantId`. The abstraction is above them.

**Why not Option B (hierarchical tenantId):** Touches every store implementation. The current `tenantId` model is simple and correct; adding hierarchy is invasive for minimal benefit.

**Why not Option C (graph-based visibility):** Every query becomes a graph traversal. Performance at scale is unpredictable.

### Selective Sharing

Not all shared is equally visible:

```java
sealed interface Visibility {
    record Private(String ownerId) implements Visibility {}
    record Shared(String spaceId) implements Visibility {}
    record Selective(String spaceId, Set<String> recipientIds) implements Visibility {}
}
```

Selective sharing: Alice told Bob about the job offer → stored in `smiths-family` space with `Visibility.Selective("smiths-family", Set.of("alice", "bob"))`. The visibility layer filters: Emma and Jake don't see it.

### Perspectival Memory — The Hard Part

The same entity, different views. "Grandma" exists in the shared graph. Each family member has their own emotional relationship with her.

**Design: Overlay model**

```
┌──────────────────────────────────────────────────┐
│  Shared MindMap (smiths-family tenant)            │
│                                                  │
│  Node: "Grandma"                                 │
│    name: "Grandma"                               │
│    confidence: 1.0                               │
│    traits: [Personable]                          │
│    properties: {birthday: "1945-03-12"}          │
│    affect: (none — shared nodes carry no PAD)    │
└──────────────────────────────────────────────────┘

┌─────────────────────┐  ┌─────────────────────┐
│  Alice's overlay     │  │  Teenager's overlay  │
│  (alice-priv tenant) │  │  (emma-priv tenant)  │
│                     │  │                     │
│  ref: grandma-id    │  │  ref: grandma-id    │
│  pleasure: 0.9      │  │  pleasure: -0.2     │
│  arousal: 0.3       │  │  arousal: 0.4       │
│  dominance: 0.5     │  │  dominance: -0.3    │
│  notes: "love her"  │  │  notes: "so annoying"|
│  confidence: 0.95   │  │  confidence: 0.8    │
└─────────────────────┘  └─────────────────────┘
```

When Alice queries the graph, the visibility layer:
1. Retrieves the shared "Grandma" node
2. Finds Alice's overlay (matched by NodeRef to the shared node ID)
3. Merges: shared properties + Alice's private PAD + Alice's private confidence
4. Returns a single `MindMapNode` that looks like Alice's perspective

This means:
- Shared nodes carry **no PAD** — affect is always perspectival
- Each agent's overlay is stored in their private tenant
- The overlay is a lightweight record: `(sharedNodeId, pleasure, arousal, dominance, confidence, properties)`
- No changes to MindMapStore — the overlay is stored as a regular node in the private tenant, linked by NodeRef

### Group Dynamics

| Dynamic | How it works |
|---|---|
| **Collective memory** | Stored in the shared space with no individual owner. "We always go to Cornwall in August" — no single family member created this, it's collective |
| **Memory conflict** | Both perspectives are valid. Alice's overlay says `pleasure=0.9` for the holiday, Bob's says `pleasure=-0.3`. No reconciliation needed — conflict IS the data |
| **Knowledge asymmetry** | Visibility rules: financial space visible to `[alice, bob]` only. Kids' queries never see it |
| **Temporal membership** | Membership is temporal: `ValidFrom`/`ValidUntil` on space membership. When teenagers turn 18, their financial visibility expands. When partners separate, shared space access changes. |

### Transactive Memory

Families develop specialisation (Wegner 1987): "Alice handles finances, Bob handles school logistics." The system should support:
- **Domain expertise tags** on space membership: Alice is the authority on financial matters
- **Delegation queries**: "Who in the family knows about X?" traverses expertise tags
- The MindMap graph naturally models this: Alice's private graph is dense around finances, Bob's around school — the shared graph bridges them

---

## Integration with casehub-life

casehub-life models households with multiple agents, each with different decision authority. The mapping:

| casehub-life concept | Memory space concept |
|---|---|
| Household | Shared memory space |
| Family member | Private memory space + membership in household space |
| Decision authority (adults) | Selective visibility on financial/legal spaces |
| Care coordination | Shared health space with selective visibility |
| Contractor tracking | Shared household-ops space |

The YAML surface:

```yaml
memory-spaces:
  - id: smiths-family
    type: shared
    members:
      - id: alice
        roles: [admin, financial-authority]
        since: 2010-06-15
      - id: bob
        roles: [admin, school-authority]
        since: 2010-06-15
      - id: emma
        roles: [member]
        since: 2012-09-01
        financial-visibility: false      # until 18
      - id: jake
        roles: [member]
        since: 2015-03-22
        financial-visibility: false
    
    scopes:
      calendar:     { visibility: all-members }
      household:    { visibility: all-members }
      finances:     { visibility: [alice, bob] }
      health:       { visibility: owner-only, shared-with-on-consent: true }
      legal:        { visibility: [alice, bob] }
```

---

## Cognitive Science Parallel

- **Transactive memory** (Wegner 1987) — groups develop shared memory systems with division of cognitive labour
- **Collective memory** (Halbwachs 1925/1992) — group memories transcending individual recall
- **Shared mental models** (Cannon-Bowers et al. 1993) — team members sharing task and team understanding
- **Perspective-taking** (Piaget, theory of mind) — understanding that others have different views of shared events
- **Distributed cognition** (Hutchins 1995) — cognitive processes distributed across group members and artefacts
- **Privacy calculus** (Dinev & Hart 2006) — individuals weigh disclosure benefits against privacy risks

**Research tags:** transactive memory, Wegner 1987, collective memory, Halbwachs, shared mental models, Cannon-Bowers 1993, team cognition, distributed cognition, Hutchins 1995, perspective-taking, multi-agent memory, privacy-preserving memory systems, Dinev & Hart 2006

---

## Impact on Architecture — Design First, Not Bolt On

Memory spaces touch every phase of the cognitive architecture roadmap:

| Phase | What changes |
|---|---|
| **Phase 1: Structural** | Unified confidence must handle collective vs individual. Builders must accept space parameters. Naming must include space terminology. |
| **Phase 2: Temporal** | Temporal queries across spaces (family calendar). `TemporalIndex` must span private + shared. Group temporal membership (access changes over time). |
| **Phase 3: Affective** | Perspectival PAD overlays (Phase 3 concern, not a separate phase). Affect trajectories per-viewer on shared nodes. Group affective patterns. |
| **Phase 4: Cross-Store** | Entity resolution across spaces. `TemporalFocus` aggregates private + shared. Graph queries span visibility. |
| **Phase 5: YAML** | Memory space YAML config. Group identity in composed YAML. Space-aware cognitive profile. |

**The principle:** If you can't explain how a feature works for shared memory, the feature isn't designed yet. Every Phase 1-5 work item should pass the "shared memory litmus test" — does this API/model/query work correctly when the agent sees both private and shared spaces?
