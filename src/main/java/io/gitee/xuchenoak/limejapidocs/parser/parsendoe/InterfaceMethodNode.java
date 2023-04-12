package io.gitee.xuchenoak.limejapidocs.parser.parsendoe;

import io.gitee.xuchenoak.limejapidocs.parser.basenode.MethodNode;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口方法节点
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InterfaceMethodNode extends MethodNode {

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

    public InterfaceMethodNode(MethodNode methodNode) {
        super(methodNode);
        if (StringUtil.isBlank(methodNode.getComment())) {
            this.setComment(methodNode.getName());
        }
    }

    public void addUri(String uri) {
        if (uriList == null) {
            uriList = new ArrayList<>();
        }
        uriList.add(uri);
    }

    public void addRequestType(String requestType) {
        if (requestTypeList == null) {
            requestTypeList = new ArrayList<>();
        }
        requestTypeList.add(requestType);
    }


}
