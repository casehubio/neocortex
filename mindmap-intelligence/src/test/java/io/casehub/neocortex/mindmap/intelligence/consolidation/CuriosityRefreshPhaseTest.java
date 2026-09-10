package io.casehub.neocortex.mindmap.intelligence.consolidation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CuriosityRefreshPhaseTest {

    @Test
    void implementsConsolidationPhase() {
        assertThat(ConsolidationPhase.class).isAssignableFrom(CuriosityRefreshPhase.class);
    }

    @Test
    void name_returnsCuriosityRefresh() {
        assertThat(new CuriosityRefreshPhase(null).name()).isEqualTo("curiosity-refresh");
    }
}
