package io.gitee.sample.dto;

/**
 * Self referencing object used to verify cycle protection
 */
public class SelfRef {

    /**
     * node name
     */
    private String name;

    /**
     * parent reference, may create a cyclic graph
     */
    private SelfRef parent;
}