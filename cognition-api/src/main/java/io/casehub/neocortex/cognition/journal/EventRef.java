package io.casehub.neocortex.cognition.journal;

public sealed interface EventRef {

    record Init() implements EventRef {
        @Override
        public String toString() { return "init"; }
    }

    record Tick(int number) implements EventRef {
        @Override
        public String toString() { return "tick:" + number; }
    }

    record Event(String id) implements EventRef {
        @Override
        public String toString() { return "event:" + id; }
    }

    record Consolidation(EventRef trigger) implements EventRef {
        @Override
        public String toString() { return "consolidation:" + trigger; }
    }

    static EventRef init() { return new Init(); }

    static EventRef tick(int number) { return new Tick(number); }

    static EventRef event(String id) { return new Event(id); }

    static EventRef consolidation(EventRef trigger) { return new Consolidation(trigger); }

    static EventRef parse(String s) {
        if ("init".equals(s)) return init();
        if (s.startsWith("tick:")) return tick(Integer.parseInt(s.substring(5)));
        if (s.startsWith("event:")) return event(s.substring(6));
        if (s.startsWith("consolidation:")) return consolidation(parse(s.substring(14)));
        throw new IllegalArgumentException("Unknown eventRef format: " + s);
    }
}
