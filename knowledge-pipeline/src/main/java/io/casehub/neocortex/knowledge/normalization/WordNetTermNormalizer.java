package io.casehub.neocortex.knowledge.normalization;

import io.casehub.neocortex.knowledge.ExpandedTerm;
import io.casehub.neocortex.knowledge.KnowledgeDomain;
import io.casehub.neocortex.knowledge.TermNormalizer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.dictionary.Dictionary;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class WordNetTermNormalizer implements TermNormalizer {

    private static final Logger LOG = Logger.getLogger(WordNetTermNormalizer.class.getName());

    private static final Map<String, List<String>> DOMAIN_TO_LEX_FILES = Map.of(
        KnowledgeDomain.PLACE, List.of("noun.artifact", "noun.location"),
        KnowledgeDomain.THING, List.of("noun.artifact", "noun.object"),
        KnowledgeDomain.ACTIVITY, List.of("noun.act")
    );

    private Dictionary dictionary;

    @PostConstruct
    void init() {
        try {
            dictionary = Dictionary.getDefaultResourceInstance();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load WordNet dictionary", e);
        }
    }

    @Override
    public ExpandedTerm normalize(String term, String domain) {
        if (dictionary == null || term == null || term.isBlank() || domain == null) {
            return ExpandedTerm.passthrough(term);
        }

        List<String> lexFiles = DOMAIN_TO_LEX_FILES.get(domain);
        if (lexFiles == null) {
            return ExpandedTerm.passthrough(term);
        }

        try {
            String lookupTerm = term.trim().toLowerCase();
            IndexWord indexWord = dictionary.lookupIndexWord(POS.NOUN, lookupTerm);
            if (indexWord == null) {
                return ExpandedTerm.passthrough(term);
            }

            Synset matchedSynset = null;
            for (Synset synset : indexWord.getSenses()) {
                if (lexFiles.contains(synset.getLexFileName())) {
                    matchedSynset = synset;
                    break;
                }
            }

            if (matchedSynset == null) {
                return ExpandedTerm.passthrough(term);
            }

            String canonical = matchedSynset.getWords().get(0).getLemma().replace('_', ' ');
            Set<String> variants = new LinkedHashSet<>();
            variants.add(canonical);
            for (Word w : matchedSynset.getWords()) {
                variants.add(w.getLemma().replace('_', ' '));
            }

            return new ExpandedTerm(canonical, variants);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "WordNet lookup failed for: " + term, e);
            return ExpandedTerm.passthrough(term);
        }
    }
}
