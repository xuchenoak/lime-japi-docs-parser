package io.gitee.sample.dto;

/**
 * Outer type holding nested types for inner/static nested/record tests
 */
public class Outer {

    /**
     * outer field
     */
    private String outerName;

    /**
     * reference to the inner class
     */
    private Inner inner;

    /**
     * Inner class (instance nested type)
     */
    public class Inner {

        /**
         * inner field
         */
        private String innerName;

        /**
         * inner method
         */
        public String innerMethod() {
            return null;
        }
    }

    /**
     * Static nested class
     */
    public static class Nested {

        /**
         * static nested field
         */
        private Integer code;
    }

    /**
     * Nested record inside outer type
     */
    public record NestedRec(
            /** 键名 */
            String key,
            /** 值 */
            Long value) {
    }
}