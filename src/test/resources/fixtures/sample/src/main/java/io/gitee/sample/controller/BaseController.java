package io.gitee.sample.controller;

import org.springframework.web.bind.annotation.GetMapping;

/**
 * Base controller holding interface methods for inheritance tests
 */
public class BaseController {

    /**
     * only declared in parent controller
     */
    @GetMapping("/parent")
    public String parentOnly() {
        return null;
    }

    /**
     * overridable method whose child overrides without mapping annotation
     */
    @GetMapping("/child-then")
    public String overridable() {
        return "base";
    }

    /**
     * plain parent helper without mapping annotation
     */
    public String parentHelper() {
        return null;
    }
}