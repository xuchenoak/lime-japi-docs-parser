package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 类节点
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassNode extends BaseNode {

    /**
     * java文件所在包路径
     */
    private String filePackagePath;

    /**
     * 包名
     */
    private String packageName;

    /**
     * 是否为数组（数组类型会转化为List返回）
     */
    private boolean array = Boolean.FALSE;

    /**
     * 是否基本数据类型（包括BigDecimal和BigInteger, 基本数据类型会转为包装类返回）
     */
    private boolean primitiveType = Boolean.FALSE;

    /**
     * 导包集
     */
    private List<ImportNode> importNodeList;

    /**
     * 属性集
     */
    private List<FieldNode> fieldNodeList;

    /**
     * 方法集
     */
    private List<MethodNode> methodNodeList;

    /**
     * 泛型集
     */
    private List<ClassNode> genericityNodeList;

    /**
     * 继承类
     */
    private ClassNode extendsNode;

    /**
     * 实现接口集
     */
    private List<ClassNode> implementsNodeList;

    /**
     * 获取本类及父级属性节点（无序）
     *
     * @return 属性及其继承节点属性集
     */
    public List<FieldNode> getFieldNodeListAndExtends() {
        List<FieldNode> fieldNodes = new ArrayList<>();
        if (extendsNode != null) {
            List<FieldNode> extendsFieldNodes = extendsNode.getFieldNodeListAndExtends();
            if (ListUtil.isNotBlank(extendsFieldNodes)) {
                fieldNodes.addAll(extendsFieldNodes);
            }
        }
        if (ListUtil.isNotBlank(fieldNodeList)) {
            fieldNodes.addAll(fieldNodeList);
        }
        return fieldNodes.stream().collect(Collectors.toMap(FieldNode::getName, Function.identity(), (k1, k2) -> k1)).values().stream().collect(Collectors.toList());
    }

    /**
     * 注入本类及父级属性节点（有序）
     *
     * @param fieldNodes 注入本类及父级属性节点集
     */
    public void injectFieldNodeListAndExtends(List<FieldNode> fieldNodes) {
        if (ListUtil.isNotBlank(fieldNodeList)) {
            for (FieldNode fieldNode : fieldNodeList) {
                if (fieldNodes.stream().filter(f -> f.getName().equals(fieldNode.getName())).count() < 1) {
                    fieldNodes.add(fieldNode);
                }
            }
        }
        if (extendsNode != null) {
            extendsNode.injectFieldNodeListAndExtends(fieldNodes);
        }
    }

    @Override
    public void setFullName(String fullName) {
        super.setFullName(fullName);
        if (StringUtil.isNotBlank(fullName)) {
            this.packageName = fullName.substring(0, fullName.lastIndexOf("."));
        }
    }

    public String getFullNameFromPackageName(String className) {
        if (StringUtil.isBlank(packageName)) {
            return null;
        }
        return packageName.concat(".").concat(className);
    }

    public void addImportNode(ImportNode importNode) {
        if (importNodeList == null) {
            importNodeList = new ArrayList<>();
        }
        importNodeList.add(importNode);
    }

    /**
     * 获取同类名包节点包含通配*
     *
     * @param className 类名
     * @return 类导包节点集
     */
    public List<ImportNode> getImportNodeByClassNameContainsAsterisk(String className) {
        List<ImportNode> resList = getImportNodeByClassName(className);
        resList.addAll(getAsteriskImportNodeByClassName(className));
        return resList;
    }

    /**
     * 获取同类名包节点
     *
     * @param className 类名
     * @return 类导包节点集
     */
    public List<ImportNode> getImportNodeByClassName(String className) {
        List<ImportNode> resList = new ArrayList<>();
        if (ListUtil.isNotBlank(importNodeList) && StringUtil.isNotBlank(className)) {
            for (ImportNode importNode : importNodeList) {
                if (className.equals(importNode.getClassName())) {
                    resList.add(importNode);
                }
            }
        }
        return resList;
    }

    /**
     * 获取通配符替换后的包节点
     *
     * @param className 类名
     * @return 类导包节点集
     */
    public List<ImportNode> getAsteriskImportNodeByClassName(String className) {
        List<ImportNode> resList = new ArrayList<>();
        if (ListUtil.isNotBlank(importNodeList) && StringUtil.isNotBlank(className)) {
            for (ImportNode importNode : importNodeList) {
                if ("*".equals(importNode.getClassName())) {
                    String fullName = importNode.getFullName().replace("*", className);
                    resList.add(new ImportNode(className, fullName));
                }
            }
        }
        return resList;
    }


    public ImportNode getOneImportNodeByFullName(String fullName) {
        if (ListUtil.isNotBlank(importNodeList) && StringUtil.isNotBlank(fullName)) {
            for (ImportNode importNode : importNodeList) {
                if (fullName.equals(importNode.getFullName())) {
                    return importNode;
                }
            }
        }
        return null;
    }

    public void addFieldNode(FieldNode fieldNode) {
        if (fieldNodeList == null) {
            fieldNodeList = new ArrayList<>();
        }
        fieldNodeList.add(fieldNode);
    }

    public FieldNode getFieldNodeByName(String name) {
        if (ListUtil.isNotBlank(fieldNodeList) && StringUtil.isNotBlank(name)) {
            for (FieldNode fieldNode : fieldNodeList) {
                if (name.equals(fieldNode.getName())) {
                    return fieldNode;
                }
            }
        }
        return null;
    }

    public void addMethodNode(MethodNode methodNode) {
        if (methodNodeList == null) {
            methodNodeList = new ArrayList<>();
        }
        methodNodeList.add(methodNode);
    }

    public MethodNode getMethodNodeByName(String name) {
        if (ListUtil.isNotBlank(methodNodeList) && StringUtil.isNotBlank(name)) {
            for (MethodNode methodNode : methodNodeList) {
                if (name.equals(methodNode.getName())) {
                    return methodNode;
                }
            }
        }
        return null;
    }

    public void addGenericityNode(ClassNode genericityNode) {
        if (genericityNodeList == null) {
            genericityNodeList = new ArrayList<>();
        }
        genericityNodeList.add(genericityNode);
    }

    public ClassNode getGenericityNodeByName(String name) {
        if (ListUtil.isNotBlank(genericityNodeList) && StringUtil.isNotBlank(name)) {
            for (ClassNode genericityNode : genericityNodeList) {
                if (name.equals(genericityNode.getName())) {
                    return genericityNode;
                }
            }
        }
        return null;
    }

    public void addImplementsNode(ClassNode classNode) {
        if (implementsNodeList == null) {
            implementsNodeList = new ArrayList<>();
        }
        implementsNodeList.add(classNode);
    }


}
