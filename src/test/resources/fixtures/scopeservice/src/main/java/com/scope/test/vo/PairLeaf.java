package com.scope.test.vo;

/**
 * Leaf type referenced ONLY by PairHolder, so that PairHolder is the first
 * (and only) place in the parse session that exercises sibling fields of this type.
 */
public class PairLeaf {

    /**
     * key
     */
    private Long key;

    /**
     * label
     */
    private String label;
}