package io.gitee.sample.common;

import java.util.List;

/**
 * Generic page wrapper
 */
public class PageResult<T> {

    /**
     * total count
     */
    private Long total;

    /**
     * page records
     */
    private List<T> records;
}