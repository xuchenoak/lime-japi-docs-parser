package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import lombok.NoArgsConstructor;

/**
 * 泛型节点
 *
 * @author xuchenoak
 **/
@NoArgsConstructor
public class GenericityNode extends ClassNode {

    public GenericityNode(String name, String fullName) {
        super.setName(name);
        super.setFullName(fullName);
    }
}
