package io.gitee.xuchenoak.limejapidocs.parser;


import cn.hutool.core.util.ClassUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.*;
import io.gitee.xuchenoak.limejapidocs.parser.annotaion.ParseIgnore;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.*;
import io.gitee.xuchenoak.limejapidocs.parser.exception.CustomException;
import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.ParseUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * 类解析器
 *
 * @author xuchenoak
 **/
public abstract class ClassParser<T extends ClassNode> {

    private static final Logger logger = LoggerFactory.getLogger(ClassParser.class);

    /**
     * java文件所在包路径（必须到java文件夹） Map<String 绝对路径, String 类全名>
     */
    private static Map<String, String> rootPathMap = new HashMap<>();

    public static void addRootPaths(Set<String> rootPaths) {
        if (rootPaths == null || rootPaths.size() == 0) {
            return;
        }
        for (String rootPath : rootPaths) {
            if (!checkRootPath(rootPath)) {
                continue;
            }
            rootPathMap.put(rootPath, null);
        }
    }

    public static void addRootPath(String rootPath) {
        if (!checkRootPath(rootPath)) {
            return;
        }
        rootPathMap.put(rootPath, null);
    }

    public static boolean checkRootPath(String rootPath) {
        if (StringUtil.isNotBlank(rootPath) && rootPath.endsWith("java")) {
            return true;
        }
        logger.info("rootPath：{}不符合要求，请保持路径末尾为“java”，无需加“/”或“\\”", rootPath);
        return false;
    }

    /**
     * 类节点
     */
    private T classNode;

    /**
     * 解析的文件
     */
    private File javaFile;

    /**
     * 泛型映射集 Map<String 泛型字符, ClassNode 映射类节点>
     */
    private Map<String, ClassNode> fromGenericityNodeMap = new HashMap<>();

    /**
     * 泛型来源集
     */
    private List<ClassNode> fromGenericityNodeList;

    /**
     * 父级节点名称集 Map<父级类名，字段名> 用于标注类的无限嵌套
     */
    private Map<String, String> parentNodeNameMap;

    public ClassParser() {
        this.classNode = (T) new ClassNode();
    }

    public ClassParser(T classNode) {
        this.classNode = classNode;
    }

    public File getJavaFile() {
        return javaFile;
    }

    /**
     * 解析方法
     *
     * @param javaFile java文件
     * @return 解析类节点
     */
    public T parse(File javaFile) {
        return parse(javaFile, null);
    }

    /**
     * 解析方法
     *
     * @param javaFile               java文件
     * @param fromGenericityNodeList 泛型来源（解析类上的泛型时替换为真实传入的类型）
     * @param parentNodeNameMap      父级节点名称集 Map<父级类名，字段名> 用于标注类的无限嵌套
     * @param parentFieldName        父级字段名 用于记录标注类嵌套
     */
    private T parse(File javaFile, List<ClassNode> fromGenericityNodeList, Map<String, String> parentNodeNameMap, String parentFieldName) {
        this.fromGenericityNodeList = fromGenericityNodeList;
        if (parentNodeNameMap == null) {
            parentNodeNameMap = new HashMap<>();
        }
        this.parentNodeNameMap = parentNodeNameMap;
        return parse(javaFile, parentFieldName);
    }

    /**
     * 解析方法
     *
     * @param javaFile        java文件
     * @param parentFieldName 父级字段名 用于记录标注类嵌套
     */
    private T parse(File javaFile, String parentFieldName) {
        try {
            if (javaFile == null || !javaFile.exists()) {
                logger.info("传入的javaFile不存在");
                return null;
            }
            CompilationUnit compilationUnit = JavaParser.parse(javaFile);
            if (compilationUnit == null) {
                logger.error("解析javaFile为compilationUnit失败: ".concat(javaFile.getAbsolutePath()));
                return null;
            }
            this.javaFile = javaFile;

            // 获取类名
            String javaFileName = javaFile.getName();
            String className = javaFileName.substring(0, javaFileName.lastIndexOf("."));
            classNode.setName(className);

            // 获取类解析对象
            Optional<ClassOrInterfaceDeclaration> classDocOpt = compilationUnit.getClassByName(className);
            if (!classDocOpt.isPresent()) {
                classDocOpt = compilationUnit.getInterfaceByName(className);
                if (!classDocOpt.isPresent()) {
//                    logger.warn("获取classDoc不存在：{}", javaFile.getPath());
                    return null;
                }
            }
            ClassOrInterfaceDeclaration classDoc = classDocOpt.get();

            // 开始解析
            handleParseClassDocBefore(classNode, classDoc);
            parseClassDoc(classDoc, parentFieldName);
            handleParseClassDocAfter(classNode, classDoc);

            return this.classNode;
        } catch (CustomException e) {
//            logger.info(e.getMsg());
            return null;
        } catch (Exception e) {
            logger.error("java文件解析异常", e);
            return null;
        }
    }

    /**
     * 解析类文档之前触发
     *
     * @param classNode 类节点
     * @param classDoc  类文档
     */
    protected void handleParseClassDocBefore(T classNode, ClassOrInterfaceDeclaration classDoc) {
    }

    /**
     * 解析类文档之后触发
     *
     * @param classNode 类节点
     * @param classDoc  类文档
     */
    protected void handleParseClassDocAfter(T classNode, ClassOrInterfaceDeclaration classDoc) {
    }


    /**
     * 解析类或接口文档
     *
     * @param classDoc 类或接口文档
     */
    private void parseClassDoc(ClassOrInterfaceDeclaration classDoc, String parentFieldName) {

        if (classDoc.getAnnotationByClass(ParseIgnore.class).isPresent()) {
            classNode.setIgnore(true);
        }

        // 获取修饰符
        classDoc.getModifiers().forEach(modifier -> classNode.addModifier(modifier.asString()));

        // 解析获取类全名和java文件所在包路径
        classDoc.getParentNode().get().findFirst(PackageDeclaration.class).ifPresent(packageDeclaration -> {
            String packageName = packageDeclaration.getNameAsString();
            if (StringUtil.isNotBlank(packageName)) {
                String fullName = packageName.concat(".").concat(classNode.getName());
                classNode.setPackageName(packageName);
                classNode.setFullName(fullName);
                // 记录当前类和对应字段名称
                if (parentNodeNameMap != null) {
                    parentNodeNameMap.put(fullName, parentFieldName);
                }
                String filePackagePath = javaFile.getAbsolutePath().replace(ParseUtil.fullNameToRelativePath(fullName), "");
                classNode.setFilePackagePath(filePackagePath);
            }
        });

        // 解析类导包
        List<ImportDeclaration> importDeclarations = classDoc.getParentNode().get().findAll(ImportDeclaration.class);
        if (ListUtil.isNotBlank(importDeclarations)) {
            importDeclarations.forEach(importDeclaration -> {
                String fullName = importDeclaration.getName().asString();
                String tokenRange = importDeclaration.getTokenRange().get().toString();
                tokenRange = tokenRange.substring(0, tokenRange.lastIndexOf(";")).trim();
                if (tokenRange.contains("*")) {
                    fullName = fullName.concat(".*");
                }
                String className = null;
                if (StringUtil.isNotBlank(fullName) && fullName.lastIndexOf(".") > 0) {
                    className = fullName.substring(fullName.lastIndexOf(".") + 1);
                }
                classNode.addImportNode(new ImportNode(className, fullName));
            });
        }


        // 解析获取类注释标签集 author和description优先用tag里的
        ParseUtil.parseJavaDoc(classDoc.getJavadoc())
                .forEach(tagNode -> classNode.addTagNode(tagNode));

        // 解析获取类注解
        ParseUtil.parseAnnotation(classDoc.getAnnotations())
                .forEach(annotationNode -> classNode.addAnnotationNode(annotationNode));

        // 解析类泛型映射
        parseMapGenericity(classDoc.getTypeParameters());

        // 解析获取类属性
        parseField(classDoc.getFields());

        // 解析获取类方法
        parseMethod(classDoc.getMethods());

        // 解析获取类继承
        if (classDoc.getExtendedTypes().isNonEmpty()) {
            classNode.setExtendsNode(parseClassByType(classDoc.getExtendedTypes(0), classNode));
        }

        // 解析获取类实现接口
        if (classDoc.getImplementedTypes().isNonEmpty()) {
            for (ClassOrInterfaceType implementedType : classDoc.getImplementedTypes()) {
                classNode.addImplementsNode(parseClassByType(implementedType, classNode));
            }
        }

        // 最后构造类节点
        classNode.lastBuild();
    }

    /**
     * 解析类泛型映射
     *
     * @param typeParameterNodeList 待解析类泛型集
     */
    private void parseMapGenericity(NodeList<TypeParameter> typeParameterNodeList) {
        if (ListUtil.isBlank(typeParameterNodeList)) {
            return;
        }
        int i = 0;
        for (TypeParameter typeParameter : typeParameterNodeList) {
            ClassNode tpClassNode;
            String tpName = typeParameter.getNameAsString();
            try {
                tpClassNode = fromGenericityNodeList.get(i);
            } catch (Exception e) {
                tpClassNode = new ClassNode();
                tpClassNode.setName(Object.class.getSimpleName());
                tpClassNode.setFullName(Object.class.getName());
            }
            fromGenericityNodeMap.put(tpName, tpClassNode);
            i++;
        }
    }

    /**
     * 解析方法
     *
     * @param methodDeclarationList 待解析方法集
     */
    private void parseMethod(List<MethodDeclaration> methodDeclarationList) {
        if (ListUtil.isBlank(methodDeclarationList)) {
            return;
        }
        methodDeclarationList.forEach(methodDeclaration -> {
            MethodNode methodNode = new MethodNode();
            if (methodDeclaration.getAnnotationByClass(ParseIgnore.class).isPresent()) {
                methodNode.setIgnore(true);
            }
            methodNode.setName(methodDeclaration.getNameAsString());
            // 获取修饰符
            methodDeclaration.getModifiers().forEach(modifier -> methodNode.addModifier(modifier.asString()));
            // 解析方法的注释
            ParseUtil.parseJavaDoc(methodDeclaration.getJavadoc())
                    .forEach(tagNode -> methodNode.addTagNode(tagNode));
            // 解析方法的注解
            ParseUtil.parseAnnotation(methodDeclaration.getAnnotations())
                    .forEach(annotationNode -> methodNode.addAnnotationNode(annotationNode));
            // 解析方法参数
            methodDeclaration.getParameters().forEach(parameter -> {
                ParamNode paramNode = new ParamNode();
                paramNode.setName(parameter.getNameAsString());
                TagNode tagNode = methodNode.getTagNodeByKey(parameter.getNameAsString());
                if (tagNode != null) {
                    paramNode.setComment(tagNode.getTagValue());
                }
                ParseUtil.parseAnnotation(parameter.getAnnotations())
                        .forEach(paramAnnotationNode -> paramNode.addAnnotationNode(paramAnnotationNode));
                paramNode.setParamType(parseClassByType(parameter.getType(), paramNode));
                methodNode.addParamNode(paramNode);
            });
            // 解析方法返回类型
            methodNode.setReturnNode(parseClassByType(methodDeclaration.getType(), methodNode));
            methodNode.lastBuild();
            classNode.addMethodNode(methodNode);
        });
    }

    /**
     * 是否在父级节点
     *
     * @param fullName 类全名
     * @return
     */
    private boolean isParentNode(String fullName) {
        if (parentNodeNameMap == null) {
            return false;
        }
        return parentNodeNameMap.keySet().contains(fullName);
    }

    /**
     * 解析属性
     *
     * @param fieldDeclarationList 待解析属性集
     */
    private void parseField(List<FieldDeclaration> fieldDeclarationList) {
        if (ListUtil.isBlank(fieldDeclarationList)) {
            return;
        }
        fieldDeclarationList.forEach(fieldDeclaration -> {
            FieldNode fieldNode = new FieldNode();
            if (fieldDeclaration.getAnnotationByClass(ParseIgnore.class).isPresent()) {
                fieldNode.setIgnore(true);
            }
            // 获取修饰符
            fieldDeclaration.getModifiers().forEach(modifier -> fieldNode.addModifier(modifier.asString()));
            // 获取属性
            fieldDeclaration.getVariables().ifNonEmpty(variableDeclarators -> variableDeclarators.forEach(variableDeclarator -> {
                String fieldName = variableDeclarator.getName().asString();
                fieldNode.setName(fieldName);
                fieldNode.setValueTypeClassNode(parseClassByType(variableDeclarator.getType(), fieldNode));
                return;
            }));
            ParseUtil.parseAnnotation(fieldDeclaration.getAnnotations())
                    .forEach(annotationNode -> fieldNode.addAnnotationNode(annotationNode));
            ParseUtil.parseJavaDoc(fieldDeclaration.getJavadoc())
                    .forEach(tagNode -> fieldNode.addTagNode(tagNode));
            // 最后构造属性节点
            fieldNode.lastBuild();
            classNode.addFieldNode(fieldNode);
        });
    }

    /**
     * 根据类型解析为类节点
     *
     * @param baseNode 父级节点 用于记录标注类嵌套
     * @param: type 类型
     */
    private ClassNode parseClassByType(Type type, BaseNode baseNode) {
        ClassNode classNode = new ClassNode();
        classNode.setName(Object.class.getSimpleName());
        classNode.setFullName(Object.class.getName());
        // 基本数据类型
        if (type.isPrimitiveType()) {
            PrimitiveType primitiveType = type.asPrimitiveType();
            classNode.setPrimitiveType(true);
            Class clazz = ParseUtil.getCommonType(primitiveType.asString());
            if (clazz != null) {
                classNode.setName(clazz.getSimpleName());
                classNode.setFullName(clazz.getName());
            } else {
                classNode.setName(primitiveType.asString());
                classNode.setFullName(primitiveType.asString());
            }
            return classNode;
        }
        // 数组类型
        if (type.isArrayType()) {
            classNode.setArray(true);
            classNode.setName(List.class.getSimpleName());
            classNode.setFullName(List.class.getName());
            ArrayType arrayType = type.asArrayType();
            ClassNode _classNode = parseClassByType(arrayType.getComponentType(), baseNode);
            if (_classNode == null) {
                classNode.addGenericityNode(new GenericityNode(
                        Object.class.getSimpleName(),
                        Object.class.getName()
                ));
            } else {
                classNode.addGenericityNode(_classNode);
            }
            return classNode;
        }
        // 类或接口
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classOrInterfaceType = type.asClassOrInterfaceType();
            String className = classOrInterfaceType.asString();
            int dotIndex = className.lastIndexOf(".");
            // 直接为类全名
            if (dotIndex > -1) {
                ClassNode _classNode = parseClassByFullName(classOrInterfaceType, className, baseNode);
                if (_classNode != null) {
                    return _classNode;
                }
            }
            // 仅为类名则获取类全名
            else {
                // 去除泛型
                className = className.replaceAll("<.*>", "");

                // 所有同名不同包的类（这里可能会解析到非该类，同名不同包最好标注在使用时）
                List<ImportNode> importNodes = this.classNode.getImportNodeByClassNameContainsAsterisk(className);
                if (ListUtil.isNotBlank(importNodes)) {
                    for (ImportNode importNode : importNodes) {
                        ClassNode _classNode = parseClassByFullName(classOrInterfaceType, importNode.getFullName(), baseNode);
                        if (_classNode != null) {
                            return _classNode;
                        }
                    }
                }

                // 可能是基本数据类型
                Class clazz = ParseUtil.getCommonType(className.toLowerCase());
                if (clazz != null) {
                    classNode.setName(clazz.getSimpleName());
                    classNode.setFullName(clazz.getName());
                    classNode.setPrimitiveType(true);
                    return classNode;
                }

                // 可能是java.lang
                String fullName = ParseUtil.JAVA_PACKAGE_LANG.concat(className);
                ClassNode _classNode = parseClassByFullName(classOrInterfaceType, fullName, baseNode);
                if (_classNode != null) {
                    return _classNode;
                }

                // 可能是同包
                fullName = this.classNode.getFullNameFromPackageName(className);
                if (StringUtil.isNotBlank(fullName)) {
                    _classNode = parseClassByFullName(classOrInterfaceType, fullName, baseNode);
                    if (_classNode != null) {
                        return _classNode;
                    }
                }

                // 可能是来源泛型
                _classNode = fromGenericityNodeMap.get(className);
                if (_classNode != null) {
                    return _classNode;
                }

                // 其它类只取类名（类全名仍然用java.lang.Object）
                classNode.setName(classOrInterfaceType.asString());
            }
        }
        return classNode;
    }

    /**
     * 根据类全名解析为类节点
     *
     * @param classOrInterfaceType 类或接口类型
     * @param fullName             类全名
     * @param baseNode             父级节点 用于记录标注类嵌套
     */
    private ClassNode parseClassByFullName(ClassOrInterfaceType classOrInterfaceType, String fullName, BaseNode baseNode) {
        ClassNode classNode = new ClassNode();
        if (isParentNode(fullName) && baseNode != null) {
            String nestComment = parentNodeNameMap.get(fullName);
            if (StringUtil.isBlank(nestComment)) {
                nestComment = "（结构同根节点对象）";
            }
            baseNode.setNestComment(nestComment);
            classNode.setName(Object.class.getSimpleName());
            classNode.setFullName(Object.class.getName());
            return classNode;
        }
        // 若存在泛型则处理泛型集
        if (classOrInterfaceType.getTypeArguments().isPresent()) {
            NodeList<Type> argTypes = classOrInterfaceType.getTypeArguments().get();
            for (Type argType : argTypes) {
                if (argType != null) {
                    classNode.addGenericityNode(parseClassByType(argType, baseNode));
                } else {
                    classNode.addGenericityNode(new GenericityNode(
                            Object.class.getSimpleName(),
                            Object.class.getName()
                    ));
                }
            }
        }
        // 基本数据类型及其包装类（包含BigDecimal/BigInteger）
        Class clazz = ParseUtil.getCommonType(fullName);
        if (clazz != null) {
            classNode.setName(clazz.getSimpleName());
            classNode.setFullName(clazz.getName());
            classNode.setPrimitiveType(true);
            return classNode;
        }
        // Java环境内部包
        else if (fullName.startsWith(ParseUtil.JAVA_PACKAGE_PREFIX)) {
            // 去除泛型
            fullName = fullName.replaceAll("<.*>", "");
            try {
                clazz = ClassUtil.loadClass(fullName);
            } catch (Exception e) {
//                logger.info("读取类异常: ".concat(fullName));
            }
            if (clazz != null) {
                if (Collection.class.isAssignableFrom(clazz)) {
                    classNode.setArray(true);
                }
                classNode.setName(clazz.getSimpleName());
                classNode.setFullName(clazz.getName());
                return classNode;
            }
        }
        // 未知的类
        else {
            try {
                String relativePath = ParseUtil.fullNameToRelativePath(fullName);
                String javaFilePath = this.classNode.getFilePackagePath().concat(relativePath);
                File javaFile = new File(javaFilePath);
                if (javaFile == null || !javaFile.exists()) {
                    for (String rootPath : rootPathMap.keySet()) {
                        javaFilePath = rootPath.concat(relativePath);
                        javaFile = new File(javaFilePath);
                        if (javaFile != null && javaFile.exists()) {
                            break;
                        }
                    }
                }
                if (javaFile != null && javaFile.exists()) {
                    ClassParser<ClassNode> classParser = new ClassParser<ClassNode>() {
                    };
                    return classParser.parse(javaFile, classNode.getGenericityNodeList(), parentNodeNameMap, baseNode == null ? null : baseNode.getName());
                }
            } catch (Exception e) {
//                logger.info("读取类异常：{}", fullName);
                return null;
            }
        }
        return null;
    }


}
