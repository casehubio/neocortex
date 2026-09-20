package io.casehub.neocortex.memory.cbr.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.jpa.CbrRecordEntity;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringCbrRecordStore.class)
@EnableJpaRepositories(basePackageClasses = CbrRecordEntityRepository.class)
@EntityScan(basePackageClasses = CbrRecordEntity.class)
public class CbrJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CbrRecordStore.class)
    public SpringCbrRecordStore springCbrRecordStore(
            CbrRecordEntityRepository repo,
            ObjectMapper objectMapper) {
        return new SpringCbrRecordStore(repo, objectMapper);
    }
}
