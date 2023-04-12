package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参数节点
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParamNode extends BaseNode {


    /**
     * 参数类型
     */
    private ClassNode paramType;


}
