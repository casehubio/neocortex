package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CbrDemoRunner {

    public static Map<String, List<?>> run(CbrCaseMemoryStore store) {
        var results = new LinkedHashMap<String, List<?>>();
        results.put("AML Investigation", AmlInvestigationDemo.run(store));
        results.put("Clinical Adverse Event", ClinicalAdverseEventDemo.run(store));
        results.put("DevTown PR Review", DevtownPrReviewDemo.run(store));
        results.put("Life Contractor", LifeContractorDemo.run(store));
        results.put("IoT Situation", IotSituationDemo.run(store));
        results.put("QuarkMind Battle", QuarkmindBattleDemo.run(store));
        return results;
    }

    public static void main(String[] args) {
        var store = new InMemoryCbrCaseMemoryStore();

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  CaseHub CBR — Six Domain Demos                 ║");
        System.out.println("║  Same SPI, different schemas, different domains  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        AmlInvestigationDemo.printResults(AmlInvestigationDemo.run(store));
        System.out.println("\n" + "─".repeat(60) + "\n");
        ClinicalAdverseEventDemo.printResults(ClinicalAdverseEventDemo.run(store));
        System.out.println("\n" + "─".repeat(60) + "\n");
        DevtownPrReviewDemo.printResults(DevtownPrReviewDemo.run(store));
        System.out.println("\n" + "─".repeat(60) + "\n");
        LifeContractorDemo.printResults(LifeContractorDemo.run(store));
        System.out.println("\n" + "─".repeat(60) + "\n");
        IotSituationDemo.printResults(IotSituationDemo.run(store));
        System.out.println("\n" + "─".repeat(60) + "\n");
        QuarkmindBattleDemo.printResults(QuarkmindBattleDemo.run(store));
    }
}
