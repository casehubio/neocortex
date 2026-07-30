package io.casehub.neocortex.inference.tasks;

import io.casehub.neocortex.inference.InferenceException;
import io.casehub.neocortex.inference.InferenceInput;
import io.casehub.neocortex.inference.InferenceModel;
import io.casehub.neocortex.inference.InferenceOutput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TensorClassifier {

    private final InferenceModel model;
    private final List<String> labels;

    public TensorClassifier(final InferenceModel model, final List<String> labels) {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        if (labels == null || labels.isEmpty())
            throw new IllegalArgumentException("labels must not be null or empty");
        this.labels = List.copyOf(labels);
        model.outputSize().ifPresent(size -> {
            if (size != this.labels.size()) {
                throw new IllegalArgumentException(
                    "labels size (" + this.labels.size() + ") does not match outputSize (" + size + ")");
            }
        });
        this.model = model;
    }

    public ClassificationResult classify(final Map<String, float[][]> inputs) {
        if (inputs == null || inputs.isEmpty())
            throw new IllegalArgumentException("inputs must not be null or empty");

        final InferenceOutput output = model.run(InferenceInput.tensor(inputs));
        final float[] values = output.values();

        if (values.length != labels.size()) {
            throw new InferenceException(
                "Expected " + labels.size() + " output values, got " + values.length);
        }

        final float[] probs = Softmax.apply(values);

        int argmax = 0;
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > probs[argmax]) argmax = i;
        }

        final Map<String, Float> scores = new LinkedHashMap<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            scores.put(labels.get(i), probs[i]);
        }

        return new ClassificationResult(labels.get(argmax), probs[argmax], scores);
    }
}
