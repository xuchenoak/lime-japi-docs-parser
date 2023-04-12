package io.gitee.xuchenoak.limejapidocs.parser.parsendoe;

import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段数据
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldInfo {

    /**
     * 参数描述
     */
    private String comment;

    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数类型
     */
    private String type;

    /**
     * 参数验证说明
     */
    private String validation;

    /**
     * 参数默认值
     */
    private String defaultValue;

    /**
     * 参数值节点
     */
    private FieldDataNode valueFieldData = new FieldDataNode();

    public FieldInfo(String comment, String name, String type, String validation) {
        this.comment = comment;
        this.name = name;
        this.type = type;
        this.validation = validation;
    }

    public boolean ok() {
        if (StringUtil.isBlank(name)) {
            return false;
        }
        if (StringUtil.isBlank(type)) {
            return false;
        }
        return true;
    }

    public FieldInfo toLastValue() {
        this.valueFieldData.setLastValue(true);
        return this;
    }

}
