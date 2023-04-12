package io.gitee.xuchenoak.limejapidocs.parser.parsendoe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 字段数据节点
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldDataNode {

    /**
     * 是否为数组
     */
    private boolean array = Boolean.FALSE;

    /**
     * 是否为最终类型（不可再分）
     */
    private boolean lastValue = Boolean.FALSE;

    /**
     * 最终类型
     */
    private String lastValueType;

    /**
     * 自定义类型字段具体信息
     */
    private List<FieldInfo> fieldInfoList;

    /**
     * 多维字段数据（数组）
     */
    private FieldDataNode childFieldData;


}
