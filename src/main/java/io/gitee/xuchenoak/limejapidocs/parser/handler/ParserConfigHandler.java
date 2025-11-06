package io.gitee.xuchenoak.limejapidocs.parser.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.AnnotationNode;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.FieldNode;
import io.gitee.xuchenoak.limejapidocs.parser.bean.ControllerData;
import io.gitee.xuchenoak.limejapidocs.parser.config.ParserConfig;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.ControllerNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldInfo;
import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;

import java.util.Date;
import java.util.List;

/**
 * 解析配置控制接口
 *
 * @author xuchenoak
 */
public interface ParserConfigHandler {

    /**
     * 获取解析全局配置
     *
     * @return 解析配置对象
     */
    ParserConfig getParserConfig();

    /**
     * 获取解析时间
     *
     * @return 解析时间对象
     */
    default Date getParseTime() {
        return new Date();
    }

    /**
     * Controller类解析节点数据处理
     *
     * @param controllerNode Controller类解析节点
     */
    default void controllerNodeHandle(ControllerNode controllerNode) {
    }

    /**
     * Controller类及其接口方法数据处理
     *
     * @param controllerData Controller及其接口方法数据
     */
    default void controllerDataHandle(ControllerData controllerData) {
    }

    /**
     * 参数验证注入处理
     *
     * @param annotationNodeList 参数注解节点列表
     * @param fieldInfo          参数信息
     */
    default void paramValidInjectHandle(List<AnnotationNode> annotationNodeList, FieldInfo fieldInfo) {
        if (ListUtil.isBlank(annotationNodeList) || fieldInfo == null) {
            return;
        }
        for (AnnotationNode annotationNode : annotationNodeList) {
            switch (annotationNode.getName()) {
                case "NotNull":
                    fieldInfo.setValidation("对象非空");
                    break;
                case "NotBlank":
                    fieldInfo.setValidation("字符串非空");
                    break;
                case "Size":
                    FieldNode<String> fieldNode = annotationNode.getFieldNodeByName("message");
                    if (fieldNode != null) {
                        fieldInfo.setValidation(fieldNode.getValue());
                    }
                    break;
            }
        }
    }

    /**
     * 默认值注入处理
     *
     * @param annotationNodeList 参数注解节点列表
     * @param fieldInfo          参数信息
     */
    default void paramDefaultValueInjectHandle(List<AnnotationNode> annotationNodeList, FieldInfo fieldInfo) {
        if (fieldInfo == null) {
            return;
        }
        String defaultValue = null;
        switch (fieldInfo.getType()) {
            case "String":
                defaultValue = StringUtil.getRandomString(5, null, fieldInfo.getName(), true);
                break;
            case "Integer":
            case "BigInteger":
                defaultValue = String.valueOf(RandomUtil.randomInt(2));
                break;
            case "Long":
                defaultValue = String.valueOf(RandomUtil.randomLong());
                break;
            case "Double":
            case "Float":
            case "BigDecimal":
                defaultValue = String.valueOf(Math.random());
                break;
            case "Date":
                String format = "yyyy-MM-dd HH:mm:ss";
                if (ListUtil.isNotBlank(annotationNodeList)) {
                    for (AnnotationNode annotationNode : annotationNodeList) {
                        if ("DateTimeFormat".equals(annotationNode.getName()) || "JsonFormat".equals(annotationNode.getName())) {
                            FieldNode<String> fieldNode = annotationNode.getFieldNodeByName("pattern");
                            if (fieldNode != null && StringUtil.isNotBlank(fieldNode.getValue())) {
                                format = fieldNode.getValue();
                            }
                            break;
                        }
                    }
                }
                try {
                    defaultValue = DateUtil.date().toString(format);
                } catch (Exception e) {
                    defaultValue = DateUtil.date().toString("yyyy-MM-dd HH:mm:ss");
                }
                break;
        }
        fieldInfo.setDefaultValue(defaultValue);
    }

    /**
     * 解析完成处理
     *
     * @param controllerDataList Controller及其接口方法数据集
     */
    void parseFinishedHandle(List<ControllerData> controllerDataList);

    /**
     * 是否解析Controller类的直接父类接口方法
     * 1.这里做一个配置是因为解析父类不好判断接口方法重写，故默认关闭
     * 2.如果开启，会解析直接父类接口方法，如果有重写方法则通过“方法名_参数数量”来判断是否同一个方法，所以打开后尽量避免方法重载，否则可能会出现方法错乱问题
     * @return 是否解析直接父类接口方法
     */
    default boolean isParseControllerFirstParent() {
        return false;
    }

}
