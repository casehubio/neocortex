## D1: View hierarchy — brain-primary with ego drill-down

**Choice:** Brain view is the primary home. Ego-centric is a drill-down via camera zoom into the focused node's region, with animated return to brain.
**Alternatives:**
- Dual-mode peer views with toggle — more flexible but less discoverable, user must know when each is appropriate
- Brain-only, no ego — loses the focused neighborhood exploration that ego provides
**Rationale:** The brain's spatial arrangement IS the information. Ego-centric exploration is a detail view, not a peer. Camera zoom creates spatial narrative ("looking inside this region").
**Trade-offs:** Zoom animation is more complex than a clean mode switch. Can phase: clean cut first, zoom animation as polish.
**Sources:** #580 (existing ego-centric 3D view), #691 issue description (interaction layer 3: Node Focus)
**Exploration:** quick
**Status:** captured

## D2: Brain mesh fidelity — recognizably brain-shaped

**Choice:** Low-poly but unmistakably brain-shaped silhouette (~80-120 faces). Two hemispheres, frontal/temporal/occipital lobes visible.
**Alternatives:**
- Abstract organic blob — doesn't communicate "cognitive infrastructure" at a glance
**Rationale:** The shape is the metaphor. It must read as "brain" in silhouette to justify the neuroanatomical region mapping.
**Trade-offs:** More effort to author the mesh than a simple sphere/blob.
**Sources:** #691 issue description ("low-poly — suggestive, not anatomical")
**Exploration:** quick
**Status:** captured

## D3: Brain scaling — uniform resize based on densest region

**Choice:** Whole brain scales uniformly as a unit. Sizing algorithm: find the region with highest node density, compute minimum hull size for adequate spacing, scale entire mesh to that size.
**Alternatives:**
- Fixed-size brain — doesn't accommodate varying data density
- Per-region distortion — breaks the brain silhouette
**Rationale:** Brain shape must be preserved. Individual regions don't distort independently. The brain "grows" with knowledge — a good metaphor.
**Trade-offs:** Sparse regions may have excess space. Acceptable — empty space in a brain region communicates "not much here" which is informative.
**Sources:** User requirement during brainstorming
**Exploration:** quick
**Status:** captured

## D4: Data loading — single new endpoint

**Choice:** Single new `/brain` endpoint returning all nodes and edges for the tenant, grouped by subgraph type, with region mapping metadata.
**Alternatives:**
- Compose from existing endpoints (/subgraphs + /subgraphs/{id}/nodes) — N+1 round trips, no cross-region edges
**Rationale:** Cross-region edges are half the point. Single fetch avoids waterfall. Server controls inclusion and can attach region mapping.
**Trade-offs:** New endpoint to maintain. Acceptable for a single-purpose view.
**Sources:** WorkbenchApi.java (existing ego endpoint), #691 issue description
**Exploration:** quick
**Status:** captured

## D5: Affect/mood in limbic region — both mood snapshots and affect projections

**Choice:** Mood snapshots (recent mood memories as nodes showing emotional trajectory) AND affect projections (shadow nodes for entities with strong PAD values, connected back to originals in other regions).
**Alternatives:**
- Mood snapshots only — misses the "what triggers emotions" story
- Affect projections only — misses the temporal emotional state
- Ambient glow (no nodes) — no clickable content, can't participate in activation animation
**Rationale:** They serve different purposes. Mood snapshots show state, affect projections show causation. Together they make the limbic region informationally dense, which is appropriate. Visual clutter is a rendering problem (faint shadow nodes), not a data problem.
**Trade-offs:** Most complex option. Region auto-scales to accommodate density.
**Sources:** MoodEvents, AffectTrajectoryAnalyzer, PAD values on MindMapNode
**Exploration:** quick
**Status:** captured

## D6: CAPS in brain view — always visible, no toggle

**Choice:** CAPS nodes always present in basal ganglia region. Smaller size and distinct color scheme distinguishes them from MindMap nodes.
**Alternatives:**
- Toggle preserved (opt-in like current 3D view) — hides data, defeats whole-brain purpose, breaks future activation animation
**Rationale:** Brain view shows everything spatially. CAPS must be always present as a destination for future activation path animation (appraisal → drives → CAPS settling → behavioral output).
**Trade-offs:** More visual density. Mitigated by sizing/color distinction.
**Sources:** graph3d.ts (existing CAPS overlay), caps-api topology
**Exploration:** quick
**Status:** captured

## D7: Pill filters — sidebar replacement in brain mode

**Choice:** When in brain mode, the existing subgraph sidebar transforms into region pill filter list. Vertical layout, dual labels (functional + anatomical).
**Alternatives:**
- Top bar — compact but less room for dual labels
- Floating overlay — risks occluding the brain view
**Rationale:** The sidebar already shows subgraph types with counts — conceptually the same as region filters. Reuses existing real estate, no new UI chrome.
**Trade-offs:** Sidebar content changes by view mode, adding conditional logic.
**Sources:** index.html sidebar structure
**Exploration:** quick
**Status:** captured

## D8: Node click — highlight neighborhood AND populate detail panel

**Choice:** Single click does both: highlights ego-neighborhood in-place (brighten clicked node + connections, darken rest) AND fills the detail panel with the clicked node's properties.
**Alternatives:**
- Click highlights only, hover for detail — can't lock panel on a node while exploring
**Rationale:** No reason these should be exclusive. Spatial context and node detail simultaneously. Click empty space resets both.
**Trade-offs:** None significant.
**Sources:** #691 interaction model (layers 1 and 2), existing detail panel
**Exploration:** quick
**Status:** captured

## D9: Layout containment — parametric hull with convex polygonal regions

**Choice:** Brain shape as a parametric mesh (visual only, ~80-120 faces). Each of 9 regions defined as a convex polygon (set of bounding planes) within the hull. Custom force function checks containment (dot product per plane) and projects nodes back to nearest surface when outside. V2 upgrade: replace planar boundaries with Bezier patches for curved containment.
**Alternatives:**
- Axis-aligned bounding boxes — simpler clamp but rectangular regions don't follow brain curvature at all
- Mesh raycast containment — pixel-perfect but O(nodes × faces) per tick, concave geometry issues, over-engineered
- SDF containment — elegant but non-trivial to define for a brain, opaque to debug, over-engineered
**Rationale:** Convex polygon containment is cheap (one dot product per plane per node per tick), follows brain surface much better than boxes, and naturally upgrades to Bezier curved boundaries. The mesh hull remains purely decorative.
**Trade-offs:** Slightly more complex than AABB min/max, but the convex polygon test is still O(planes) per node — trivial at ~6-8 planes per region.
**Upgrade path:** Containment is a single swappable force function. V1 uses planar convex regions. V2 replaces planes with Bezier patches for curved boundaries that follow the brain hull surface — same algorithm structure, organic curvature.
**Sources:** 3d-force-graph custom force API, d3-force
**Exploration:** quick
**Status:** captured

## D10: Code — TypeScript in mosaic ecosystem

**Choice:** All brain view code in TypeScript, fitting the mosaic monorepo conventions (Vite, vitest, strict types).
**Alternatives:**
- Plain JS (like the original workbench files) — inconsistent with mosaic, no type safety
**Rationale:** The mosaic repo is TS throughout. The existing JS workbench files are being migrated (#693). New code starts right.
**Trade-offs:** None — this is the established pattern.
**Sources:** casehub-mosaic package.json, tsconfig.json, existing component pattern
**Exploration:** quick
**Status:** captured