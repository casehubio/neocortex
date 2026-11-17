---
layout: post
title: "The character that couldn't worry"
date: 2026-11-17
entry_type: note
subtype: diary
projects: [casehubio/neocortex]
tags: [caps, personality, pid5, appraisal, classification, cbr]
series: issue-977-pid5-facet-personality-classification
---

The CAPS situation classifier has been personality-aware since disposition axes landed — it knows whether a character is cooperative or competitive, strict or flexible. What it couldn't distinguish was two cooperative characters who *worry differently*. An anxious cooperator reads "your manager announces unexpected organisational changes" as a threat. A calm cooperator reads it as information. Same disposition axes, same tension routing, same classification result.

The gap is between direction and magnitude. Disposition axes give you the compass bearing — this character leans toward cooperation. PID-5 facets give you the intensity — this character's anxiousness is 0.85, their suspiciousness is 0.75, their fear onset threshold is 60% higher than neutral. The first tells the classifier *what* the character values. The second tells it *how strongly* the character reacts to threats against those values.

The fix threads PID-5 facets and derived AppraisalWeights through the existing metadata pipeline — the same `Map<String, String>` that carries disposition axes now also carries `pid5.Anxiousness=0.85` and `appraisal.fearOnsetThreshold=1.60`. PersonalityPromptBuilder formats these as natural-language annotations: "Personality facets: high Anxiousness (0.85), low Risk Taking (0.20)" and "Perception biases: elevated urgency sensitivity (1.45), heightened fear onset (1.60)." The LLM sees the character's perceptual filters alongside their social orientation.

The CBR cache needed the same treatment. Two characters with identical disposition axes but different PID-5 profiles were hitting the same cached classification — the SHA-256 hash only included disposition values. Now every `pid5.*` and `appraisal.*` metadata key feeds into the hash in sorted order, so different personality magnitudes produce different cache entries. The model version bumps from `haiku` to `haiku-pid5`, which invalidates the entire cache. That's deliberate — every existing entry was classified without personality magnitude, and returning those results for personality-enriched queries would be wrong.

The design decision I found most interesting was what *not* to put in the cache key. PID-5 facets are stable — they're derived from disposition and don't change between turns. Mood and active drives are transient. Mood shapes how a character perceives a situation right now, but caching mood-specific classifications would fragment the cache with per-turn entries that never get reused. The line is: personality goes in the cache key, emotional state goes in the prompt but not the key.

All 13 PID-5 facets are included by default, behind a config property that lets you narrow the set after eval. The issue originally specified 7 perception-shaping facets, but we decided not to hardcode that hypothesis — we won't know which facets actually improve classification quality until we run the eval with real LLM responses.

The eval test adds two profiles — anxious-cooperative and calm-cooperative — with identical disposition axes and opposite PID-5 extremes. The assertion is that cosine distance between their activation vectors exceeds 0.05, which is the same baseline the existing disposition-divergence test uses. A second assertion checks that the anxious profile activates threat nodes (social_threat, punishment, powerlessness) more strongly than the calm one. These are LLM-dependent and may need threshold tuning when first run against the live API, but the structure is there.

What this opens up: the classifier now has the vocabulary to express personality magnitude, not just personality direction. That's the prerequisite for the tension table to eventually incorporate PID-5 — an anxious character with a bold/strict tension feels that tension differently than a non-anxious one. The current tension routing is still disposition-only, but the classifier prompt now has the information it would need to discriminate.
