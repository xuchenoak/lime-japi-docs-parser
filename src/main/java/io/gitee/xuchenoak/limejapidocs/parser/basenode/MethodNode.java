package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 方法节点
 *
 * @author xuchenoak
 **/
@Data
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
