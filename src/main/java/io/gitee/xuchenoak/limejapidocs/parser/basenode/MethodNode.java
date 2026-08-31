package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 方法节点
 *
 * @author xuchenoak
 **/
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class MethodNode<T extends MethodNode> extends BaseNode {

    /**
     * 参数节点集
     */
    private List<ParamNode> paramNodeList;

    /**
     * 返回对象节点
     */
    private ClassNode returnNode;

    public MethodNode(T t) {
        super(t);
        this.paramNodeList = t.getParamNodeList();
        this.returnNode = t.getReturnNode();
    }

    public void addParamNode(ParamNode paramNode) {
        if (paramNodeList == null) {
            paramNodeList = new ArrayList<>();
        }
        paramNodeList.add(paramNode);
    }

    /**
     * 根据参数名获取参数节点
     *
     * @param paramName 参数名
     * @return 参数节点
     * @deprecated V1.0.6 起标记，库内已无使用方，待后续版本移除
     */
    @Deprecated
    public ParamNode getParamNodeByName(String paramName) {
        if (StringUtil.isBlank(paramName) || ListUtil.isBlank(paramNodeList)) {
            return null;
        }
        for (ParamNode paramNode : paramNodeList) {
            if (paramName.equals(paramNode.getName())) {
                return paramNode;
            }
        }
        return null;
    }

    public boolean isOverride() {
        return getAnnotationNodeByName("Override") != null;
    }
}
