package io.casehub.neocortex.memory.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "memory_entry")
public class MemoryEntry {

    @Id
    @Column(name = "memory_id", length = 36, nullable = false)
    public String memoryId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(name = "entity_id", nullable = false)
    public String entityId;

    @Column(name = "domain", nullable = false)
    public String domain;

    @Column(name = "case_id")
    public String caseId;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    public String text;

    @Column(name = "attributes", nullable = false, columnDefinition = "TEXT")
    public String attributes;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "confidence")
    public Double confidence;

    @Column(name = "pleasure")
    public Double pleasure;

    @Column(name = "arousal")
    public Double arousal;

    @Column(name = "dominance")
    public Double dominance;

    @Column(name = "subject_type", nullable = false)
    public String subjectType;

    @Column(name = "principal_id")
    public String principalId;

    @Column(name = "shared_with", columnDefinition = "TEXT")
    public String sharedWith;
}
