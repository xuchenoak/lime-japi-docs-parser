package com.scope.test.sub.controller;

import com.scope.test.vo.WrapperVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deep package controller (com.scope.test.sub.controller), used to distinguish
 * single-segment wildcard (*) from multi-segment wildcard (**).
 */
@RestController
@RequestMapping("/api/scope/deep")
public class DeepScopeController {

    /**
     * Fetch wrapper in a deeper package
     */
    @GetMapping("/options")
    public WrapperVO options() {
        return null;
    }
}