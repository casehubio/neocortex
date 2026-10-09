package io.casehub.neocortex.cognition.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionRenderContextTest {

    @Test
    void threeArgConstructorProducesEmptyActivatedDrives() {
        var ctx = new CognitionRenderContext("agent1", "t1", null);
        assertThat(ctx.activatedDriveCategories()).isEmpty();
    }

    @Test
    void fourArgConstructorPreservesActivatedDrives() {
        var ctx = new CognitionRenderContext("agent1", "t1", null,
                                             List.of("scheming", "gloating"));
        assertThat(ctx.activatedDriveCategories()).containsExactly("scheming", "gloating");
    }

    @Test
    void activatedDriveCategoriesIsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<>(List.of("scheming"));
        var ctx = new CognitionRenderContext("agent1", "t1", null, mutable);
        mutable.add("gloating");
        assertThat(ctx.activatedDriveCategories()).containsExactly("scheming");
    }

    @Test
    void nullActivatedDriveCategoriesTreatedAsEmpty() {
        var ctx = new CognitionRenderContext("agent1", "t1", null, null);
        assertThat(ctx.activatedDriveCategories()).isEmpty();
        assertThat(ctx.isDriveActivated("any")).isFalse();
    }

    @Test
    void isDriveActivatedReturnsTrueForMatchingCategory() {
        var ctx = new CognitionRenderContext("agent1", "t1", null,
                                             List.of("scheming", "gloating"));
        assertThat(ctx.isDriveActivated("scheming")).isTrue();
        assertThat(ctx.isDriveActivated("gloating")).isTrue();
    }

    @Test
    void isDriveActivatedReturnsFalseForNonMatchingCategory() {
        var ctx = new CognitionRenderContext("agent1", "t1", null,
                                             List.of("scheming"));
        assertThat(ctx.isDriveActivated("gallantry")).isFalse();
    }

    @Test
    void isDriveActivatedReturnsFalseWhenEmpty() {
        var ctx = new CognitionRenderContext("agent1", "t1", null);
        assertThat(ctx.isDriveActivated("scheming")).isFalse();
    }
}
