# io.casehub.neocortex.memory.cbr.PersonalityTransitionSchema

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `class`

Schema convention for personality transition CBR cases.

<p>Records personality evolution events — when an agent's cognitive function
profile shifts (e.g. dominant Ti→Fe after JPAF reflection). The engine stores
transitions; CBR retrieval finds similar past transitions to inform routing
decisions: "last time this agent shifted dominant function, what happened?"

<p>caseType: CASE_TYPE

<p>Features:
<ul>
  <li>`agent_id` — Categorical: the agent whose personality evolved</li>
  <li>`old_dominant` — Categorical: previous dominant cognitive function (Ti, Te, Fi, Fe, Ni, Ne, Si, Se)</li>
  <li>`new_dominant` — Categorical: new dominant cognitive function</li>
  <li>`old_auxiliary` — Categorical: previous auxiliary function</li>
  <li>`new_auxiliary` — Categorical: new auxiliary function</li>
  <li>`trigger_type` — Categorical: what caused the shift (reflection, compensation, reinforcement, manual)</li>
  <li>`outcome` — Categorical: observed effect on agent performance (improved, degraded, neutral, unknown)</li>
</ul>

<p>problem: human-readable description of the transition context
<p>solution: the routing/adaptation action taken in response

<p>Consumers: engine personality-adaptive routing (engine#790)
<p>Producers: engine JPAF reflection mechanism (engine#790 sub-issues)
<p>Data model: eidos weighted disposition profiles (eidos#111)

## Fields

### `CASE_TYPE` (`java.lang.String`)

## Constructors

### `private PersonalityTransitionSchema()`

## Methods

### `public static io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema()`
