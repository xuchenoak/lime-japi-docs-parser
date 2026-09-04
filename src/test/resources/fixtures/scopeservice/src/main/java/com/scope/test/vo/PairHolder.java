package com.scope.test.vo;

/**
 * Holder with two sibling fields of the same object type.
 * Without parentNodeNameMap backtracking, {@code second} is wrongly treated as
 * a recursive parent of {@code first} and truncated.
 */
public class PairHolder {

    /**
     * first pair leaf
     */
    private PairLeaf first;

    /**
     * second pair leaf
     */
    private PairLeaf second;
}