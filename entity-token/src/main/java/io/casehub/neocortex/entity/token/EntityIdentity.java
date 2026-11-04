package io.casehub.neocortex.entity.token;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EntityIdentity(
        String entityId,
        String canonicalFull,
        Map<String, String> nameParts,
        Map<String, String> pronouns,
        Map<String, String> preferred,
        Set<String> aliases,
        Map<String, EntityIdentity> alterEgos
) {

    public EntityIdentity {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(canonicalFull, "canonicalFull");
        nameParts = Map.copyOf(nameParts);
        pronouns  = Map.copyOf(pronouns);
        preferred = Map.copyOf(preferred);
        aliases   = Set.copyOf(aliases);
        alterEgos = Map.copyOf(alterEgos);
    }

    public static EntityIdentity of(String entityId, String canonicalFull,
                                    Map<String, String> nameParts,
                                    Map<String, String> pronouns,
                                    Map<String, String> preferred,
                                    Set<String> aliases) {
        return new EntityIdentity(entityId, canonicalFull, nameParts,
                                  pronouns, preferred, aliases, Map.of());
    }

    public List<SurfaceForm> surfaceForms() {
        Set<String>       seen  = new LinkedHashSet<>();
        List<SurfaceForm> forms = new ArrayList<>();

        addIfNew(forms, seen, canonicalFull, "full", entityId);
        nameParts.forEach((key, value) -> addIfNew(forms, seen, value, key, entityId));
        preferred.forEach((key, value) -> addIfNew(forms, seen, value, key, entityId));
        aliases.forEach(alias -> addIfNew(forms, seen, alias, "other", entityId));
        for (EntityIdentity alterEgo : alterEgos.values()) {
            for (SurfaceForm form : alterEgo.surfaceForms()) {
                addIfNew(forms, seen, form.text(), form.variant(), form.entityId());
            }
        }

        return List.copyOf(forms);
    }

    public String resolveVariant(String variant) {
        if ("full".equals(variant)) {return canonicalFull;}
        String fromParts = nameParts.get(variant);
        if (fromParts != null) {return fromParts;}
        String fromPreferred = preferred.get(variant);
        if (fromPreferred != null) {return fromPreferred;}
        if (variant.startsWith("pronoun")) {
            String pronounKey = variant.startsWith("pronoun:")
                                ? "pronouns" + Character.toUpperCase(variant.charAt(8)) + variant.substring(9)
                                : variant;
            return pronouns.get(pronounKey);
        }
        return null;
    }

    private void addIfNew(List<SurfaceForm> forms, Set<String> seen,
                          String text, String variant, String forEntityId) {
        if (text != null && !text.isBlank() && seen.add(text)) {
            forms.add(new SurfaceForm(text, variant, forEntityId));
        }
    }
}
