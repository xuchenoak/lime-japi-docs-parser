package io.gitee.xuchenoak.limejapidocs.parser.basenode;


import java.util.List;

/**
 * 注解节点类
 *
 * @author xuchenoak
 **/

public class AnnotationNode extends ClassNode {

    public AnnotationNode(String name, List<FieldNode> fieldNodeList) {
        this.setName(name);
        this.setFieldNodeList(fieldNodeList);
    }
}
