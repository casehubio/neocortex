## D1: Approach for trajectory divergence

**Choice:** Add output→mediating feedback loops to the CAPS topology
**Alternatives:**
- More extreme input activations — won't work; damping factor dominates convergence rate regardless of input magnitude
- Accept trajectory as topology property — abandons a psychologically meaningful signal that CAPS theory expects
**Rationale:** The current topology is feedforward with zero feedback loops. CAPS theory (Mischel & Shoda 1995) predicts personality-dependent convergence dynamics via recurrent connections. Adding tagged feedback connections creates genuine structural basis for trajectory divergence.
**Trade-offs:** Structural topology change affects all settling; needs careful weight calibration to avoid destabilising convergence
**Sources:** CapsSettler.java (damping at line 99), caps-topology.yaml (no output→mediating connections), Mischel & Shoda 1995, Gray 1982
**Exploration:** quick
**Status:** captured

## D2: Feedback connection scope

**Choice:** Full set of 6 feedback connections covering all three CAPS subsystems (RST affect, self-model, social-model)
**Alternatives:**
- Minimal 2-3 connections — unnecessarily restrictive; each connection is independent, no compounding risk
- BAS/BIS only (2) — theoretically clean but misses self-model and social-model feedback
**Rationale:** More feedback paths = more dimensions where personality profiles can diverge. Each connection has an independent moderate weight and disposition-modulated tag. No downside to the fuller set — it gives more tuning opportunities.
**Trade-offs:** None meaningful — 6 connections at 0.1-0.2 weights don't destabilise convergence
**Sources:** Bandura 1997 (self-efficacy), Beck 1976 (self-worth), Bowlby 1988 (attachment working models), Gray 1982 (RST)
**Exploration:** quick
**Status:** captured
