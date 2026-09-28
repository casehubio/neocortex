package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.mindmap.ActionAppraisal;
import io.casehub.neocortex.mindmap.ActionContext;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpActionAppraisal implements ActionAppraisal {
    @Override
    public List<CognitiveEmotion> appraise(ActionContext context) {
        return List.of();
    }
}
