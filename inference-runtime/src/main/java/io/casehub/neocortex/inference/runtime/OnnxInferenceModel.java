package io.casehub.neocortex.inference.runtime;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import io.casehub.neocortex.inference.InferenceException;
import io.casehub.neocortex.inference.InferenceInput;
import io.casehub.neocortex.inference.InferenceModel;
import io.casehub.neocortex.inference.InferenceOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * ONNX Runtime implementation of {@link InferenceModel}. Wraps ONNX Runtime JNI
 * for model execution and DJL HuggingFace Tokenizers JNI for tokenization.
 *
 * <p>Thread-safe for concurrent {@link #run}/{@link #runBatch} calls once constructed.
 * One-shot lifecycle: construct, use, close.
 */
public final class OnnxInferenceModel implements InferenceModel {

    private static final Map<String, List<String>> INPUT_ALIASES = Map.of(
        "input_ids", List.of("tokens", "input.1"),
        "attention_mask", List.of("input_mask", "mask", "input.2"),
        "token_type_ids", List.of("segment_ids", "input.3")
    );

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final boolean requiresTokenTypeIds;
    private final String inputIdsName;
    private final String attentionMaskName;
    private final String tokenTypeIdsName;
    private final OptionalInt outputSize;
    private final boolean hasRank3Output;
    private volatile boolean closed;

    /**
     * Creates a new model from the given configuration. Loads the ONNX model,
     * validates its inputs (must have {@code input_ids} and {@code attention_mask}, or recognized aliases),
     * validates its outputs (at least one, each rank 2 or 3), and creates the tokenizer.
     *
     * <p>Supports multi-output models (e.g., BGE-M3 with dense, sparse, and ColBERT heads)
     * and rank-3 {@code [batch, seq_len, dim]} outputs (e.g., ColBERT token-level embeddings).
     *
     * @throws ModelLoadException if the model or tokenizer cannot be loaded,
     *         or if the model's input/output schema is invalid
     */
    public OnnxInferenceModel(ModelConfig config) {
        OrtSession openedSession = null;
        HuggingFaceTokenizer openedTokenizer = null;

        try {
            this.env = OrtEnvironment.getEnvironment();

            // Session options with thread config
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                if (config.intraOpThreads() > 0) {
                    opts.setIntraOpNumThreads(config.intraOpThreads());
                }
                if (config.interOpThreads() > 0) {
                    opts.setInterOpNumThreads(config.interOpThreads());
                }
                openedSession = env.createSession(config.modelPath().toString(), opts);
            }
            this.session = openedSession;

            // Resolve input names — only for text models (tokenizer present)
            if (config.tokenizerPath() != null) {
                Set<String> inputNames = session.getInputNames();
                this.inputIdsName = resolveInputName("input_ids", inputNames, config.inputNameOverrides());
                this.attentionMaskName = resolveInputName("attention_mask", inputNames, config.inputNameOverrides());
                String resolvedTokenTypeIds = resolveInputName("token_type_ids", inputNames, config.inputNameOverrides());
                this.requiresTokenTypeIds = resolvedTokenTypeIds != null;
                this.tokenTypeIdsName = resolvedTokenTypeIds;
            } else {
                this.inputIdsName = null;
                this.attentionMaskName = null;
                this.requiresTokenTypeIds = false;
                this.tokenTypeIdsName = null;
            }

            // Validate outputs: at least one, each must be rank 2 or rank 3
            Map<String, NodeInfo> outputInfo = session.getOutputInfo();
            if (outputInfo.isEmpty()) {
                throw new ModelLoadException("Model must have at least one output");
            }
            boolean anyRank3 = false;
            for (Map.Entry<String, NodeInfo> entry : outputInfo.entrySet()) {
                if (!(entry.getValue().getInfo() instanceof TensorInfo ti)) {
                    throw new ModelLoadException(
                        "Output '" + entry.getKey() + "' must be a tensor");
                }
                int rank = ti.getShape().length;
                if (rank < 2 || rank > 3) {
                    throw new ModelLoadException(
                        "Output '" + entry.getKey() + "' must be rank 2 or 3, got rank " + rank);
                }
                if (rank == 3) anyRank3 = true;
            }
            this.hasRank3Output = anyRank3;

            // outputSize: only meaningful for single-output rank-2 models with known dimension
            if (outputInfo.size() == 1 && !anyRank3) {
                TensorInfo tensorInfo = (TensorInfo) outputInfo.values().iterator().next().getInfo();
                long[] shape = tensorInfo.getShape();
                this.outputSize = shape[1] >= 0
                    ? OptionalInt.of((int) shape[1])
                    : OptionalInt.empty();
            } else {
                this.outputSize = OptionalInt.empty();
            }

            if (config.tokenizerPath() != null) {
                openedTokenizer = HuggingFaceTokenizer.newInstance(
                    config.tokenizerPath(),
                    Map.of("maxLength", String.valueOf(config.maxSequenceLength()),
                           "modelMaxLength", String.valueOf(config.maxSequenceLength()),
                           "truncation", "true",
                           "padding", "false"));
                this.tokenizer = openedTokenizer;
            } else {
                this.tokenizer = null;
            }

        } catch (ModelLoadException e) {
            // Clean up already-opened resources on failure
            closeQuietly(openedSession);
            closeQuietly(openedTokenizer);
            throw e;
        } catch (OrtException e) {
            closeQuietly(openedSession);
            closeQuietly(openedTokenizer);
            throw new ModelLoadException("Failed to load ONNX model: " + e.getMessage(), e);
        } catch (IOException e) {
            closeQuietly(openedSession);
            closeQuietly(openedTokenizer);
            throw new ModelLoadException("Failed to load tokenizer: " + e.getMessage(), e);
        }
    }

    @Override
    public InferenceOutput run(InferenceInput input) {
        if (closed) {throw new InferenceException("Model is closed");}
        Objects.requireNonNull(input, "input must not be null");

        return switch (input) {
            case InferenceInput.Text textInput -> runText(textInput);
            case InferenceInput.Tensor tensorInput -> runTensor(tensorInput);
        };}

    private InferenceOutput runText(InferenceInput.Text input) {
        if (tokenizer == null) {
            throw new InferenceException("Text input requires a tokenizer — this model was loaded without one");
        }
        List<String> texts = input.texts();
        Encoding encoding = texts.size() == 1
                            ? tokenizer.encode(texts.get(0))
                            : tokenizer.encode(texts.get(0), texts.get(1));

        long[]   inputIds        = encoding.getIds();
        long[]   attentionMask   = encoding.getAttentionMask();
        long[][] inputIds2d      = {inputIds};
        long[][] attentionMask2d = {attentionMask};

        List<OnnxTensor> tensors = new ArrayList<>();
        try {
            OnnxTensor idsTensor = OnnxTensor.createTensor(env, inputIds2d);
            tensors.add(idsTensor);
            OnnxTensor maskTensor = OnnxTensor.createTensor(env, attentionMask2d);
            tensors.add(maskTensor);

            Map<String, OnnxTensor> inputMap = new HashMap<>();
            inputMap.put(inputIdsName, idsTensor);
            inputMap.put(attentionMaskName, maskTensor);

            if (requiresTokenTypeIds) {
                long[][]   typeIds2d     = {encoding.getTypeIds()};
                OnnxTensor typeIdsTensor = OnnxTensor.createTensor(env, typeIds2d);
                tensors.add(typeIdsTensor);
                inputMap.put(tokenTypeIdsName, typeIdsTensor);
            }

            return runSession(inputMap);
        } catch (OrtException e) {
            throw new InferenceException("Inference failed: " + e.getMessage(), e);
        } finally {
            for (OnnxTensor t : tensors) {t.close();}
        }
    }

    private InferenceOutput runTensor(InferenceInput.Tensor input) {
        List<OnnxTensor> tensors = new ArrayList<>();
        try {
            Map<String, OnnxTensor> inputMap = new HashMap<>();
            for (Map.Entry<String, float[][]> entry : input.inputs().entrySet()) {
                OnnxTensor tensor = OnnxTensor.createTensor(env, entry.getValue());
                tensors.add(tensor);
                inputMap.put(entry.getKey(), tensor);
            }
            return runSession(inputMap);
        } catch (OrtException e) {
            throw new InferenceException("Inference failed: " + e.getMessage(), e);
        } finally {
            for (OnnxTensor t : tensors) {t.close();}
        }
    }

    private InferenceOutput runSession(Map<String, OnnxTensor> inputMap) throws OrtException {
        try (OrtSession.Result result = session.run(inputMap)) {
            Map<String, float[][]> outputs = new LinkedHashMap<>();
            for (Map.Entry<String, OnnxValue> entry : result) {
                Object value = entry.getValue().getValue();
                if (value instanceof float[][] rank2) {
                    outputs.put(entry.getKey(), new float[][]{rank2[0]});
                } else if (value instanceof float[][][] rank3) {
                    outputs.put(entry.getKey(), rank3[0]);
                }
            }
            return new InferenceOutput(outputs);
        }
    }


    @Override
    public List<InferenceOutput> runBatch(List<InferenceInput> inputs) {
        if (closed) {throw new InferenceException("Model is closed");}

        if (inputs == null) {
            throw new IllegalArgumentException("inputs must not be null");
        }
        if (inputs.isEmpty()) {return List.of();}

        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i) == null) {
                throw new IllegalArgumentException("inputs[" + i + "] must not be null");
            }
        }

        InferenceInput first = inputs.get(0);
        if (first instanceof InferenceInput.Tensor) {
            List<InferenceOutput> results = new ArrayList<>(inputs.size());
            for (InferenceInput input : inputs) {
                if (!(input instanceof InferenceInput.Tensor)) {
                    throw new IllegalArgumentException("Cannot mix Text and Tensor inputs in a batch");
                }
                results.add(runTensor((InferenceInput.Tensor) input));
            }
            return Collections.unmodifiableList(results);
        }

        int batchSize = inputs.size();

        if (tokenizer == null) {
            throw new InferenceException("Text input requires a tokenizer — this model was loaded without one");
        }

        Encoding[] encodings = new Encoding[batchSize];
        int        maxLen    = 0;
        for (int i = 0; i < batchSize; i++) {
            if (!(inputs.get(i) instanceof InferenceInput.Text textInput)) {
                throw new IllegalArgumentException("Cannot mix Text and Tensor inputs in a batch");
            }
            List<String> texts = textInput.texts();
            encodings[i] = texts.size() == 1
                           ? tokenizer.encode(texts.get(0))
                           : tokenizer.encode(texts.get(0), texts.get(1));
            maxLen       = Math.max(maxLen, encodings[i].getIds().length);
        }

        long[][] batchIds     = new long[batchSize][maxLen];
        long[][] batchMask    = new long[batchSize][maxLen];
        long[][] batchTypeIds = requiresTokenTypeIds ? new long[batchSize][maxLen] : null;
        for (int i = 0; i < batchSize; i++) {
            long[] ids  = encodings[i].getIds();
            long[] mask = encodings[i].getAttentionMask();
            System.arraycopy(ids, 0, batchIds[i], 0, ids.length);
            System.arraycopy(mask, 0, batchMask[i], 0, mask.length);
            if (batchTypeIds != null) {
                long[] typeIds = encodings[i].getTypeIds();
                System.arraycopy(typeIds, 0, batchTypeIds[i], 0, typeIds.length);
            }
        }

        List<OnnxTensor> tensors = new ArrayList<>();
        try {
            OnnxTensor idsTensor = OnnxTensor.createTensor(env, batchIds);
            tensors.add(idsTensor);
            OnnxTensor maskTensor = OnnxTensor.createTensor(env, batchMask);
            tensors.add(maskTensor);

            Map<String, OnnxTensor> inputMap = new HashMap<>();
            inputMap.put(inputIdsName, idsTensor);
            inputMap.put(attentionMaskName, maskTensor);

            if (batchTypeIds != null) {
                OnnxTensor typeIdsTensor = OnnxTensor.createTensor(env, batchTypeIds);
                tensors.add(typeIdsTensor);
                inputMap.put(tokenTypeIdsName, typeIdsTensor);
            }

            try (OrtSession.Result result = session.run(inputMap)) {
                Map<String, Object> rawOutputs = new LinkedHashMap<>();
                for (Map.Entry<String, OnnxValue> entry : result) {
                    rawOutputs.put(entry.getKey(), entry.getValue().getValue());
                }

                List<InferenceOutput> outputs = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize; i++) {
                    Map<String, float[][]> sampleOutputs = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : rawOutputs.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof float[][] rank2) {
                            sampleOutputs.put(entry.getKey(), new float[][]{rank2[i]});
                        } else if (value instanceof float[][][] rank3) {
                            int actualLen = 0;
                            for (long v : batchMask[i]) {actualLen += (int) v;}
                            float[][] stripped = Arrays.copyOf(rank3[i], actualLen);
                            sampleOutputs.put(entry.getKey(), stripped);
                        }
                    }
                    outputs.add(new InferenceOutput(sampleOutputs));
                }
                return Collections.unmodifiableList(outputs);
            }
        } catch (OrtException e) {
            throw new InferenceException("Batch inference failed: " + e.getMessage(), e);
        } finally {
            for (OnnxTensor t : tensors) {
                t.close();
            }
        }}

    @Override
    public OptionalInt outputSize() {
        return outputSize;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            session.close();
        } catch (Exception ignored) {
            // swallow — close must not throw
        }
        try {
            tokenizer.close();
        } catch (Exception ignored) {
            // swallow — close must not throw
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // swallow
            }
        }
    }

    /**
     * Resolves an input name by checking:
     * 1. Explicit overrides from config (if provided)
     * 2. Canonical name in model inputs
     * 3. Known aliases in model inputs
     *
     * @param canonicalName the standard BERT input name (input_ids, attention_mask, token_type_ids)
     * @param modelInputs   the actual input names from the ONNX model
     * @param overrides     explicit name overrides from config (nullable)
     * @return the resolved input name, or null if not required and not found
     * @throws ModelLoadException if a required input (input_ids, attention_mask) cannot be resolved
     */
    private static String resolveInputName(String canonicalName, Set<String> modelInputs,
                                           Map<String, String> overrides) {
        // Check explicit override first
        if (overrides != null && overrides.containsKey(canonicalName)) {
            String override = overrides.get(canonicalName);
            if (!modelInputs.contains(override)) {
                throw new ModelLoadException(
                    "Overridden input name '" + override + "' for '" + canonicalName +
                    "' not found in model inputs: " + modelInputs);
            }
            return override;
        }

        // Check canonical name
        if (modelInputs.contains(canonicalName)) {
            return canonicalName;
        }

        // Check aliases
        List<String> aliases = INPUT_ALIASES.get(canonicalName);
        if (aliases != null) {
            for (String alias : aliases) {
                if (modelInputs.contains(alias)) {
                    return alias;
                }
            }
        }

        // token_type_ids is optional
        if ("token_type_ids".equals(canonicalName)) {
            return null;
        }

        // input_ids and attention_mask are required
        List<String> knownAliases = INPUT_ALIASES.getOrDefault(canonicalName, List.of());
        throw new ModelLoadException(
            "Could not resolve required input '" + canonicalName + "' in model inputs: " + modelInputs
            + ". Known aliases: " + knownAliases);
    }
}
