package io.casehub.neocortex.memory.seeding;

public final class SalienceDefaults {
    private SalienceDefaults() {}

    public static double forPeriod(String developmentalPeriod) {
        if (developmentalPeriod == null) return 1.0;
        return switch (developmentalPeriod) {
            case "infancy"     -> 3.0;
            case "childhood"   -> 2.0;
            case "adolescence" -> 1.5;
            case "adult"       -> 1.0;
            default            -> 1.0;
        };
    }
}
