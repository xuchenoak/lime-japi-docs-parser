package io.gitee.xuchenoak.limejapidocs.parser;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.*;
import io.gitee.xuchenoak.limejapidocs.parser.bean.ControllerData;
import io.gitee.xuchenoak.limejapidocs.parser.bean.InterfaceData;
import io.gitee.xuchenoak.limejapidocs.parser.config.ParserConfig;
import io.gitee.xuchenoak.limejapidocs.parser.constant.InterfaceMethodType;
import io.gitee.xuchenoak.limejapidocs.parser.constant.InterfaceRequestContentType;
import io.gitee.xuchenoak.limejapidocs.parser.exception.CustomException;
import io.gitee.xuchenoak.limejapidocs.parser.handler.ParserConfigHandler;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.ControllerNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldDataNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldInfo;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.InterfaceMethodNode;
import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.ParseUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 控制器解析器
 *
 * @author xuchenoak
 **/
public class ControllerParser extends ClassParser<ControllerNode> {

    /**
     * 配置控制对象
     */
    private ParserConfigHandler parserConfigHandler;

    /**
     * 是否为自定义最终类型
     *
     * @param fullName 类全名
     * @return 是否为最终数据类型
     */
    public boolean isDiyLastValueType(String fullName) {
        if (StringUtil.isBlank(fullName)) {
            return false;
        }
        if (String.class.getName().equals(fullName)) {
            return true;
        }
        if (Date.class.getName().equals(fullName)) {
            return true;
        }
        if (parserConfigHandler.getParserConfig() == null || parserConfigHandler.getParserConfig().getLastValueTypeFullName() == null) {
            return false;
        }
        return parserConfigHandler.getParserConfig().getLastValueTypeFullName().contains(fullName);
    }

    private ControllerParser() {
        super(new ControllerNode());
    }

    public static ControllerParser createParser(ParserConfigHandler parserConfigHandler) {
        return new ControllerParser().handler(parserConfigHandler);
    }

    private ControllerParser handler(ParserConfigHandler parserConfigHandler) {
        if (parserConfigHandler == null) {
            throw new RuntimeException("ParserConfigHandler为空");
        }
        if (parserConfigHandler.getParseTime() == null) {
            throw new RuntimeException("ParseTime为空");
        }
        this.parserConfigHandler = parserConfigHandler;
        return this;
    }

    @Override
    protected void handleParseClassDocBefore(ControllerNode classNode, ClassOrInterfaceDeclaration classDoc) {
        final String className = classNode.getName();
        if (!classDoc.getAnnotationByName("RestController").isPresent()
                && !classDoc.getAnnotationByName("Controller").isPresent()) {
            throw CustomException.instance("{}类非Controller接口类，不再解析", className);
        }
        Set<String> filterControllerNames = parserConfigHandler.getParserConfig().getFilterControllerNames();
        Set<String> ignoreControllerNames = parserConfigHandler.getParserConfig().getIgnoreControllerNames();
        if (ListUtil.isNotBlank(filterControllerNames)) {
            if (filterControllerNames.stream().filter(n -> n.equals(className)).count() < 1) {
                throw CustomException.instance("已配置得仅解析接口类未包含{}类，不再解析", className);
            }
        }
        if (ListUtil.isNotBlank(ignoreControllerNames)) {
            if (ignoreControllerNames.stream().filter(n -> n.equals(className)).count() > 0) {
                throw CustomException.instance("已配置忽略解析接口类包含{}类，不再解析", className);
            }
        }
    }

    @Override
    protected void handleParseClassDocAfter(ControllerNode controllerNode, ClassOrInterfaceDeclaration classDoc) {
        if (StringUtil.isBlank(controllerNode.getComment())) {
            controllerNode.setComment(controllerNode.getName());
        }
        parseBaseUri(controllerNode);
        parseInterfaceMethod(controllerNode);
        constructInterfaceData(controllerNode);
    }

    /**
     * 构造接口解析数据
     *
     * @param controllerNode
     */
    private void constructInterfaceData(ControllerNode controllerNode) {
        if (controllerNode == null) {
            return;
        }
        List<InterfaceMethodNode> interfaceMethodNodeList = controllerNode.getInterfaceMethodNodeList();
        if (ListUtil.isBlank(interfaceMethodNodeList)) {
            throw CustomException.instance("{}类内无接口方法，不再解析", controllerNode.getName());
        }

        long createTimestamp = parserConfigHandler.getParseTime().getTime();
        String javaFileCode = SecureUtil.md5(getJavaFile());
        String controllerId = SecureUtil.md5(StrUtil.format("{}_{}", javaFileCode, createTimestamp));
        List<InterfaceData> interfaceDataList = new ArrayList<>();
        int flag = 1;
        for (InterfaceMethodNode interfaceMethodNode : interfaceMethodNodeList) {
            if (ListUtil.isBlank(interfaceMethodNode.getUriList())) {
                continue;
            }
            interfaceDataList.add(new InterfaceData(
                    SecureUtil.md5(StrUtil.format("{}_{}_{}", controllerId, IdUtil.fastSimpleUUID(), flag)),
                    controllerId,
                    interfaceMethodNode.getName(),
                    interfaceMethodNode.getComment(),
                    interfaceMethodNode.getUriList(),
                    interfaceMethodNode.getRequestTypeList(),
                    interfaceMethodNode.getRequestContentType(),
                    interfaceMethodNode.getFormData(),
                    interfaceMethodNode.getBodyData(),
                    interfaceMethodNode.getResData(),
                    flag++
            ));
        }
        controllerNode.setControllerData(new ControllerData(
                controllerId,
                controllerNode.getFullName(),
                controllerNode.getComment(),
                controllerNode.getBaseUriList(),
                interfaceDataList
        ));
    }

    /**
     * 解析接口方法
     *
     * @param controllerNode 控制层节点对象
     */
    private void parseInterfaceMethod(ControllerNode controllerNode) {
        List<MethodNode> methodNodeList = controllerNode.getMethodNodeList();
        if (ListUtil.isNotBlank(methodNodeList)) {
            for (MethodNode methodNode : methodNodeList) {
                if (methodNode.getIgnore()) {
                    continue;
                }
                InterfaceMethodNode interfaceMethodNode = new InterfaceMethodNode(methodNode);
                // 若为RequestMapping注解则最优先
                AnnotationNode annotationNode = methodNode.getAnnotationNodeByName(InterfaceMethodType.DEFAULT.getAnnotation());
                if (annotationNode != null) {
                    boolean hasUri = false;
                    boolean hasMethod = false;
                    List<FieldNode> fieldNodeList = annotationNode.getFieldNodeList();
                    if (ListUtil.isNotBlank(fieldNodeList)) {
                        for (FieldNode fieldNode : fieldNodeList) {
                            // uri
                            if ("value".equals(fieldNode.getName()) || "path".equals(fieldNode.getName())) {
                                if (fieldNode.isArray()) {
                                    List<String> uriList = (List<String>) fieldNode.getValue();
                                    if (ListUtil.isNotBlank(uriList)) {
                                        for (String uri : uriList) {
                                            hasUri = addRealUri(interfaceMethodNode, controllerNode.getBaseUriList(), uri);
                                        }
                                    }
                                } else {
                                    hasUri = addRealUri(interfaceMethodNode, controllerNode.getBaseUriList(), String.valueOf(fieldNode.getValue()));
                                }
                            }
                            // methodType
                            else if ("method".equals(fieldNode.getName())) {
                                if (fieldNode.isArray()) {
                                    List<String> methodList = (List<String>) fieldNode.getValue();
                                    for (String method : methodList) {
                                        InterfaceMethodType methodType = InterfaceMethodType.getMethodTypeByHttpMethod(method);
                                        if (methodType != null) {
                                            hasMethod = true;
                                            interfaceMethodNode.addRequestType(method);
                                        }
                                    }
                                } else {
                                    InterfaceMethodType methodType = InterfaceMethodType.getMethodTypeByHttpMethod(String.valueOf(fieldNode.getValue()));
                                    if (methodType != null) {
                                        hasMethod = true;
                                        interfaceMethodNode.addRequestType(methodType.getHttpMethod());
                                    }
                                }
                            }
                        }
                    } else {
                        hasUri = addRealUri(interfaceMethodNode, controllerNode.getBaseUriList(), "");
                    }

                    // 未设置方法 默认所有方法
                    if (hasUri && !hasMethod) {
                        for (InterfaceMethodType type : InterfaceMethodType.values()) {
                            if (type.equals(InterfaceMethodType.DEFAULT)) {
                                continue;
                            }
                            interfaceMethodNode.addRequestType(type.getHttpMethod());
                        }
                    }
                    if (hasUri) {
                        parseInterfaceMethodParam(interfaceMethodNode, controllerNode.isValidated());
                        parseInterFaceMethodResData(interfaceMethodNode);
                        controllerNode.addInterfaceMethodNode(interfaceMethodNode);
                    }
                    continue;
                }
                // 另外注解
                for (InterfaceMethodType type : InterfaceMethodType.values()) {
                    annotationNode = methodNode.getAnnotationNodeByName(type.getAnnotation());
                    if (annotationNode != null) {
                        interfaceMethodNode.addRequestType(type.getHttpMethod());
                        break;
                    }
                }
                if (annotationNode != null) {
                    List<FieldNode> fieldNodeList = annotationNode.getFieldNodeList();
                    boolean hasUri = false;
                    if (ListUtil.isNotBlank(fieldNodeList)) {
                        for (FieldNode fieldNode : fieldNodeList) {
                            if ("value".equals(fieldNode.getName()) || "path".equals(fieldNode.getName())) {
                                if (fieldNode.isArray()) {
                                    List<String> uriList = (List<String>) fieldNode.getValue();
                                    if (ListUtil.isNotBlank(uriList)) {
                                        for (String uri : uriList) {
                                            hasUri = addRealUri(interfaceMethodNode, controllerNode.getBaseUriList(), uri);
                                        }
                                    }
                                } else {
                                    hasUri = addRealUri(interfaceMethodNode, controllerNode.getBaseUriList(), String.valueOf(fieldNode.getValue()));
                                }
                            }
                        }
                    } else {
                        hasUri = addRealUri(interfaceMethodNode, controllerNode.getBaseUriList(), "");
                    }
                    if (hasUri) {
                        parseInterfaceMethodParam(interfaceMethodNode, controllerNode.isValidated());
                        parseInterFaceMethodResData(interfaceMethodNode);
                        controllerNode.addInterfaceMethodNode(interfaceMethodNode);
                    }
                }
            }
        }
    }

    /**
     * 解析接口响应对象
     *
     * @param interfaceMethodNode 接口方法节点对象
     */
    private void parseInterFaceMethodResData(InterfaceMethodNode interfaceMethodNode) {
        FieldDataNode fieldDataNode = new FieldDataNode();
        toFieldDataNode(fieldDataNode, interfaceMethodNode.getReturnNode(), false);
        interfaceMethodNode.setResData(fieldDataNode);
    }

    /**
     * 解析接口请求方法参数
     *
     * @param interfaceMethodNode 接口方法节点对象
     * @param isValidation        是否验证参数
     */
    private void parseInterfaceMethodParam(InterfaceMethodNode interfaceMethodNode, boolean isValidation) {
        List<ParamNode> paramNodeList = interfaceMethodNode.getParamNodeList();
        if (ListUtil.isNotBlank(paramNodeList)) {
            // 请求参数字段数据（表单）
            List<FieldInfo> formData = null;
            // 请求参数字段数据（json）
            FieldDataNode bodyData = null;
            for (ParamNode paramNode : paramNodeList) {
                ClassNode paramType = paramNode.getParamType();
                // JSON参数
                if (paramNode.getAnnotationNodeByName("RequestBody") != null) {
                    if (bodyData != null) {
                        continue;
                    }
                    bodyData = new FieldDataNode();
                    toFieldDataNode(bodyData, paramType, paramNode.isValidated() || paramNode.isValid());
                }
                // 非JSON参数
                else {
                    if (formData == null) {
                        formData = new ArrayList<>();
                    }
                    // 数组类型参数先不处理
                    if (paramType.isArray()) {
                        continue;
                    }
                    // 基础类型直接记录
                    if (paramType.isPrimitiveType() || isDiyLastValueType(paramType.getFullName()) || (paramType.getFullName() != null && paramType.getFullName().equals(Object.class.getName()))) {
                        FieldInfo fieldInfo = new FieldInfo(
                                paramNode.getComment(),
                                paramNode.getName(),
                                paramType.getName(),
                                paramNode.getValidation()
                        ).toLastValue();
                        if (isValidation) {
                            parserConfigHandler.paramValidInjectHandle(paramNode.getAnnotationNodeList(), fieldInfo);
                        }
                        parserConfigHandler.paramDefaultValueInjectHandle(paramNode.getAnnotationNodeList(), fieldInfo);
                        formData.add(fieldInfo);
                        continue;
                    }
                    // 自定义对象
//                    List<FieldNode> fieldNodeList = paramType.getFieldNodeListAndExtends();
                    List<FieldNode> fieldNodeList = new ArrayList<>();
                    paramType.injectFieldNodeListAndExtends(fieldNodeList);
                    if (ListUtil.isBlank(fieldNodeList)) {
                        continue;
                    }
                    for (FieldNode fieldNode : fieldNodeList) {
                        ClassNode typeClassNode = fieldNode.getValueTypeClassNode();
                        FieldInfo fieldInfo = new FieldInfo(
                                fieldNode.getComment(),
                                fieldNode.getName(),
                                typeClassNode.getName(),
                                fieldNode.getValidation()
                        );
                        if (paramNode.isValidated() || paramNode.isValid()) {
                            parserConfigHandler.paramValidInjectHandle(typeClassNode.getAnnotationNodeList(), fieldInfo);
                        }
                        formData.add(fieldInfo);
                        if (fieldNode.isPrimitiveType() || isDiyLastValueType(typeClassNode.getFullName())) {
                            parserConfigHandler.paramDefaultValueInjectHandle(typeClassNode.getAnnotationNodeList(), fieldInfo);
                            fieldInfo.toLastValue();
                        }
                    }
                }
            }
            interfaceMethodNode.setFormData(formData);
            interfaceMethodNode.setBodyData(bodyData);

            // json参数类型
            if (bodyData != null) {
                interfaceMethodNode.setRequestContentType(InterfaceRequestContentType.JSON.getParamType());
            }
            // get请求
            else if (interfaceMethodNode.getRequestTypeList().contains(InterfaceMethodType.GET.getHttpMethod())) {
                interfaceMethodNode.setRequestContentType(InterfaceRequestContentType.FORM_URLENCODED.getParamType());
            }
            // 其它请求默认用form-data
            else {
                interfaceMethodNode.setRequestContentType(InterfaceRequestContentType.FORM_DATA.getParamType());
            }
        }
    }

    /**
     * 添加处理后的uri
     *
     * @param interfaceMethodNode 接口方法节点
     * @param baseUriList         控制器uri前缀集
     * @param uri                 原uri
     */
    private boolean addRealUri(InterfaceMethodNode interfaceMethodNode, List<String> baseUriList, String uri) {
        if (interfaceMethodNode == null) {
            return false;
        }
        if (uri == null) {
            return false;
        }
        uri = delPrefixAndSuffixRod(uri);
        StringBuilder realBuilder = null;
        if (ListUtil.isBlank(baseUriList)) {
            realBuilder = new StringBuilder("/");
            interfaceMethodNode.addUri(realBuilder.append(uri).toString());
            return true;
        }
        for (String baseUri : baseUriList) {
            realBuilder = new StringBuilder("/");
            if (StringUtil.isNotBlank(baseUri)) {
                baseUri = delPrefixAndSuffixRod(baseUri);
            }
            if (StringUtil.isNotBlank(baseUri)) {
                realBuilder.append(baseUri);
            }
            if (StringUtil.isNotBlank(uri)) {
                realBuilder.append("/").append(uri);
            }
            interfaceMethodNode.addUri(realBuilder.toString());
        }
        return true;
    }

    /**
     * 去除首尾斜杆
     *
     * @return
     */
    private String delPrefixAndSuffixRod(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        return uri;
    }

    /**
     * 解析控制器uri前缀
     *
     * @param controllerNode 控制层节点对象
     */
    private void parseBaseUri(ControllerNode controllerNode) {
        AnnotationNode annotationNode = controllerNode.getAnnotationNodeByName("RequestMapping");
        if (annotationNode != null && ListUtil.isNotBlank(annotationNode.getFieldNodeList())) {
            for (FieldNode fieldNode : annotationNode.getFieldNodeList()) {
                if ("value".equals(fieldNode.getName()) || "path".equals(fieldNode.getName())) {
                    if (fieldNode.isArray()) {
                        List<String> uriList = (List<String>) fieldNode.getValue();
                        if (ListUtil.isNotBlank(uriList)) {
                            uriList.forEach(uri -> controllerNode.addBaseUri(uri));
                        }
                    } else {
                        controllerNode.addBaseUri(String.valueOf(fieldNode.getValue()));
                    }
                }
            }
        }
    }

    /**
     * 类型转字段数据
     *
     * @param fieldDataNode 字段数据
     * @param classNode     类型
     * @param isValid       是否验证
     */
    private void toFieldDataNode(FieldDataNode fieldDataNode, ClassNode classNode, boolean isValid) {
        // 数组向里递归
        if (classNode.isArray()) {
            fieldDataNode.setArray(true);
            FieldDataNode child = new FieldDataNode();
            fieldDataNode.setChildFieldData(child);
            List<ClassNode> genericNodeList = classNode.getGenericityNodeList();
            if (ListUtil.isNotBlank(genericNodeList)) {
                toFieldDataNode(child, genericNodeList.get(0), false);
            }
            return;
        }
        // 基础类型
        if (isDiyLastValueType(classNode.getFullName()) || ParseUtil.getCommonType(classNode.getFullName()) != null) {
            fieldDataNode.setLastValue(true);
            fieldDataNode.setLastValueType(classNode.getName());
            return;
        }
        // 对象构造属性
        List<FieldInfo> fieldInfoList = new ArrayList<>();
        fieldDataNode.setFieldInfoList(fieldInfoList);
        List<FieldNode> fieldNodeList = new ArrayList<>();
        classNode.injectFieldNodeListAndExtends(fieldNodeList);
        if (ListUtil.isBlank(fieldNodeList)) {
            return;
        }
        for (FieldNode fieldNode : fieldNodeList) {
            ClassNode typeClassNode = fieldNode.getValueTypeClassNode();
            if (fieldNode.isStatic()) {
                continue;
            }
            FieldInfo fieldInfo = new FieldInfo(
                    fieldNode.getComment(),
                    fieldNode.getName(),
                    typeClassNode.getName(),
                    fieldNode.getValidation()
            );
            if (isValid) {
                parserConfigHandler.paramValidInjectHandle(fieldNode.getAnnotationNodeList(), fieldInfo);
            }
            fieldInfoList.add(fieldInfo);
            // 基本数据类型或配置的基本类型则跳过
            if (fieldNode.isPrimitiveType() || isDiyLastValueType(typeClassNode.getFullName())) {
                parserConfigHandler.paramDefaultValueInjectHandle(fieldNode.getAnnotationNodeList(), fieldInfo);
                fieldInfo.toLastValue();
                continue;
            }
            // 其他类型（数组、自定义对象）继续递归
            toFieldDataNode(fieldInfo.getValueFieldData(), typeClassNode, fieldNode.isValid());
        }
    }
}
