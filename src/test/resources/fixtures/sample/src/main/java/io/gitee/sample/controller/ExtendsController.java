package io.gitee.sample.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller extending BaseController to verify parent method parsing
 */
@RestController
@RequestMapping("/api/ext")
public class ExtendsController extends BaseController {

    /**
     * override parent method with @Override only, mapping annotation inherited from parent
     */
    @Override
    public String overridable() {
        return "child";
    }

    /**
     * own interface method
     */
    @GetMapping("/own")
    public String own() {
        return null;
    }
}