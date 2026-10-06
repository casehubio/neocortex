package io.casehub.neocortex.knowledge.normalization;

import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.dictionary.Dictionary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtJwnlSmokeTest {

    @Test
    void loadsDictionaryAndLooksUpWord() throws Exception {
        Dictionary dict = Dictionary.getDefaultResourceInstance();
        assertThat(dict).isNotNull();

        IndexWord word = dict.lookupIndexWord(POS.NOUN, "restaurant");
        assertThat(word).isNotNull();
        assertThat(word.getLemma()).isEqualTo("restaurant");
        assertThat(word.getSenses()).isNotEmpty();
    }

    @Test
    void findsCompoundTerm() throws Exception {
        Dictionary dict = Dictionary.getDefaultResourceInstance();
        IndexWord word = dict.lookupIndexWord(POS.NOUN, "coffee shop");
        assertThat(word).isNotNull();
    }

    @Test
    void lookupUnknownTermReturnsNull() throws Exception {
        Dictionary dict = Dictionary.getDefaultResourceInstance();
        IndexWord word = dict.lookupIndexWord(POS.NOUN, "xyznotaword");
        assertThat(word).isNull();
    }
}
