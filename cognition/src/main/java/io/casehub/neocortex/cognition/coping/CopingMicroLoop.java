package io.casehub.neocortex.cognition.coping;

import io.casehub.neocortex.cognition.appraisal.AppraisalContext;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.EmotionMapper;
import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.appraisal.SecCheck;
import io.casehub.neocortex.cognition.appraisal.SecResult;
import io.casehub.neocortex.cognition.regulation.CognitiveLoadLevel;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.index.CopingDefaults;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CopingMicroLoop {

    private static final Logger LOG = Logger.getLogger(CopingMicroLoop.class.getName());

    private final CopingStrategyPredictor predictor;
    private final CopingGenerationPhase generationPhase;
    private final List<SecCheck> keywordChecks;
    private final CopingConfig config;

    public CopingMicroLoop(CopingStrategyPredictor predictor,
                           CopingGenerationPhase generationPhase,
                           List<SecCheck> keywordChecks) {
        this(predictor, generationPhase, keywordChecks, CopingConfig.defaults());
    }

    public CopingMicroLoop(CopingStrategyPredictor predictor,
                           CopingGenerationPhase generationPhase,
                           List<SecCheck> keywordChecks,
                           CopingConfig config) {
        this.predictor = predictor;
        this.generationPhase = generationPhase;
        this.keywordChecks = List.copyOf(keywordChecks);
        this.config = config;
    }

    public @Nullable CopingState execute(AppraisalContext appraisalContext,
                                          AppraisalResult appraisalResult,
                                          @Nullable CopingDefaults copingDefaults,
                                          @Nullable DispositionAxes axes,
                                          @Nullable CognitiveLoadLevel cognitiveLoad) {
        var state = CopingState.initial(
                appraisalContext.situation().narrative(),
                appraisalResult.secDimensions(),
                appraisalResult.emotions());

        while (state.iteration() < config.copingMaxIterations()) {
            if (!hasCopingOpportunity(state.workingEmotions())) {
                break;
            }

            // Layer 2: CAPS coping attractor resolution — deferred to #997
            CopingStrategy pinnedStrategy = null;

            // Layer 3: mechanical predictor fallback
            if (pinnedStrategy == null) {
                var prediction = predictor.predict(
                        copingDefaults, state.secDimensions(), state.workingEmotions(),
                        axes, cognitiveLoad);
                pinnedStrategy = prediction.map(CopingPrediction::strategy).orElse(null);
            }

            // Layer 4: LLM generation
            var copingResult = generationPhase.generate(
                    appraisalContext, state, pinnedStrategy, copingDefaults);
            if (copingResult == null) {
                LOG.log(Level.WARNING, "Coping generation returned null at iteration {0}", state.iteration());
                break;
            }

            // Apply effects and record strategy attempt
            state = state.withInterpretation(copingResult.narrative())
                         .withAccumulatedEffects(copingResult)
                         .withStrategy(new CopingStrategyAttempt(
                                 copingResult.strategy(), state.iteration(),
                                 copingResult.narrative()));

            // Oscillation detection — strategy already attempted in a prior iteration
            if (state.strategiesAttempted().stream()
                    .filter(a -> a.strategy() == copingResult.strategy())
                    .count() > 1) {
                break;
            }

            // Keyword SEC re-evaluation on the reinterpreted narrative
            state = reEvaluate(appraisalContext, state);

            state = state.withIteration(state.iteration() + 1);
        }

        return state;
    }

    boolean hasCopingOpportunity(List<CognitiveEmotion> emotions) {
        return emotions.stream()
                .anyMatch(e -> e.intensity() > config.copingOpportunityThreshold());
    }

    CopingState reEvaluate(AppraisalContext originalContext, CopingState state) {
        var syntheticContext = new AppraisalContext(
                originalContext.agentId(),
                PerceivedSituation.passThrough(state.interpretationNarrative()),
                originalContext.drives(),
                originalContext.weights(),
                originalContext.habituationConfig(),
                originalContext.habituation(),
                originalContext.currentMood(),
                originalContext.dispositionAxes());

        var secResults = new ArrayList<SecResult>();
        for (var check : keywordChecks) {
            secResults.add(check.evaluate(syntheticContext));
        }

        var newDimensions = EmotionMapper.mergeDimensions(secResults);
        var newEmotions = EmotionMapper.mapEmotions(secResults, originalContext.agentId());

        return state.withDimensions(newDimensions).withEmotions(newEmotions);
    }
}
