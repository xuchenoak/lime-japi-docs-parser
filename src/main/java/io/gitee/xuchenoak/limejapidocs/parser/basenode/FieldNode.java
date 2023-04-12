package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 属性节点
 *
 * @author xuchenoak
 **/
@Data
@NoArgsConstructor
public class FieldNode<T> extends BaseNode {

    /**
     * 属性默认值
     */
    private T defaultValue;

    /**
     * 属性值
     */
    private T value;

    /**
     * 值类型节点
     */
    private ClassNode valueTypeClassNode;

    public FieldNode(T value, boolean isArray) {
        this.setArray(isArray);
        this.value = value;
    }

    public FieldNode(T value) {
        this.value = value;
    }

    public void setArray(boolean isArray) {
        if (valueTypeClassNode == null) {
            valueTypeClassNode = new ClassNode();
        }
        valueTypeClassNode.setArray(isArray);
    }

    public boolean isArray() {
        if (valueTypeClassNode == null) {
            return false;
        }
        return valueTypeClassNode.isArray();
    }

    public void setPrimitiveType(boolean isPrimitiveType) {
        if (valueTypeClassNode == null) {
            valueTypeClassNode = new ClassNode();
        }
        valueTypeClassNode.setArray(isPrimitiveType);
    }

    public boolean isPrimitiveType() {
        if (valueTypeClassNode == null) {
            return false;
        }
        return valueTypeClassNode.isPrimitiveType();
    }

}
