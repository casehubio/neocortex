package io.casehub.neocortex.corpus.management.impl;

public record SearchResult(
        String id,
        String title,
        String domain,
        String type,
        int score,
        String body,
        double relevance,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Double crossEncoderScore,
        String source,
        String sourcePrefix,
        @com.fasterxml.jackson.annotation.JsonIgnore java.util.List<String> seeAlsoIds,
        @com.fasterxml.jackson.annotation.JsonIgnore java.util.Map<String, String> metadata,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer annotationCount,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) java.util.List<io.casehub.neocortex.corpus.management.Annotation> annotations) {

    public SearchResult(String id, String title, String domain, String type,
                        int score, String body, double relevance, Double crossEncoderScore,
                        String source, String sourcePrefix) {
        this(id, title, domain, type, score, body, relevance, crossEncoderScore,
             source, sourcePrefix, java.util.List.of(), java.util.Map.of(), null, null);
    }

    public SearchResult(String id, String title, String domain, String type,
                        int score, String body, double relevance, Double crossEncoderScore,
                        String source, String sourcePrefix, java.util.List<String> seeAlsoIds) {
        this(id, title, domain, type, score, body, relevance, crossEncoderScore,
             source, sourcePrefix, seeAlsoIds, java.util.Map.of(), null, null);
    }

    public SearchResult(String id, String title, String domain, String type,
                        int score, String body, double relevance, Double crossEncoderScore,
                        String source, String sourcePrefix, java.util.List<String> seeAlsoIds,
                        java.util.Map<String, String> metadata) {
        this(id, title, domain, type, score, body, relevance, crossEncoderScore,
             source, sourcePrefix, seeAlsoIds, metadata, null, null);
    }

    public SearchResult withAdjustedScores(double newRelevance, Double newCeScore) {
        return new SearchResult(id, title, domain, type, score, body,
                                newRelevance, newCeScore, source, sourcePrefix, seeAlsoIds, metadata,
                                annotationCount, annotations);
    }

    public SearchResult withPreview(int maxLength) {
        if (maxLength <= 0 || body == null || body.length() <= maxLength) {return this;}
        String truncated = body.substring(0, maxLength) + "...";
        return new SearchResult(id, title, domain, type, score, truncated,
                                relevance, crossEncoderScore, source, sourcePrefix, seeAlsoIds, metadata,
                                annotationCount, annotations);
    }

    public SearchResult withAnnotationData(Integer count, java.util.List<io.casehub.neocortex.corpus.management.Annotation> annotationList) {
        return new SearchResult(id, title, domain, type, score, body,
                                relevance, crossEncoderScore, source, sourcePrefix, seeAlsoIds, metadata,
                                count, annotationList);
    }

    public SearchResult withExtraMetadata(String key, String value) {
        var extended = new java.util.LinkedHashMap<>(metadata != null ? metadata : java.util.Map.<String, String>of());
        extended.put(key, value);
        return new SearchResult(id, title, domain, type, score, body,
                                relevance, crossEncoderScore, source, sourcePrefix, seeAlsoIds,
                                java.util.Map.copyOf(extended), annotationCount, annotations);
    }


    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String stalenessThreshold() {return metadata != null ? metadata.get("staleness_threshold") : null;}

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    public java.util.List<String> tags() {
        if (metadata == null) {return java.util.List.of();}
        String joined = metadata.get("tags_joined");
        if (joined == null || joined.isEmpty()) {return java.util.List.of();}
        return java.util.List.of(joined.split("\\|"));
    }

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String lastReviewed() {return metadata != null ? metadata.get("last_reviewed") : null;}

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String author() {return metadata != null ? metadata.get("author") : null;}

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String verifiedOn() {return metadata != null ? metadata.get("verified_on") : null;}

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public Integer relationshipCount() {
        if (metadata == null) {return null;}
        String count = metadata.get("relationship_count");
        return count != null ? Integer.parseInt(count) : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String createdBy() {return metadata != null ? metadata.get("created_by") : null;}

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String updatedBy() {return metadata != null ? metadata.get("updated_by") : null;}

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String createdAt() {return metadata != null ? metadata.get("created_at") : null;}

    @com.fasterxml.jackson.annotation.JsonProperty
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String updatedAt() {return metadata != null ? metadata.get("updated_at") : null;}

}
