package io.casehub.neocortex.memory.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.jpa.MemoryEntry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringMemoryStore.class)
@EnableJpaRepositories(basePackageClasses = MemoryEntryRepository.class)
@EntityScan(basePackageClasses = MemoryEntry.class)
public class MemoryJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CaseMemoryStore.class)
    public SpringMemoryStore springMemoryStore(
            MemoryEntryRepository repo,
            CurrentPrincipal principal,
            ObjectMapper objectMapper,
            @Value("${casehub.memory.jpa.fts.enabled:true}") boolean ftsEnabled,
            @Value("${casehub.memory.jpa.fts.language:english}") String ftsLanguage) {
        return new SpringMemoryStore(repo, principal, objectMapper, ftsEnabled, ftsLanguage);
    }
}
