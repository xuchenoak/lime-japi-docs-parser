package io.gitee.sample.dto;

/**
 * References Outer.Inner by dotted same-package name without import
 */
public class OuterUse {

    /**
     * same-package inner reference without import
     */
    private Outer.Inner inner;
}