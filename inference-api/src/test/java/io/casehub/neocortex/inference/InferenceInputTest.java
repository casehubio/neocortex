package io.casehub.neocortex.inference;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class InferenceInputTest {

    // --- Text factory: of(String) ---

    @Test
    void of_single_text() {
        var input = InferenceInput.of("hello");
        assertThat(input.texts()).containsExactly("hello");
    }

    @Test
    void of_null_text_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> InferenceInput.of(null))
            .withMessageContaining("null");
    }

    // --- Text factory: pair(String, String) ---

    @Test
    void pair_two_texts() {
        var input = InferenceInput.pair("premise", "hypothesis");
        assertThat(input.texts()).containsExactly("premise", "hypothesis");
    }

    @Test
    void pair_null_first_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> InferenceInput.pair(null, "second"))
            .withMessageContaining("first");
    }

    @Test
    void pair_null_second_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> InferenceInput.pair("first", null))
            .withMessageContaining("second");
    }

    // --- Text validation ---

    @Test
    void null_list_throws() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new InferenceInput.Text(null))
            .withMessageContaining("must not be empty");
    }

    @Test
    void empty_list_throws() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new InferenceInput.Text(List.of()))
            .withMessageContaining("must not be empty");
    }

    @Test
    void three_texts_throws() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new InferenceInput.Text(List.of("a", "b", "c")))
            .withMessageContaining("at most 2");
    }

    @Test
    void text_constructor_makes_defensive_copy() {
        var mutable = new ArrayList<>(List.of("original"));
        var input = new InferenceInput.Text(mutable);
        mutable.set(0, "mutated");
        assertThat(input.texts()).containsExactly("original");
    }

    @Test
    void texts_returns_unmodifiable_list() {
        var input = InferenceInput.of("hello");
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> input.texts().add("extra"));
    }

    // --- Tensor factory ---

    @Test
    void tensor_creates_valid_input() {
        var input = InferenceInput.tensor(Map.of("features", new float[][]{{1f, 2f, 3f}}));
        assertThat(input.inputs()).containsKey("features");
        assertThat(input.inputs().get("features")[0]).containsExactly(1f, 2f, 3f);
    }

    @Test
    void tensor_null_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> InferenceInput.tensor(null));
    }

    @Test
    void tensor_empty_map_throws() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> InferenceInput.tensor(Map.of()))
            .withMessageContaining("must not be empty");
    }

    @Test
    void tensor_defensive_copy() {
        float[][] original = {{1f, 2f}};
        var input = InferenceInput.tensor(Map.of("x", original));
        original[0][0] = 99f;
        assertThat(input.inputs().get("x")[0][0]).isEqualTo(1f);
    }

    @Test
    void tensor_inputs_unmodifiable() {
        var input = InferenceInput.tensor(Map.of("x", new float[][]{{1f}}));
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> input.inputs().put("y", new float[][]{{2f}}));
    }

    // --- Sealed type checks ---

    @Test
    void of_returns_text_variant() {
        assertThat(InferenceInput.of("hello")).isInstanceOf(InferenceInput.Text.class);
    }

    @Test
    void tensor_returns_tensor_variant() {
        assertThat(InferenceInput.tensor(Map.of("x", new float[][]{{1f}})))
            .isInstanceOf(InferenceInput.Tensor.class);
    }
}
