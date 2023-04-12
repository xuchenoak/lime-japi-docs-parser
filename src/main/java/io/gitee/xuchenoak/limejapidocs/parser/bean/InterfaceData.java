package io.gitee.xuchenoak.limejapidocs.parser.bean;

import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldDataNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 接口数据
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InterfaceData {

    /**
     * 接口唯一标识
     */
    private String interfaceId;

    /**
     * controller唯一标识
     */
    private String controllerId;

    /**
     * 接口方法名
     */
    private String methodName;

    /**
     * 接口名称注释
     */
    private String comment;

    /**
     * 请求地址
     */
    private List<String> uriList;

    /**
     * 请求方式
     */
    private List<String> requestTypeList;

    /**
     * 请求参数类型
     */
    private String requestContentType;

    /**
     * 请求参数字段数据（表单）
     */
    private List<FieldInfo> formData;

    /**
     * 请求参数字段数据（json）
     */
    private FieldDataNode bodyData;

    /**
     * 响应信息字段数据
     */
    private FieldDataNode resData;

    /**
     * 排序
     */
    private Integer sort;

}
