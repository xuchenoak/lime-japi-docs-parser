package com.scope.test.common;

import java.io.Serializable;

/**
 * Generic result wrapper (simulates a real project AjaxResult)
 */
public class AjaxResult<T> implements Serializable {

    /**
     * status code
     */
    private int code;

    /**
     * response message
     */
    private String msg;

    /**
     * response payload
     */
    private T data;
}