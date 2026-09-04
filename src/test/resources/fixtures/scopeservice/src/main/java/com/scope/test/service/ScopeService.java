package com.scope.test.service;

import com.scope.test.vo.LeafVO;
import com.scope.test.vo.WrapperVO;

import java.util.List;

/**
 * Service interface whose method return types share the parse chain.
 * CRITICAL: {@code listLeaf} (returning {@code List<LeafVO>}) is declared BEFORE
 * {@code getWrapper} (returning {@code WrapperVO} whose field is {@code List<LeafVO>}).
 * If the parser's recursion-guard map is not backtracked, {@code LeafVO} registered by
 * {@code listLeaf} wrongly truncates {@code WrapperVO#available}.
 */
public interface ScopeService {

    /**
     * list leaf nodes
     */
    List<LeafVO> listLeaf();

    /**
     * fetch wrapper with available leaves
     */
    WrapperVO getWrapper();
}