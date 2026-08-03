package io.casehub.neocortex.rag.expansion;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpansionConfigValidatorTest {

    @Test
    void warnsWhenModeIsEmpty() {
        var validator = validatorWith(stubConfig(Optional.empty(), stubDriftConfig(false, 0.7)), emptyInstance());
        var record    = captureWarning(validator);
        assertThat(record).isNotNull();
        assertThat(record.getMessage()).contains("no mode is set");
    }

    @Test
    void noWarningWhenModeIsSet() {
        var validator = validatorWith(stubConfig(Optional.of("llm"), stubDriftConfig(false, 0.7)), emptyInstance());
        var record    = captureWarning(validator);
        assertThat(record).isNull();
    }

    @Test
    void warnsWhenDriftEnabledButNoEmbeddingModel() {
        var validator = validatorWith(
                stubConfig(Optional.of("llm"), stubDriftConfig(true, 0.7)),
                emptyInstance());
        var record = captureWarning(validator);
        assertThat(record).isNotNull();
        assertThat(record.getMessage()).contains("no EmbeddingModel available");
    }

    @Test
    void noWarningWhenDriftEnabledAndEmbeddingModelPresent() {
        EmbeddingModel model = segments -> {throw new UnsupportedOperationException();};
        var validator = validatorWith(
                stubConfig(Optional.of("llm"), stubDriftConfig(true, 0.7)),
                presentInstance(model));
        var record = captureWarning(validator);
        assertThat(record).isNull();
    }

    @Test
    void rejectsThresholdAboveOne() {
        var validator = validatorWith(
                stubConfig(Optional.of("llm"), stubDriftConfig(true, 1.5)),
                emptyInstance());
        assertThatThrownBy(() -> validator.onStartup(new StartupEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("threshold")
                .hasMessageContaining("1.5");
    }

    @Test
    void rejectsThresholdBelowZero() {
        var validator = validatorWith(
                stubConfig(Optional.of("llm"), stubDriftConfig(true, -0.1)),
                emptyInstance());
        assertThatThrownBy(() -> validator.onStartup(new StartupEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("threshold")
                .hasMessageContaining("-0.1");
    }

    @Test
    void acceptsThresholdAtBoundaries() {
        var validator0 = validatorWith(
                stubConfig(Optional.of("llm"), stubDriftConfig(true, 0.0)),
                emptyInstance());
        captureWarning(validator0);

        var validator1 = validatorWith(
                stubConfig(Optional.of("llm"), stubDriftConfig(true, 1.0)),
                emptyInstance());
        captureWarning(validator1);
    }

    private ExpansionConfigValidator validatorWith(ExpansionConfig config,
                                                   Instance<EmbeddingModel> embeddingModel) {
        var validator = new ExpansionConfigValidator();
        validator.config         = config;
        validator.embeddingModel = embeddingModel;
        return validator;
    }

    private LogRecord captureWarning(ExpansionConfigValidator validator) {
        var logger   = Logger.getLogger(ExpansionConfigValidator.class.getName());
        var captured = new LogRecord[1];
        var handler = new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r.getLevel() == Level.WARNING) {captured[0] = r;}
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            validator.onStartup(new StartupEvent());
        } finally {
            logger.removeHandler(handler);
        }
        return captured[0];
    }

    private static ExpansionConfig.DriftConfig stubDriftConfig(boolean enabled, double threshold) {
        return new ExpansionConfig.DriftConfig() {
            @Override
            public boolean enabled()    {return enabled;}

            @Override
            public double threshold()   {return threshold;}

            @Override
            public DriftAction action() {return DriftAction.OBSERVE;}
        };
    }

    static ExpansionConfig stubConfig(Optional<String> mode, ExpansionConfig.DriftConfig drift) {
        return new ExpansionConfig() {
            @Override
            public boolean enabled()                         {return true;}

            @Override
            public Optional<String> mode()                   {return mode;}

            @Override
            public int hypotheticalCount()                   {return 1;}

            @Override
            public Optional<String> promptTemplate()         {return Optional.empty();}

            @Override
            public Optional<String> template()               {return Optional.empty();}

            @Override
            public Optional<String> stepBackPromptTemplate() {return Optional.empty();}

            @Override
            public DriftConfig drift()                       {return drift;}
        };
    }

    @SuppressWarnings("unchecked")
    static <T> Instance<T> emptyInstance() {
        return (Instance<T>) java.lang.reflect.Proxy.newProxyInstance(
                Instance.class.getClassLoader(),
                new Class[]{Instance.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isResolvable" -> false;
                    case "isUnsatisfied" -> true;
                    case "isAmbiguous" -> false;
                    case "get" -> {throw new IllegalStateException("No instance");}
                    case "iterator" -> java.util.Collections.emptyIterator();
                    case "stream" -> java.util.stream.Stream.empty();
                    case "forEach" -> null;
                    case "spliterator" -> java.util.Spliterators.emptySpliterator();
                    case "toString" -> "EmptyInstance";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    static <T> Instance<T> presentInstance(T value) {
        return (Instance<T>) java.lang.reflect.Proxy.newProxyInstance(
                Instance.class.getClassLoader(),
                new Class[]{Instance.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isResolvable" -> true;
                    case "isUnsatisfied" -> false;
                    case "isAmbiguous" -> false;
                    case "get" -> value;
                    case "iterator" -> java.util.List.of(value).iterator();
                    case "stream" -> java.util.stream.Stream.of(value);
                    case "forEach" -> {
                        java.util.List.of(value).forEach((java.util.function.Consumer) args[0]);
                        yield null;
                    }
                    case "spliterator" -> java.util.List.of(value).spliterator();
                    case "toString" -> "PresentInstance[" + value + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
