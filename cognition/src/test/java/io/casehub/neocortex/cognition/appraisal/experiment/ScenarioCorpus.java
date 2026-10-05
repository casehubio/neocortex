package io.casehub.neocortex.cognition.appraisal.experiment;

import io.casehub.neocortex.cognition.appraisal.Drive;
import io.casehub.neocortex.cognition.appraisal.DriveCategory;
import java.util.List;

public final class ScenarioCorpus {

    public record Scenario(String name, String observation, List<Drive> drives) {}

    private static final List<Drive> RESEARCH_DRIVES = List.of(
            new Drive("curiosity", DriveCategory.BASELINE, 0.8, "research"),
            new Drive("competence", DriveCategory.BASELINE, 0.6, "solve"));

    private static final List<Drive> SOCIAL_DRIVES = List.of(
            new Drive("affiliation", DriveCategory.BASELINE, 0.7, "team"),
            new Drive("competence", DriveCategory.BASELINE, 0.5, "review"));

    private static final List<Drive> MINIMAL_DRIVES = List.of(
            new Drive("curiosity", DriveCategory.BASELINE, 0.3, "explore"));

    private ScenarioCorpus() {}

    public static List<Scenario> scenarios() {
        return List.of(
            new Scenario("achievement",
                "I just solved a complex algorithm problem that nobody else could figure out",
                RESEARCH_DRIVES),
            new Scenario("deployment_success",
                "I successfully deployed the new feature and all tests are passing",
                RESEARCH_DRIVES),
            new Scenario("proposal_approved",
                "The architecture review board approved my proposal unanimously",
                SOCIAL_DRIVES),
            new Scenario("metrics_improved",
                "The quarterly report shows our metrics have improved across the board",
                MINIMAL_DRIVES),
            new Scenario("server_down_no_access",
                "The server is completely down and I have no access to the logs or the recovery tools",
                RESEARCH_DRIVES),
            new Scenario("deadline_moved",
                "The deadline was moved up by a week and I'm already behind schedule",
                SOCIAL_DRIVES),
            new Scenario("data_errors",
                "I discovered that the data we've been analyzing contains systematic errors",
                RESEARCH_DRIVES),
            new Scenario("acquisition_threat",
                "An unexpected acquisition means our entire tech stack might change",
                SOCIAL_DRIVES),
            new Scenario("uncertain_solution",
                "I found a potential solution but I'm not sure if it will work",
                RESEARCH_DRIVES),
            new Scenario("colleague_breakthrough",
                "My colleague achieved a breakthrough in the project",
                SOCIAL_DRIVES),
            new Scenario("sla_violation",
                "The client reported a critical bug that violates our SLA commitments",
                SOCIAL_DRIVES),
            new Scenario("process_ignored",
                "Someone on the team keeps ignoring the code review process",
                SOCIAL_DRIVES),
            new Scenario("locked_no_key",
                "The door is locked and I don't have the key",
                MINIMAL_DRIVES),
            new Scenario("equipped_and_ready",
                "I can handle this — I have all the tools and training I need",
                RESEARCH_DRIVES),
            new Scenario("routine_standup",
                "A routine standup meeting with no surprises",
                MINIMAL_DRIVES)
        );
    }
}
