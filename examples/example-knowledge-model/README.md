# Knowledge Model — From Notes to Semantic Knowledge

How raw conversation becomes structured, queryable knowledge that an
agent can reason about.

## What is the Thing Model?

Knowledge doesn't arrive structured. A user describes a birthday party
they're planning — mentioning people, places, dates, dietary
requirements, relationships. It arrives as prose. The platform needs to
turn this into something it can query, traverse, and reason about.

The **Thing** model is the foundation. Every entity in the knowledge
graph — a person, a project, a note, a concept — is a Thing. Things
carry identity, properties, traits, and a type. They support
`instanceof`-style type checking and typed property access via Java
interfaces.

```
Thing (thing-api, zero deps)
  ├── id()           — unique identifier
  ├── name()         — human-readable name
  ├── type()         — what this entity IS (person, project, note)
  ├── properties()   — key-value data (birthday, email, body text)
  ├── traits()       — discovered capabilities (Personable, Organisational)
  ├── is(typeName)   — instanceof check (type OR trait)
  └── as(Interface)  — typed property access via JDK Proxy
```

**MindMapNode extends Thing**, adding cognitive features the agent's
reasoning system needs — epistemic certainty (confidence with decay),
emotional assessment (PAD dimensions), temporal validity, provenance,
and visibility controls. Consumers who just want to work with entities
depend on `thing-api` (zero deps). The cognitive machinery lives in
`mindmap-api`.

### Where this comes from

The pattern is from Drools/PHREAK — a rule engine where facts gain and
lose types dynamically based on their properties. A fact starts as raw
data. Rules evaluate it. When properties match, the fact gains an
interface — it IS that type now, and you can cast to it. Properties
change, rules re-evaluate, types shift.

MindMap already implemented this informally. `TraitRule` evaluates
whether a node satisfies a trait based on its properties and edges.
`TraitProxy` creates JDK Proxy views for typed access. This epic
formalizes that into an explicit `Thing` interface with `is()`/`as()`
and a dynamic type system.

### What this is NOT

- **Not OWL-DL.** No formal reasoner, no description logic, no open
  world assumption. Types are data, not axioms.
- **Not RDFS.** Not "everything is a triple." Core types feel like Java
  — typed fields, IDE support, compile-time safety. Properties and edges
  are the flexible layer underneath.
- **Not a fixed ontology.** The LLM discovers new types at runtime. No
  recompile needed.

## The Knowledge Lifecycle

Knowledge evolves through phases — from raw conversation to structured,
semantically rich, retrievable entities. This mirrors how human memory
works: raw experiences are captured quickly during wakefulness, then
reorganized into structured knowledge during sleep.

```
┌─────────────────────────────────────────────────────────────┐
│                    THE KNOWLEDGE CONTINUUM                   │
│                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌───────┐ │
│  │  Notes   │───→│ Entities │───→│  Traits  │───→│ Types │ │
│  │ (prose)  │    │(extracted)│   │(discovered)│   │(named)│ │
│  └──────────┘    └──────────┘    └──────────┘    └───────┘ │
│                                                             │
│  type: general    type: person    +Personable    birthday-  │
│  body: "Emily's   name: Emily     as(P.class)   party      │
│   birthday..."    role: mum        .role()→mum   (dynamic)  │
│                   email: e@...                              │
└─────────────────────────────────────────────────────────────┘
```

### The three speeds

| Speed | When | What happens |
|-------|------|-------------|
| **Real-time** | During conversation | Capture prose as "general" Things — notes with freeform properties |
| **Near-time** | After conversation | Extract entities, identify relationships, assign types |
| **Background** | Idle / "sleep" | Consolidate, decay, merge duplicates, discover schemas, optimize retrieval |

This example demonstrates phases 1-6 manually. The [Knowledge
Consolidation Pipeline](https://github.com/casehubio/neocortex/issues/295)
(#295) will automate the near-time and background phases.

### The scenario

Planning Emily's birthday party. A user has a conversation:

> "Emily's birthday is next Saturday. Phil wants to invite John from
> school. Need to check with Joe — that's John's dad. Phil is turning
> 8. Emily mentioned she'd like it at the community hall. Oh, and John
> has a nut allergy."

Watch how this prose becomes structured knowledge:

| Phase | What happens | Things created |
|-------|-------------|---------------|
| 1. Notes | Conversation captured as "general" nodes | 2 note Things |
| 2. Extraction | Entities identified and typed | Emily, Phil, John, Joe (person), St Mary's (organisation) |
| 3. Traits | TraitRules evaluate → traits assigned | Emily gains Personable, St Mary's gains Organisational |
| 4. Dynamic types | "birthday-party" registered as a new type | Party entity with type "birthday-party" |
| 5. Schema | Schema derived from Java interfaces | Person schema: birthday, role, email, phone |
| 6. Queries | Graph traversal, text search, Subject bridge | "Who is connected to Phil?" → Emily, John, school, party |

## Core Concepts

### Thing — The Universal Base Type

Every entity is a Thing. The interface lives in `thing-api` — a
zero-dependency module that consumers can depend on without pulling in
MindMap internals.

```java
Thing note = store.getNode(noteId, tenantId);
note.type();         // "general"
note.name();         // "Party planning chat"
note.property("body"); // Optional.of("Emily's birthday is...")
note.is("Personable"); // false — it's a note, not a person
```

`MindMapNode` extends `Thing` — any MindMapNode can be used wherever
a Thing is expected:

```java
MindMapNode emily = store.getNode(emilyId, tenantId);
Thing thing = emily; // widening — MindMapNode IS a Thing
```

### Types — What an Entity IS

`type()` returns the entity's creation type, derived from its subgraph
membership. A node in a "person" subgraph has type `"person"`. A node
in a "general" subgraph has type `"general"`.

Types are **dynamic strings**, not a fixed enum. The LLM can discover
new types at runtime:

```java
// Well-known types are constants
store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), tenant);

// LLM-discovered types work the same way
store.createSubgraph(new SubgraphInput("Emily's Party", "birthday-party", null), tenant);
```

Type strings are lowercase-normalized (`strip().toLowerCase()`) in
`SubgraphInput`'s constructor — `"Person"`, `"PERSON"`, and `"person"`
all become `"person"`.

### Traits — What an Entity CAN DO

**Traits are types.** When a Thing gains a trait, it gains a type.
`is()` checks both the creation type and the trait set:

```java
emily.is("person");     // true — creation type
emily.is("Personable"); // true — trait type
emily.is("project");    // false — neither
```

Traits are assigned by `TraitRule` implementations that evaluate a
node's properties and edges:

```java
// PersonableTraitRule checks for birthday, role, email, phone, or
// family/work edges. If any are present, the node is Personable.
PersonableTraitRule rule = new PersonableTraitRule();
rule.matches(emily, emilyEdges); // true — has role + email
```

In production, `TraitApplicationDecorator` runs TraitRules automatically
when nodes are added or updated. Traits can also be declared in YAML
via `DeclarativeTraitRule`.

**Convention:** trait names are PascalCase (matching Java interface simple
names). Type names are lowercase. This reflects their origin — traits
come from code, types come from data.

### is() / as() — The Drools Pattern

`is()` is an `instanceof` check. `as()` is a cast to a typed interface.
Together they give the Drools traits experience:

```java
if (emily.is("Personable")) {
    Personable p = emily.as(Personable.class);
    p.role();     // Optional.of("mum")
    p.email();    // Optional.of("emily@example.com")
    p.birthday(); // Optional.empty() — not set yet
}
```

**How `as()` works:** a JDK Proxy maps interface method names to
`property(methodName)` calls. `Personable.role()` reads
`property("role")`. Return types are coerced:

| Return type | Behaviour |
|-------------|----------|
| `Optional<String>` | `Optional.ofNullable(property value)` |
| `String` | value or `null` |
| `int`, `long`, `double`, `boolean` | parsed, or type default (0, 0L, 0.0, false) |
| `Integer`, `Long`, `Double`, `Boolean` | parsed, or `null` |

**You define your own trait interfaces.** The platform provides
`Personable`, `Projectlike`, `Organisational`, `Eventlike` as
conveniences, but `as()` works with any interface:

```java
interface PartyGuest {
    Optional<String> dietary();
    Optional<String> rsvpStatus();
}

// No platform dependency — just method names matching property keys
if (john.property("dietary").isPresent()) {
    PartyGuest guest = john.as(PartyGuest.class);
    guest.dietary(); // Optional.of("nut allergy")
}
```

### Type Registry — Types as Knowledge

Types are first-class data in MindMap, stored as nodes in a
`TYPE_SYSTEM` subgraph. The `TypeRegistry` CDI bean mediates all type
operations:

```java
TypeRegistry registry = new TypeRegistry(store);

// Core types are bootstrapped automatically on first access
registry.typeExists("person", tenant);        // true
registry.javaClass("person", tenant);         // Optional.of(Personable.class)

// Register dynamic types discovered by the LLM
registry.registerType("birthday-party", "general", tenant);
registry.subtypesOf("general", tenant);       // [..., "birthday-party"]
registry.javaClass("birthday-party", tenant); // Optional.empty() — no Java interface yet
```

The type hierarchy is graph-native — `subtype-of` edges between type
nodes. Hierarchy queries are graph traversal. Adding a type is adding
a node.

### Property Schema — What Properties a Type Expects

Core types derive their schema from Java interfaces automatically:

```java
Map<String, SchemaField> personSchema = registry.schemaFor("person", tenant);
// {birthday: SchemaField(name=birthday, type=string, required=false),
//  role:     SchemaField(name=role, type=string, required=false),
//  email:    SchemaField(name=email, type=string, required=false),
//  phone:    SchemaField(name=phone, type=string, required=false)}
```

Dynamic types start with no schema. As the LLM discovers consistent
property patterns, schema can be added to the type node using the
`schema.{fieldName}.type` naming convention. Schema validation is
**advisory** — it documents expectations but doesn't reject novel
properties. LLMs discover new things; rigid schema would prevent
learning.

### Subject Bridge — Cross-Store Entity References

`Subject(String type, String id)` in `memory-api` references a Thing
by convention. Same type, same id — no code dependency between modules:

```java
// Create a Subject reference pointing to a MindMap entity
Subject ref = Subject.of("person", emilyId);

// Resolve it back to a Thing
Thing resolved = store.getNode(ref.id(), tenantId);
assert resolved.type().equals(ref.type()); // both "person"
```

This follows the `NodeRef` pattern — convention-based cross-store
references without coupling modules.

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     Consumer Layer                        │
│  Depends on: thing-api (zero deps)                       │
│  Sees: Thing — id, name, type, properties, traits,       │
│        is(), as()                                        │
├──────────────────────────────────────────────────────────┤
│                    Cognitive Layer                        │
│  Depends on: mindmap-api (+ cognitive-api, platform-api) │
│  Sees: MindMapNode — extends Thing with confidence, PAD, │
│        temporal bounds, provenance, visibility           │
├──────────────────────────────────────────────────────────┤
│                   Intelligence Layer                     │
│  Depends on: mindmap-intelligence                        │
│  Provides: TypeRegistry, TraitRules, MindMapExtractor,   │
│           CuriositySignalGenerator                       │
└──────────────────────────────────────────────────────────┘

Dependency direction:
  thing-api ← mindmap-api ← mindmap-intelligence ← cognitive-index
  (zero deps)  (cognitive)   (type registry,        (cross-store
                              trait rules)            aggregation)
```

| Module | What it owns |
|--------|-------------|
| `thing-api` | Thing interface, ThingProxyHandler, zero deps |
| `mindmap-api` | MindMapNode, MindMapStore SPI, SubgraphTypes, SchemaField, TraitRule, RuleCondition |
| `mindmap-inmem` | In-memory MindMapStore for tests |
| `mindmap-sqlite` | Production SQLite backend with Flyway migrations |
| `mindmap-intelligence` | TypeRegistry, TraitProxy, trait rule implementations, MindMapExtractor |

## Capabilities Matrix

| Capability | Phase | API |
|-----------|-------|-----|
| Freeform content nodes | 1 | `NodeInput.of()` + `SubgraphTypes.GENERAL` |
| Thing.type() from subgraph | 1, 2 | `Thing.type()` → subgraph type string |
| Typed entities with properties | 2 | `NodeInput.withProperties()` |
| Typed relationships (edges) | 2 | `EdgeInput.of()` + `MindMapStore.addEdge()` |
| Trait evaluation (rules) | 3 | `TraitRule.matches(node, edges)` |
| is() — type + trait checking | 3 | `Thing.is("Personable")` |
| as() — typed property access | 3 | `Thing.as(Personable.class)` |
| Traits are types | 3 | `is(type) ∪ is(trait)` = full type set |
| Dynamic type registration | 4 | `TypeRegistry.registerType()` |
| Type hierarchy (subtype-of) | 4 | `TypeRegistry.subtypesOf()` |
| Core type → Java class mapping | 4 | `TypeRegistry.javaClass()` |
| Schema from Java interfaces | 5 | `TypeRegistry.schemaFor()` + reflection |
| Text search | 6 | `MindMapQuery.withText()` |
| Subgraph-scoped queries | 6 | `MindMapStore.nodesIn()` |
| Relationship traversal | 6 | `MindMapStore.neighbors()` |
| Subject ↔ Thing bridge | 6 | `Subject.of(type, id)` → `store.getNode()` |

## Running the Example

```bash
# From the neocortex root — no Docker, no models, runs in seconds
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test \
  -pl examples/example-knowledge-model -Pexamples-smoke
```

The test output walks through each phase with printed summaries and
ends with the capabilities matrix.

## What's Next — The Consolidation Pipeline

This example demonstrates each phase as explicit method calls. In
production, near-time and background phases run automatically:

**[Epic #295: Knowledge Consolidation Pipeline](https://github.com/casehubio/neocortex/issues/295)**

| Issue | What it builds |
|-------|---------------|
| [#296](https://github.com/casehubio/neocortex/issues/296) | **Conversation-to-Thing bridge** — turn raw prose into initial Things |
| [#297](https://github.com/casehubio/neocortex/issues/297) | **Consolidation scheduler** — coordinate background processing phases |
| [#298](https://github.com/casehubio/neocortex/issues/298) | **Access-frequency tracking** — strengthen frequently-retrieved knowledge |
| [#299](https://github.com/casehubio/neocortex/issues/299) | **Community summaries** — higher-level abstractions from entity clusters |
| [#300](https://github.com/casehubio/neocortex/issues/300) | **Merge detection** — identify and unify duplicate entities |

The vision: knowledge starts as conversation, background processes
progressively enrich it into semantic entities with types, traits, and
relationships — like memory consolidation during sleep. The Thing model
provides the representation; the pipeline provides the automation.

### The promotion path

When a dynamic type crystallises — stable schema, frequently queried,
consistent properties across instances — a developer creates a Java
interface for it. The type node gains a `java-class` property, and
consumers get typed access via `as()`. Future: code generation tooling
reads the schema and generates the interface automatically
([#293](https://github.com/casehubio/neocortex/issues/293)).
