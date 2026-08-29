package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 参数节点
 *
 * @author xuchenoak
 **/
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ParamNode extends BaseNode {


    /**
     * 参数类型
     */
    private ClassNode paramType;


}
