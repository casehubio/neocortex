package io.casehub.neocortex.rag.cdi;

import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.runtime.PayloadBoostCaseRetriever;
import io.casehub.neocortex.rag.runtime.RagConfig;
import io.quarkus.arc.Unremovable;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(60)
@Unremovable
public class PayloadBoostDecorator extends PayloadBoostCaseRetriever {

    @Inject
    PayloadBoostDecorator(@Delegate @Any CaseRetriever delegate, RagConfig config) {
        super(delegate, config);
    }
}
