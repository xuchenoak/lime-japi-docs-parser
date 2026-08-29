package io.gitee.sample.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Paged query filters
 */
public class UserQuery {

    /**
     * search keyword
     */
    private String keyword;

    /**
     * current page number
     */
    private Integer page;

    /**
     * page size
     */
    private Integer size;

    /**
     * start date filter
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;
}