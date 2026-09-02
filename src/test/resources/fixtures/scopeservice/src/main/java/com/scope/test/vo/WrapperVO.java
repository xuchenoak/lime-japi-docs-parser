package com.scope.test.vo;

import java.util.List;

/**
 * Wrapper object holding a list of leaf nodes
 */
public class WrapperVO {

    /**
     * available leaf nodes
     */
    private List<LeafVO> available;

    /**
     * active ids
     */
    private List<Long> active;
}