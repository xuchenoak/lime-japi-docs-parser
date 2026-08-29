package io.gitee.sample.dto;

/**
 * Form params extending base page params
 */
public class GenericParam extends PageParam {

    /**
     * sort field
     */
    private String sort;

    /**
     * descending order flag
     */
    private Boolean desc;
}