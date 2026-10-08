package io.casehub.neocortex.cognition.drive;

import java.util.Map;

public record ModulationLayer(Map<DriveAxis, Double> modulation, double strength, String source) {
    public ModulationLayer {
        modulation = Map.copyOf(modulation);
    }
}
