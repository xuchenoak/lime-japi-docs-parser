package io.gitee.sample.dto;

import java.util.List;
import java.util.Map;

/**
 * Edge type references used to verify boundary handling
 */
public class EdgeTypes {

    /**
     * JDK nested class (binary name java.util.Map$Entry)
     */
    private Map.Entry<String, String> entry;

    /**
     * double nested generic list
     */
    private List<List<User>> nestedList;

    /**
     * map value is a POJO
     */
    private Map<String, UserProfile> profileMap;

    /**
     * map value is a list of POJO
     */
    private Map<String, List<User>> userListMap;
}