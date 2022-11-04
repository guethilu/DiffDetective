package org.variantsync.diffdetective.variationtree.source;

/**
 * A reference to the source of the variation tree.
 */
public interface VariationTreeSource {
    /**
     * The source of the variation tree is unknown.
     * Should be avoided if possible.
     */
    public static VariationTreeSource Unknown = new VariationTreeSource() {
        @Override
        public String toString() {
            return "unknown";
        }
    };

    String toString();
}
