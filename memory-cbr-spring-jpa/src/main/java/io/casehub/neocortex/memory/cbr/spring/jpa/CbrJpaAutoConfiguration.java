package io.casehub.neocortex.memory.cbr.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.jpa.CbrCaseEntity;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringCbrCaseMemoryStore.class)
@EnableJpaRepositories(basePackageClasses = CbrCaseEntityRepository.class)
@EntityScan(basePackageClasses = CbrCaseEntity.class)
public class CbrJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CbrCaseMemoryStore.class)
    public SpringCbrCaseMemoryStore springCbrCaseMemoryStore(
            CbrCaseEntityRepository repo,
            ObjectMapper objectMapper) {
        return new SpringCbrCaseMemoryStore(repo, objectMapper);
    }
}
