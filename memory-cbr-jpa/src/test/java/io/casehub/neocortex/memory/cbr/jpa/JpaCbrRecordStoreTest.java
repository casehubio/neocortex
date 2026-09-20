package io.casehub.neocortex.memory.cbr.jpa;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.testing.CbrRecordStoreContractTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;

@QuarkusTest
class JpaCbrRecordStoreTest extends CbrRecordStoreContractTest {

    @Inject
    CbrRecordStore store;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void clearDatabase() {
        em.createQuery("DELETE FROM CbrRecordEntity").executeUpdate();
    }

    @Override
    protected CbrRecordStore store() {
        return store;
    }
}
