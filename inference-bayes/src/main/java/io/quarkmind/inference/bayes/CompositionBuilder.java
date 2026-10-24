package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CompositionBuilder {

    private final String name;
    private final List<SlotDefinition> slotDefs = new ArrayList<>();
    private final List<LinkDefinition> linkDefs = new ArrayList<>();
    private SlotDefinition currentSlot;

    private CompositionBuilder(String name) {
        this.name = name;
    }

    public static CompositionBuilder create(String name) {
        return new CompositionBuilder(name);
    }

    public SlotDef slot(String slotName, JunctionTree tree) {
        finalizeCurrentSlot();
        currentSlot = new SlotDefinition(slotName, tree);
        return new SlotDef(this);
    }

    public LinkSourceDef link(String sourceSlot, String sourceVar) {
        finalizeCurrentSlot();
        return new LinkSourceDef(this, sourceSlot, sourceVar);
    }

    public NetworkComposition build() {
        finalizeCurrentSlot();

        List<NetworkSlot> slots = new ArrayList<>();
        for (var def : slotDefs) {
            slots.add(new NetworkSlot(def.name, def.tree, Map.copyOf(def.variants)));
        }

        List<NetworkLink> links = new ArrayList<>();
        for (var def : linkDefs) {
            links.add(new NetworkLink(def.sourceSlot, def.targetSlot,
                Map.of(def.sourceVar, def.targetVar), def.mode));
        }

        return new NetworkComposition(name, slots, links);
    }

    private void finalizeCurrentSlot() {
        if (currentSlot != null) {
            slotDefs.add(currentSlot);
            currentSlot = null;
        }
    }

    public static class SlotDef {
        private final CompositionBuilder builder;

        SlotDef(CompositionBuilder builder) {
            this.builder = builder;
        }

        public SlotDef variant(String contextKey, JunctionTree tree) {
            builder.currentSlot.variants.put(contextKey, tree);
            return this;
        }

        public SlotDef slot(String slotName, JunctionTree tree) {
            return builder.slot(slotName, tree);
        }

        public LinkSourceDef link(String sourceSlot, String sourceVar) {
            return builder.link(sourceSlot, sourceVar);
        }

        public NetworkComposition build() {
            return builder.build();
        }
    }

    public static class LinkSourceDef {
        private final CompositionBuilder builder;
        private final String sourceSlot;
        private final String sourceVar;

        LinkSourceDef(CompositionBuilder builder, String sourceSlot, String sourceVar) {
            this.builder = builder;
            this.sourceSlot = sourceSlot;
            this.sourceVar = sourceVar;
        }

        public LinkDef to(String targetSlot, String targetVar) {
            var def = new LinkDefinition(sourceSlot, targetSlot, sourceVar, targetVar);
            builder.linkDefs.add(def);
            return new LinkDef(builder, def);
        }

        public LinkDef to(String targetSlot) {
            return to(targetSlot, sourceVar);
        }
    }

    public static class LinkDef {
        private final CompositionBuilder builder;
        private final LinkDefinition def;

        LinkDef(CompositionBuilder builder, LinkDefinition def) {
            this.builder = builder;
            this.def = def;
        }

        public LinkDef mode(TransferMode mode) {
            def.mode = mode;
            return this;
        }

        public SlotDef slot(String slotName, JunctionTree tree) {
            return builder.slot(slotName, tree);
        }

        public LinkSourceDef link(String sourceSlot, String sourceVar) {
            return builder.link(sourceSlot, sourceVar);
        }

        public NetworkComposition build() {
            return builder.build();
        }
    }

    private static class SlotDefinition {
        final String name;
        final JunctionTree tree;
        final Map<String, JunctionTree> variants = new LinkedHashMap<>();

        SlotDefinition(String name, JunctionTree tree) {
            this.name = name;
            this.tree = tree;
        }
    }

    private static class LinkDefinition {
        final String sourceSlot;
        final String targetSlot;
        final String sourceVar;
        final String targetVar;
        TransferMode mode = TransferMode.POSTERIOR;

        LinkDefinition(String sourceSlot, String targetSlot, String sourceVar, String targetVar) {
            this.sourceSlot = sourceSlot;
            this.targetSlot = targetSlot;
            this.sourceVar = sourceVar;
            this.targetVar = targetVar;
        }
    }
}
