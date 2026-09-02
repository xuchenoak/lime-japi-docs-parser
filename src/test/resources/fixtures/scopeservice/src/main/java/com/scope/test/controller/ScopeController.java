package com.scope.test.controller;

import com.scope.test.common.AjaxResult;
import com.scope.test.service.ScopeService;
import com.scope.test.vo.PairHolder;
import com.scope.test.vo.WrapperVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scope management controller
 */
@RestController
@RequestMapping("/api/scope")
public class ScopeController {

    private final ScopeService scopeService;

    public ScopeController(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    /**
     * Fetch available scopes wrapped in AjaxResult
     */
    @GetMapping("/options")
    public AjaxResult<WrapperVO> options() {
        return null;
    }

    /**
     * Fetch a pair of leaf nodes (two sibling fields of the same type)
     */
    @GetMapping("/pair")
    public PairHolder pair() {
        return null;
    }
}