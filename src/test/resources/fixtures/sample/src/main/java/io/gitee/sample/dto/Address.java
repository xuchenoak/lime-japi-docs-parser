package io.gitee.sample.dto;

import java.util.List;

/**
 * User address
 */
public class Address {

    /**
     * city name
     */
    private String city;

    /**
     * street name
     */
    private String street;

    /**
     * nested child addresses
     */
    private List<Address> children;
}