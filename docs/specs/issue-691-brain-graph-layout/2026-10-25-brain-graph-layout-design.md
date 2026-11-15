# 3D Brain-Shaped Graph Layout — Design Spec

**Issue:** casehubio/neocortex#691
**Date:** 2026-10-25
**Branch:** issue-691-brain-graph-layout

## Overview

Replace the sphere layout in the Cognitive Workbench's 3D graph view with a brain-shaped mesh hull. Subgraph types are seeded into regions based on neuroanatomical parallels. The brain view becomes the primary visualization, with ego-centric exploration as a drill-down. The spatial arrangement communicates cognitive structure immediately and provides the foundation for future live activation animation (watching the brain "fire" during cognitive processing).

## Architecture

### View Hierarchy

The brain view is the primary home. The user lands here, uses pill filters and click-highlighting to orient, and drills down into ego-centric exploration via an explicit "Focus" action.

```
Brain View (home)
  ├── Pill filters: region-level highlight/darken
  ├── Node click: ego-neighborhood highlight in-place + detail panel
  └── Node focus: camera zoom into region → ego-centric BFS loads in-place
        └── "Back to brain" reverses the zoom animation
```

The transition between brain and ego is a camera zoom — fly into the focused node's region, surrounding brain fades, ego-centric graph loads in that spatial area. This creates spatial narrative ("looking inside this part of the brain"). The reverse animation zooms out and fades the brain back in.

### Data Loading

A single new endpoint `/brain` returns all nodes and edges for the tenant, grouped by subgraph type, with region mapping metadata:

```typescript
interface BrainResponse {
  regions: RegionData[];
  edges: EdgeDto[];
  moodSnapshots: MoodNode[];      // limbic: recent mood memories as synthetic nodes
  affectProjections: AffectNode[]; // limbic: shadow nodes for affect-bearing entities
  capsNodes: CapsNodeDto[];        // basal ganglia: CAPS topology
  capsConnections: CapsConnectionDto[];
  nodeCount: number;
  capped: boolean;
}

interface RegionData {
  regionId: string;
  functionalLabel: string;
  anatomicalLabel: string;
  subgraphTypes: string[];
  nodes: NodeDto[];
}
```

The endpoint queries all subgraphs, synthesizes mood/affect nodes for the limbic region, and includes CAPS topology. Designed for a single fetch that populates the entire brain.

**Edge collection:** `MindMapStore` lacks an `allEdges(tenantId)` method. The endpoint collects edges via: (1) `bridgeEdges(subgraphId, tenantId)` per subgraph for cross-subgraph edges, (2) `neighbors(nodeId, tenantId)` per node for intra-subgraph edges, with deduplication by edge ID. At 300 nodes this is O(N) store queries — acceptable for the current scale. If latency becomes an issue, adding `allEdges(tenantId)` to the store SPI is the clean fix.

**CAPS experience links:** The existing `/caps-experience-links` computation (500 memory scans + classification) is expensive. The `/brain` endpoint returns structural data immediately; experience-CAPS links load lazily via a follow-up fetch to avoid blocking initial brain render.

Current scale target: hundreds of nodes (one biography, one agent). The data boundary is clean — LOD or clustering can be added at the API level later without reworking the brain view.

## Brain Mesh

### Shape

Recognizably brain-shaped low-poly silhouette. Two hemispheres, frontal/temporal/occipital lobes visible. ~80-120 triangular faces, ~60-80 vertices. Must read as "brain" in silhouette. Not anatomically accurate — suggestive.

The mesh can be authored in Blender (subdivided icosphere with brain-like deformation, exported as JSON vertex/face arrays) or procedurally generated. Rendered as a translucent wireframe or semi-transparent solid with the dark workbench background showing through.

### Scaling

The brain scales uniformly as a unit based on data density. Algorithm:

1. For each region, compute `nodeCount / regionVolume` as density
2. Find the region with highest density
3. Compute the minimum scale factor that gives the densest region adequate inter-node spacing (target: ~30-40px between nodes at default camera distance)
4. Scale the entire brain mesh uniformly to that factor

The brain never distorts — individual regions don't resize independently. Sparse regions have proportionally more empty space, which communicates "not much here" visually.

## Region Mapping

Nine regions, each defined by a functional label, anatomical sub-label, and convex region bounds within the brain hull:

| Region ID | Subgraph Types | Functional Label | Anatomical Label | Hull Position |
|-----------|---------------|------------------|-----------------|---------------|
| `social` | PERSON, ORGANISATION | Social Cognition | Temporal-Parietal Junction | Lateral temporal-parietal |
| `executive` | GOAL, PROJECT | Planning & Executive | Prefrontal Cortex | Frontal |
| `episodic` | ACTIVITY, PLACE | Episodic Memory | Hippocampal / Medial Temporal | Medial temporal |
| `semantic` | CONCEPT, RESEARCH_AREA, GENERAL | Semantic Knowledge | Anterior Temporal Lobe | Anterior temporal |
| `habitual` | BEHAVIORAL (CAPS) | Habitual Response | Basal Ganglia | Subcortical central |
| `judgment` | COGNITIVE (graduated) | Belief & Judgment | Dorsolateral Prefrontal | Upper frontal |
| `categorical` | TYPE_SYSTEM | Categorical Structure | — | Cerebellum (posterior) |
| `normative` | CULTURAL | Norms & Values | Ventromedial Prefrontal | Lower frontal |
| `limbic` | (synthetic) | Emotional State | Limbic System | Deep central |

PROJECT maps to `executive` (goal-adjacent planning). GENERAL maps to `semantic` (catch-all knowledge). Any future subgraph types without explicit mapping fall back to `semantic`.

The mapping is suggestive "best fit," not a neuroscience claim. Documentation should make this explicit.

### Region Layout

Each region is defined as a convex polygon — a set of bounding planes positioned within the brain hull, slightly inset from the surface so nodes never poke through the visual mesh. Each plane is defined by a normal vector and offset. A point is inside the region if it's on the inside of all planes.

Nodes are seeded at random positions within their region. The force-directed layout settles within the constraint — a custom force function applies a velocity-based restoring force proportional to penetration distance when nodes drift outside their region:

```typescript
interface RegionBounds {
  planes: Array<{ normal: Vec3; offset: number }>;
}

function regionContainmentForce(alpha: number): void {
  const strength = 0.8;
  for (const node of nodes) {
    const region = regionFor(node);
    for (const plane of region.planes) {
      const dist = dot(plane.normal, nodePos(node)) - plane.offset;
      if (dist > 0) {
        // Outside this plane — apply restoring velocity, not hard projection
        node.vx! -= plane.normal.x * dist * alpha * strength;
        node.vy! -= plane.normal.y * dist * alpha * strength;
        node.vz! -= plane.normal.z * dist * alpha * strength;
      }
    }
  }
}
```

This follows the d3-force convention of modifying velocities rather than positions, allowing the simulation to settle smoothly without oscillation at region boundaries. One dot product per plane per node per tick — trivial at ~6-8 planes per region.

**Upgrade path:** The containment function is the only component that knows about region geometry. V1 uses planar convex boundaries. V2 replaces planes with Bezier patches for curved boundaries that follow the brain hull surface — same algorithm structure (inside/outside test + projection), organic curvature. Region definitions, seeding, rendering, and interaction are all containment-independent.

## Limbic Region — Affect/Mood Nodes

The limbic region contains two types of synthetic nodes:

### Mood Snapshots

Recent mood memories (domain="mood") materialized as nodes. Each shows a PAD state at a point in time. Clustered in the center of the limbic region, ordered chronologically. Clicking a mood snapshot shows the emotional state at that moment in the detail panel.

### Affect Projections

Entities from other regions that carry non-null PAD values exceeding a configurable intensity threshold (default: any dimension > 0.3 absolute, tuning parameter to be calibrated against real data) get a "shadow" node projected into the limbic region. Nodes with null PAD values are excluded — no affect projection is created, connected by an edge back to the original node in its home region. The shadow is rendered fainter and smaller than the original. This shows what carries emotional weight — a PERSON with high arousal gets a projection that visually connects the social region to the limbic region.

Together, mood snapshots show the emotional state timeline, and affect projections show what drives those emotions. The limbic region becomes the most informationally dense part of the brain.

## CAPS Integration

CAPS topology nodes (~36 nodes: input, mediating, output per `caps-topology.yaml`) live permanently in the basal ganglia region. No toggle — always visible. Distinguished visually from MindMap nodes by smaller size and the existing CAPS color scheme (input: blue, mediating: purple, output: orange).

Experience-linked synthetic edges cross from entities in other regions into CAPS nodes in the basal ganglia, showing which entities activate which behavioral patterns. These are the same experience-CAPS links currently computed by the `/caps-experience-links` endpoint.

## Interaction Model

Three composable interaction layers:

### Layer 1: Pill Filters — Region-Level Highlight/Darken

The existing sidebar transforms into region pill filters when in brain mode. Each pill shows:
- Primary: functional name (e.g., "Social Cognition")
- Secondary: anatomical name (e.g., "Temporal-Parietal Junction")
- Count badge: number of nodes in the region

Toggling a pill brightens that region and darkens everything else. Multiple pills can be active — their regions are unioned. All pills off = full brain visible.

### Layer 2: Node Click — Ego-Neighborhood Highlight + Detail

Single-clicking a node:
1. Highlights the clicked node and its direct connections (brighten)
2. Darkens everything else
3. Cross-region edges become visually prominent
4. Populates the detail panel with the node's properties, edges, PAD values

Click empty space to reset both highlighting and the detail panel.

Pill filters and node click compose: filter to "Social Cognition" + "Planning & Executive," then click a person node to see which goals they connect to within those active regions.

### Layer 3: Node Focus — Ego-Centric Drill-Down

A deliberate action (right-click → "Focus," or button in the detail panel):
1. Camera zooms into the focused node's brain region
2. Surrounding brain geometry fades out
3. Ego-centric BFS graph loads in the same spatial area (via existing `/ego/{nodeId}` endpoint)
4. Ego-centric navigation works as before (click to explore, breadcrumb trail)
5. "Back to brain" button reverses the animation — zooms out, brain fades back in

**Transition states:** BRAIN_IDLE → ZOOMING_IN (camera moves + mesh fades + ego data fetches in parallel) → EGO_ACTIVE → ZOOMING_OUT (reverse animation, brain data restored from cache) → BRAIN_IDLE. If ego fetch fails during ZOOMING_IN, reverse to BRAIN_IDLE. Re-entrant clicks during animation are queued, not ignored.

The key insight: the brain view uses highlight/darken, not graph reloading. Ego-centric navigation is a separate explicit action, preserving the brain's spatial information.

## Implementation Scope

### New Files (TypeScript, in casehub-mosaic)

**Prerequisite:** casehubio/neocortex#693 (workbench TS migration) must be merged first. All new code goes in the cognitive-workbench frontend source alongside the migrated TS files:

| File | Purpose |
|------|---------|
| `brain3d.ts` | Brain view controller: init, render, interaction handlers, view transitions |
| `brain-mesh.ts` | Brain hull geometry: vertex/face arrays, convex region bounds, scaling |
| `brain-regions.ts` | Region rendering (consumes server-provided mapping), pill filter state management |
| `brain-data.ts` | Data loading: `/brain` endpoint fetch, mood/affect node synthesis types |

### Modified Files

| File | Changes |
|------|---------|
| `workbench.ts` | Replace "3D" toggle button with "Brain" — ego-centric 3D is accessible only via drill-down from brain view, not as a peer toggle |
| `index.html` | Add brain view container, script tags for new files |
| `WorkbenchApi.java` | Add `/brain` endpoint |
| `WorkbenchDto.java` | Add BrainResponse, RegionData, MoodNode, AffectNode DTOs |

### Not In Scope

- Live activation animation (future issue — this lays the spatial foundation)
- Level-of-detail / clustering for large graphs (clean data boundary, add when needed)
- Bezier curved region boundaries (upgrade path documented, convex planar is v1)
- Evaluation panel integration with brain view (evaluation.ts stays as-is)

## Technical Notes

- The brain mesh is purely visual — a Three.js `BufferGeometry` rendered as translucent wireframe. It does not participate in force simulation physics.
- Region convex polygons are slightly inset from the brain surface vertices to prevent nodes from appearing to poke through the hull.
- The 3d-force-graph library supports custom forces via `.d3Force()` — the region containment force is registered alongside the existing charge and link forces.
- Cross-region edges are the primary visual signal for inter-system connections. They should be rendered with higher visibility than intra-region edges (brighter color, slightly thicker).
- **Region mapping is server-authoritative.** The `/brain` endpoint includes region mapping in `RegionData.subgraphTypes`. The client (`brain-regions.ts`) consumes and renders it — no mapping constants in TypeScript. This allows server-side evolution of the mapping without client changes.
- Performance target: handle 500+ nodes at 60fps. The total node count includes entity nodes (~250), CAPS nodes (~36), mood snapshots (~20), and affect projections (~75 at 30% PAD coverage). The existing 3d-force-graph handles 200+ in the ego view. The brain view adds hull rendering and convex polygon containment — both negligible per-tick cost.

## Future Vision

The brain layout is infrastructure for live cognitive processing visualization. Future work (separate issues):

1. **Activation animation** — as the LLM runs cognitive ticks, regions light up in sequence: appraisal (limbic) → drives (frontal) → CAPS settling (basal ganglia) → behavioral output. WebSocket channel already exists for pushing events.
2. **Memory formation** — new memories appear live in the brain as the agent interacts, fading into their regions.
3. **Bezier curved containment** — upgrade planar convex regions to Bezier patch boundaries for organic curvature that follows the brain hull surface.
4. **LOD/clustering** — aggregate dense regions into summary nodes at far zoom, expand on zoom-in.

## References

- casehubio/neocortex#580 — 3D ego-centric graph view (parent, closed)
- casehubio/neocortex#691 — this issue
- casehubio/neocortex#693 — workbench TS migration (in progress)
- `casehub-mosaic/examples/cognitive-workbench/` — existing workbench code
- `graph3d.ts` — existing 3D ego-centric view implementation
- `WorkbenchApi.java` — existing API endpoints (ego, subgraphs, CAPS topology)
- `SubgraphTypes.java` — subgraph type constants
- 3d-force-graph library: `ForceGraph3D`, d3-force custom forces
