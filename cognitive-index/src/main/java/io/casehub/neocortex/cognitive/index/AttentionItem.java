package io.casehub.neocortex.cognitive.index;

public record AttentionItem(
    TemporalEntry entry,
    double salience,
    String reason
) implements Comparable<AttentionItem> {

    @Override
    public int compareTo(AttentionItem other) {
        return Double.compare(other.salience, this.salience);
    }
}
