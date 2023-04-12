package io.gitee.xuchenoak.limejapidocs.parser.parsendoe;

import io.gitee.xuchenoak.limejapidocs.parser.basenode.ClassNode;
import io.gitee.xuchenoak.limejapidocs.parser.bean.ControllerData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 控制器节点
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ControllerNode extends ClassNode {

    /**
     * 解析出的接口数据
     */
    private ControllerData controllerData;

    /**
     * 请求前缀
     */
    private List<String> baseUriList;

    /**
     * 接口方法集
     */
    private List<InterfaceMethodNode> interfaceMethodNodeList;

    public void addBaseUri(String baseUri) {
        if (baseUriList == null) {
            baseUriList = new ArrayList<>();
        }
        baseUriList.add(baseUri);
    }

    public void addInterfaceMethodNode(InterfaceMethodNode interfaceMethodNode) {
        if (interfaceMethodNodeList == null) {
            interfaceMethodNodeList = new ArrayList<>();
        }
        interfaceMethodNodeList.add(interfaceMethodNode);
    }

    public ControllerData getControllerData() {
        return controllerData;
    }

    public void setControllerData(ControllerData controllerData) {
        this.controllerData = controllerData;
    }
}
