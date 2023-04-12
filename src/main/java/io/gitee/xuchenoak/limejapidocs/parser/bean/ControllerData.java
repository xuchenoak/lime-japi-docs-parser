package io.gitee.xuchenoak.limejapidocs.parser.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * controller数据
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ControllerData {

    /**
     * controller唯一标识
     */
    private String controllerId;

    /**
     * controller类全名
     */
    private String controllerFullName;

    /**
     * controller名称注释
     */
    private String comment;

    /**
     * 请求前缀
     */
    private List<String> baseUriList;

    /**
     * 接口方法集
     */
    private List<InterfaceData> interfaceDataList;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 生成时间
     */
    private Date createTime;

    public ControllerData(String controllerId, String controllerFullName, String comment, List<String> baseUriList, List<InterfaceData> interfaceDataList) {
        this.controllerId = controllerId;
        this.controllerFullName = controllerFullName;
        this.comment = comment;
        this.baseUriList = baseUriList;
        this.interfaceDataList = interfaceDataList;
    }
}
