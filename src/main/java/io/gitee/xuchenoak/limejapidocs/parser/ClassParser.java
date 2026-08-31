package io.gitee.xuchenoak.limejapidocs.parser;


import cn.hutool.core.util.ClassUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.nodeTypes.NodeWithMembers;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
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
     * 解析器实例（固定 JAVA_25 语言级别）。
     * 采用线程局部共享：同一线程内复用避免重复构建，跨线程天然隔离（实测 JavaParser 实例跨线程并发 parse 不可靠）
     */
    private static final ThreadLocal<JavaParser> JAVA_PARSER = ThreadLocal.withInitial(() -> new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25)));

    /**
     * 最大类解析嵌套深度
     */
    private static final int MAX_PARSE_DEPTH = 64;

    /**
     * 解析会话：本解析器实例（含其递归子解析器）共享的解析状态（root路径、类模板缓存、嵌套深度）
     */
    private final ParseSession session;

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

    /**
     * 构造解析器实例（自带全新解析会话）
     */
    public ClassParser() {
        this(new ParseSession(), null);
    }

    /**
     * 构造解析器实例（自带全新解析会话）
     *
     * @param classNode 预置的类节点（通常为子类实现专用节点，如 ControllerNode）
     */
    public ClassParser(T classNode) {
        this(new ParseSession(), classNode);
    }

    /**
     * 构造解析器实例（复用指定解析会话，用于多解析器共享root路径与类模板缓存）
     *
     * @param session 解析会话；为 null 时自动创建新会话
     */
    public ClassParser(ParseSession session) {
        this(session, null);
    }

    /**
     * 构造解析器实例（复用指定解析会话）
     *
     * @param session   解析会话；为 null 时自动创建新会话
     * @param classNode 预置的类节点；为 null 时自动创建普通 ClassNode
     */
    public ClassParser(ParseSession session, T classNode) {
        this.session = session != null ? session : new ParseSession();
        this.classNode = classNode != null ? classNode : (T) new ClassNode();
    }

    /**
     * 添加root路径（解析引用类时的根查找范围）
     *
     * @param rootPath java源码绝对路径，必须以 java 结尾、不带尾部 / 或 \
     */
    public void addRootPath(String rootPath) {
        session.addRootPath(rootPath);
    }

    /**
     * 批量添加root路径
     *
     * @param rootPaths java源码绝对路径集
     */
    public void addRootPaths(Set<String> rootPaths) {
        session.addRootPaths(rootPaths);
    }

    /**
     * 清空当前会话的类模板缓存
     */
    public void clearCache() {
        session.clearCache();
    }

    /**
     * 清空当前会话的root路径集
     */
    public void clearRootPaths() {
        session.clearRootPaths();
    }

    /**
     * 校验root路径（静态纯校验，无状态）
     *
     * @param rootPath 待校验的root路径
     * @return 路径以 java 结尾且非空时返回 true
     */
    public static boolean checkRootPath(String rootPath) {
        return ParseSession.isValidRootPath(rootPath);
    }

    /**
     * 释放当前线程的局部解析器实例（由 {@link LimeJapiDocsParser#build} 结束后自动调用；
     * 直接使用 {@link ClassParser#parse} 的场景如需立即释放线程局部解析器可自行调用）
     */
    public static void removeJavaParser() {
        JAVA_PARSER.remove();
    }

    /**
     * 获取当前会话root路径集（不可修改视图）
     *
     * @return 本会话已登记的root路径集
     */
    public Set<String> getSessionRootPaths() {
        return session.getRootPaths();
    }

    /**
     * 创建共享当前会话的子解析器（递归引用解析用，session 自动继承）
     *
     * @return 与当前会话绑定的普通类解析器
     */
    private ClassParser<ClassNode> newChildParser() {
        return new ClassParser<ClassNode>(this.session) {
        };
    }

    /**
     * 加载类；对 JDK 内部嵌套类支持 binary 名（java.util.Map.Entry → java.util.Map$Entry）
     */
    private Class<?> loadClassWithNestedFallback(String fullName) {
        try {
            return ClassUtil.loadClass(fullName);
        } catch (Exception e) {
//            logger.info("读取类异常: ".concat(fullName));
        }
        int dot = fullName.lastIndexOf('.');
        if (dot > 0) {
            String binaryName = fullName.substring(0, dot).concat("$").concat(fullName.substring(dot + 1));
            try {
                return ClassUtil.loadClass(binaryName);
            } catch (Exception ex) {
//                logger.info("读取类异常(binary): ".concat(binaryName));
            }
        }
        return null;
    }

    public File getJavaFile() {
        return javaFile;
    }

    /**
     * 解析标记：一个解析器实例只应解析一次（运行一次的实例语义），防止实例节点状态被二次解析复用污染
     */
    private boolean parsedOnce = false;

    /**
     * 当前正在填充的类型节点全名（顶层层为文件名主类全名，嵌套层为其嵌套全名），用于简单名嵌套类型引用的缓存定位
     */
    private String currentTypeFullName;

    /**
     * 解析方法
     *
     * @param javaFile java文件
     * @return 解析类节点
     */
    public T parse(File javaFile) {
        if (parsedOnce) {
            throw new IllegalStateException("ClassParser实例仅支持解析一次，如需再次解析请新建实例");
        }
        parsedOnce = true;
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
        int depth = session.getParseDepth();
        if (depth >= MAX_PARSE_DEPTH) {
            logger.warn("类依赖嵌套深度超过{}，已终止深层解析", MAX_PARSE_DEPTH);
            return null;
        }
        session.setParseDepth(depth + 1);
        try {
            if (javaFile == null || !javaFile.exists()) {
                logger.info("传入的javaFile不存在");
                return null;
            }
            CompilationUnit compilationUnit = JAVA_PARSER.get().parse(javaFile).getResult().orElse(null);
            if (compilationUnit == null) {
                logger.error("解析javaFile为compilationUnit失败: ".concat(javaFile.getAbsolutePath()));
                return null;
            }
            this.javaFile = javaFile;

            // 获取类名
            String javaFileName = javaFile.getName();
            String className = javaFileName.substring(0, javaFileName.lastIndexOf("."));
            classNode.setName(className);

            // 获取顶层类型解析对象（class/record/interface，record 的 AST 节点与 class 不同）
            TypeDeclaration<?> typeDoc = findPrimaryTypeDoc(compilationUnit, className);
            if (typeDoc == null) {
//                logger.warn("获取classDoc不存在：{}", javaFile.getPath());
                return null;
            }

            // 开始解析（顶层 class/interface 走旧签名钩子，record 走新增重载钩子）
            if (typeDoc instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDoc = (ClassOrInterfaceDeclaration) typeDoc;
                handleParseClassDocBefore(classNode, classDoc);
                parseClassDoc(classDoc, parentFieldName);
                handleParseClassDocAfter(classNode, classDoc);
            } else {
                handleParseClassDocBefore(classNode, typeDoc);
                parseTypeDoc(typeDoc, parentFieldName);
                handleParseClassDocAfter(classNode, typeDoc);
            }

            session.cacheClassNode(this.classNode);
            return this.classNode;
        } catch (CustomException e) {
//            logger.info(e.getMsg());
            return null;
        } catch (Exception e) {
            logger.error("java文件解析异常", e);
            return null;
        } finally {
            session.setParseDepth(depth);
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
     * 解析类文档之前触发（record 与嵌套类型专用重载）
     * 默认不做处理；需要拦截 record / 内部类解析时可覆写本方法
     *
     * @param classNode 类节点（嵌套类型时为独立挂载节点）
     * @param typeDoc   类型节点（class/interface/record）
     */
    protected void handleParseClassDocBefore(ClassNode classNode, TypeDeclaration<?> typeDoc) {
    }

    /**
     * 解析类文档之后触发（record 与嵌套类型专用重载）
     * 默认不做处理；需要拦截 record / 内部类解析时可覆写本方法
     *
     * @param classNode 类节点（嵌套类型时为独立挂载节点）
     * @param typeDoc   类型节点（class/interface/record）
     */
    protected void handleParseClassDocAfter(ClassNode classNode, TypeDeclaration<?> typeDoc) {
    }

    /**
     * 查找文件主类型（class -> record -> interface）
     */
    private TypeDeclaration<?> findPrimaryTypeDoc(CompilationUnit compilationUnit, String className) {
        Optional<ClassOrInterfaceDeclaration> classDoc = compilationUnit.getClassByName(className);
        if (classDoc.isPresent()) {
            return classDoc.get();
        }
        Optional<RecordDeclaration> recordDoc = compilationUnit.getRecordByName(className);
        if (recordDoc.isPresent()) {
            return recordDoc.get();
        }
        Optional<ClassOrInterfaceDeclaration> interfaceDoc = compilationUnit.getInterfaceByName(className);
        if (interfaceDoc.isPresent()) {
            return interfaceDoc.get();
        }
        return null;
    }


    /**
     * 解析 class/interface 顶层对象（沿用旧解析语义）
     * 先解析嵌套类型（挂载+缓存），再解析字段/方法，便于字段以简单名引用内部类
     */
    private void parseClassDoc(ClassOrInterfaceDeclaration classDoc, String parentFieldName) {
        parseBaseMeta(this.classNode, classDoc, parentFieldName, true);
        parseNestedTypes(this.classNode, classDoc, parentFieldName);
        parseMembersAndExtends(this.classNode, classDoc, parentFieldName);
    }

    /**
     * 解析 record 等非 class/interface 的顶层类型（record 组件会按属性解析）
     */
    private void parseTypeDoc(TypeDeclaration<?> typeDoc, String parentFieldName) {
        parseBaseMeta(this.classNode, typeDoc, parentFieldName, true);
        parseNestedTypes(this.classNode, typeDoc, parentFieldName);
        parseMembersAndExtends(this.classNode, typeDoc, parentFieldName);
    }

    /**
     * 解析类型公共元数据（修饰符/包全名/导包/注释/注解/泛型映射）
     *
     * @param target         解析目标节点（顶层为当前解析器节点，嵌套为独立新节点）
     * @param typeDoc        待解析类型节点（class/interface/record 均可）
     * @param parentFieldName 父级字段名
     * @param topLevel       是否顶层类型；嵌套类型由调用方预先设置 name/fullName/packageName/filePackagePath
     */
    private void parseBaseMeta(ClassNode target, TypeDeclaration<?> typeDoc, String parentFieldName, boolean topLevel) {
        // @ParseIgnore（按注解名匹配，兼容任意类型节点）
        for (AnnotationExpr annotationExpr : typeDoc.getAnnotations()) {
            if (ParseIgnore.class.getSimpleName().equals(annotationExpr.getNameAsString())) {
                target.setIgnore(true);
                break;
            }
        }

        // 获取修饰符
        typeDoc.getModifiers().forEach(modifier -> target.addModifier(modifier.getKeyword().asString()));

        CompilationUnit cu = findCompilationUnit(typeDoc);

        // 解析获取类全名和java文件所在包路径（仅顶层计算文件名主类）
        if (topLevel && cu != null) {
            cu.getPackageDeclaration().ifPresent(packageDeclaration -> {
                String packageName = packageDeclaration.getNameAsString();
                if (StringUtil.isNotBlank(packageName)) {
                    String fullName = packageName.concat(".").concat(target.getName());
                    target.setPackageName(packageName);
                    target.setFullName(fullName);
                    // 记录当前类和对应字段名称
                    if (parentNodeNameMap != null) {
                        parentNodeNameMap.put(fullName, parentFieldName);
                    }
                    String filePackagePath = javaFile.getAbsolutePath().replace(ParseUtil.fullNameToRelativePath(fullName), "");
                    target.setFilePackagePath(filePackagePath);
                }
            });
        }

        // 解析类导包（文件级导入，顶层与嵌套一致）
        if (cu != null) {
            for (ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
                String fullName = importDeclaration.getName().asString();
                // 防御：tokenRange 缺失时直接按导入名处理
                if (importDeclaration.getTokenRange().isPresent()) {
                    String tokenRange = importDeclaration.getTokenRange().get().toString();
                    tokenRange = tokenRange.substring(0, tokenRange.lastIndexOf(";")).trim();
                    if (tokenRange.contains("*")) {
                        fullName = fullName.concat(".*");
                    }
                }
                String className = null;
                if (StringUtil.isNotBlank(fullName) && fullName.lastIndexOf(".") > 0) {
                    className = fullName.substring(fullName.lastIndexOf(".") + 1);
                }
                target.addImportNode(new ImportNode(className, fullName));
            }
        }

        // 解析获取类注释标签集 author和description优先用tag里的
        ParseUtil.parseJavaDoc(typeDoc.getJavadoc())
                .forEach(tagNode -> target.addTagNode(tagNode));

        // 解析获取类注解
        ParseUtil.parseAnnotation(typeDoc.getAnnotations())
                .forEach(annotationNode -> target.addAnnotationNode(annotationNode));

        // 解析类泛型映射
        if (typeDoc instanceof NodeWithTypeParameters) {
            parseMapGenericity(((NodeWithTypeParameters<?>) typeDoc).getTypeParameters());
        }
    }

    /**
     * 解析类的成员（属性/方法/继承/实现接口），并完成节点收尾
     */
    private void parseMembersAndExtends(ClassNode target, TypeDeclaration<?> typeDoc, String parentFieldName) {
        // 记录当前填充的类型节点上下文，供简单名嵌套引用定位缓存
        this.currentTypeFullName = target.getFullName();
        if (typeDoc instanceof NodeWithMembers) {
            NodeWithMembers<?> membersDoc = (NodeWithMembers<?>) typeDoc;
            parseField(target, membersDoc.getFields());
            parseMethod(target, membersDoc.getMethods());
        }

        // record 组件转为属性节点
        if (typeDoc instanceof RecordDeclaration) {
            parseRecordComponents(target, ((RecordDeclaration) typeDoc).getParameters());
        }

        // 解析获取类继承（仅 class 有显式 extends）
        if (typeDoc instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDoc = (ClassOrInterfaceDeclaration) typeDoc;
            if (classDoc.getExtendedTypes().isNonEmpty()) {
                target.setExtendsNode(parseClassByType(classDoc.getExtendedTypes(0), target));
            }
        }

        // 解析获取类实现接口（class/interface/record 均有）
        if (typeDoc instanceof NodeWithImplements) {
            for (ClassOrInterfaceType implementedType : ((NodeWithImplements<?>) typeDoc).getImplementedTypes()) {
                target.addImplementsNode(parseClassByType(implementedType, target));
            }
        }

        // 最后构造类节点
        target.lastBuild();
    }

    /**
     * 解析 record 组件为属性节点
     * 组件注释支持两种写法：组件声明处 javadoc；或 record 类级 javadoc 的 @param（按组件名匹配回填）
     */
    private void parseRecordComponents(ClassNode target, NodeList<Parameter> parameters) {
        if (ListUtil.isBlank(parameters)) {
            return;
        }
        for (Parameter parameter : parameters) {
            FieldNode fieldNode = new FieldNode();
            fieldNode.setName(parameter.getNameAsString());
            fieldNode.setValueTypeClassNode(parseClassByType(parameter.getType(), fieldNode));
            ParseUtil.parseAnnotation(parameter.getAnnotations())
                    .forEach(annotationNode -> fieldNode.addAnnotationNode(annotationNode));
            // 写法一：组件声明处注释（JavaDoc形式）
            if (parameter.getComment().isPresent() && parameter.getComment().get() instanceof JavadocComment) {
                JavadocComment javadocComment = (JavadocComment) parameter.getComment().get();
                ParseUtil.parseJavaDoc(Optional.of(javadocComment.parse()))
                        .forEach(tagNode -> fieldNode.addTagNode(tagNode));
            }
            fieldNode.lastBuild();
            // 写法二：record 类级 javadoc 的 @param（tagKey=组件名）回填组件注释
            if (StringUtil.isBlank(fieldNode.getComment())) {
                TagNode paramTagNode = getParamTagNode(target, fieldNode.getName());
                if (paramTagNode != null) {
                    fieldNode.setComment(paramTagNode.getTagValue());
                }
            }
            target.addFieldNode(fieldNode);
        }
    }

    /**
     * 从类节点 javadoc 标签集中匹配 @param 标签（tagName=param）
     */
    private TagNode getParamTagNode(ClassNode target, String tagKey) {
        if (target.getTagNodeList() == null || StringUtil.isBlank(tagKey)) {
            return null;
        }
        for (Object objectTagNode : target.getTagNodeList()) {
            TagNode tagNode = (TagNode) objectTagNode;
            if ("param".equals(tagNode.getTagName()) && tagKey.equals(tagNode.getTagKey())) {
                return tagNode;
            }
        }
        return null;
    }

    /**
     * 解析声明在类型体内的嵌套类型（内部类/内部静态类/嵌套record），挂载到外层并注册缓存
     */
    private void parseNestedTypes(ClassNode target, TypeDeclaration<?> typeDoc, String parentFieldName) {
        for (BodyDeclaration<?> member : typeDoc.getMembers()) {
            TypeDeclaration<?> nestedDoc;
            if (member.isClassOrInterfaceDeclaration()) {
                nestedDoc = member.asClassOrInterfaceDeclaration();
            } else if (member.isRecordDeclaration()) {
                nestedDoc = member.asRecordDeclaration();
            } else {
                continue;
            }
            buildNestedClassNode(target, nestedDoc, parentFieldName);
        }
    }

    /**
     * 构建单个嵌套类型节点
     */
    private void buildNestedClassNode(ClassNode target, TypeDeclaration<?> nestedDoc, String parentFieldName) {
        ClassNode nestedNode = new ClassNode();
        nestedNode.setName(nestedDoc.getNameAsString());
        nestedNode.setFullName(target.getFullName().concat(".").concat(nestedNode.getName()));
        // setFullName 会按最后一个点重算 packageName，此处回设真实包名
        nestedNode.setPackageName(target.getPackageName());
        nestedNode.setFilePackagePath(target.getFilePackagePath());
        if (parentNodeNameMap != null) {
            parentNodeNameMap.put(nestedNode.getFullName(), parentFieldName);
        }
        handleParseClassDocBefore(nestedNode, nestedDoc);
        parseBaseMeta(nestedNode, nestedDoc, parentFieldName, false);
        parseNestedTypes(nestedNode, nestedDoc, parentFieldName);
        parseMembersAndExtends(nestedNode, nestedDoc, parentFieldName);
        handleParseClassDocAfter(nestedNode, nestedDoc);
        target.addNestedClassNode(nestedNode);
        session.cacheClassNode(nestedNode);
    }

    private CompilationUnit findCompilationUnit(TypeDeclaration<?> typeDoc) {
        return typeDoc.findCompilationUnit().orElse(null);
    }

    /**
     * 补全 外部类.内部类 形式引用的全名
     * 优先取最外层简单名匹配的 import 全名拼接；其次尝试同包；全限定名原样返回
     */
    private String resolveInnerClassFullName(String dottedName) {
        if (StringUtil.isBlank(dottedName)) {
            return dottedName;
        }
        int firstDot = dottedName.indexOf('.');
        if (firstDot <= 0) {
            return dottedName;
        }
        String packageName = this.classNode.getPackageName();
        // 已是全限定名（含当前包前缀或 java.* 等），原样返回
        if (StringUtil.isNotBlank(packageName) && dottedName.startsWith(packageName.concat("."))) {
            return dottedName;
        }
        String outerSimple = dottedName.substring(0, firstDot);
        String remain = dottedName.substring(firstDot + 1);
        // 最外层与 import 匹配则拼接全名（如 import a.b.Outer 后用 Outer.Inner）
        List<ImportNode> importNodes = this.classNode.getImportNodeByClassName(outerSimple);
        if (ListUtil.isNotBlank(importNodes)) {
            return importNodes.get(0).getFullName().concat(".").concat(remain);
        }
        // 同包引用（如 Outer.Inner 且未 import）：仅当该包下文件确实存在时才拼接，否则原样返回避免拼造错误全名
        if (StringUtil.isNotBlank(packageName)) {
            String samePackage = packageName.concat(".").concat(dottedName);
            if (resolveJavaFileForFullName(samePackage) != null) {
                return samePackage;
            }
        }
        return dottedName;
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
     * @param target                 解析目标节点
     * @param methodDeclarationList  待解析方法集
     */
    private void parseMethod(ClassNode target, List<MethodDeclaration> methodDeclarationList) {
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
            methodDeclaration.getModifiers().forEach(modifier -> methodNode.addModifier(modifier.getKeyword().asString()));
            // 解析方法的注释
            ParseUtil.parseJavaDoc(methodDeclaration.getJavadoc())
                    .forEach(tagNode -> methodNode.addTagNode(tagNode));
            // 解析方法的注解
            ParseUtil.parseAnnotation(methodDeclaration.getAnnotations())
                    .forEach(annotationNode -> methodNode.addAnnotationNode(annotationNode));
            // 解析方法参数
            Map<String, String> typeExtendsMap = null;
            NodeList<TypeParameter> typeParameters = methodDeclaration.getTypeParameters();
            if (typeParameters != null && typeParameters.size() > 0) {
                for (TypeParameter typeParameter : typeParameters) {
                    NodeList<ClassOrInterfaceType> typeBounds = typeParameter.getTypeBound();
                    if (typeBounds != null && typeBounds.size() > 0) {
                        typeExtendsMap = new HashMap<>();
                        typeExtendsMap.put(typeParameter.getNameAsString(), typeBounds.get(0).getNameAsString());
                    }
                }
            }
            Map<String, String> finalTypeExtendsMap = typeExtendsMap;
            methodDeclaration.getParameters().forEach(parameter -> {
                ParamNode paramNode = new ParamNode();
                paramNode.setName(parameter.getNameAsString());
                TagNode tagNode = methodNode.getTagNodeByKey(parameter.getNameAsString());
                if (tagNode != null) {
                    paramNode.setComment(tagNode.getTagValue());
                }
                ParseUtil.parseAnnotation(parameter.getAnnotations())
                        .forEach(paramAnnotationNode -> paramNode.addAnnotationNode(paramAnnotationNode));
                paramNode.setParamType(parseClassByType(parameter.getType(), paramNode, finalTypeExtendsMap));
                methodNode.addParamNode(paramNode);
            });
            // 解析方法返回类型
            methodNode.setReturnNode(parseClassByType(methodDeclaration.getType(), methodNode));
            methodNode.lastBuild(false);
            target.addMethodNode(methodNode);
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
     * @param target               解析目标节点
     * @param fieldDeclarationList 待解析属性集
     */
    private void parseField(ClassNode target, List<FieldDeclaration> fieldDeclarationList) {
        if (ListUtil.isBlank(fieldDeclarationList)) {
            return;
        }
        fieldDeclarationList.forEach(fieldDeclaration -> {
            FieldNode fieldNode = new FieldNode();
            if (fieldDeclaration.getAnnotationByClass(ParseIgnore.class).isPresent()) {
                fieldNode.setIgnore(true);
            }
            // 获取修饰符
            fieldDeclaration.getModifiers().forEach(modifier -> fieldNode.addModifier(modifier.getKeyword().asString()));
            // 获取属性
            fieldDeclaration.getVariables().ifNonEmpty(variableDeclarators -> variableDeclarators.forEach(variableDeclarator -> {
                String fieldName = variableDeclarator.getName().asString();
                fieldNode.setName(fieldName);
                fieldNode.setValueTypeClassNode(parseClassByType(variableDeclarator.getType(), fieldNode));
            }));
            ParseUtil.parseAnnotation(fieldDeclaration.getAnnotations())
                    .forEach(annotationNode -> fieldNode.addAnnotationNode(annotationNode));
            ParseUtil.parseJavaDoc(fieldDeclaration.getJavadoc())
                    .forEach(tagNode -> fieldNode.addTagNode(tagNode));
            // 最后构造属性节点
            fieldNode.lastBuild();
            target.addFieldNode(fieldNode);
        });
    }

    /**
     * 根据类型解析为类节点
     *
     * @param baseNode 父级节点 用于记录标注类嵌套
     * @param type 类型
     */
    private ClassNode parseClassByType(Type type, BaseNode baseNode) {
        return parseClassByType(type, baseNode, null);
    }

    /**
     * 根据类型解析为类节点
     *
     * @param baseNode 父级节点 用于记录标注类嵌套
     * @param type 类型
     * @param typeExtendsMap 类型继承关系映射（方法上有的泛型继承于某个类，如果本身找不到则取继承的类）
     */
    private ClassNode parseClassByType(Type type, BaseNode baseNode, Map<String, String> typeExtendsMap) {
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
            // 直接为类全名（或 外部类.内部类，需从import/同包补全外层全名）
            if (dotIndex > -1) {
                String resolvedFullName = resolveInnerClassFullName(className);
                ClassNode _classNode = parseClassByFullName(classOrInterfaceType, resolvedFullName, baseNode);
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
                } if (typeExtendsMap != null && typeExtendsMap.containsKey(className)) {
                    className = typeExtendsMap.get(className);
                    _classNode = fromGenericityNodeMap.get(className);
                    if (_classNode != null) {
                        return _classNode;
                    }
                }

                // 可能是当前类型体内的嵌套类型（内部类以简单名引用，从当前层逐层向上定位）
                if (StringUtil.isNotBlank(currentTypeFullName)) {
                    String prefix = currentTypeFullName;
                    ClassNode nestedCached;
                    boolean hit = false;
                    do {
                        nestedCached = session.getCachedClassNode(prefix.concat(".").concat(className));
                        if (nestedCached != null) {
                            hit = true;
                            break;
                        }
                        int idx = prefix.lastIndexOf('.');
                        if (idx <= 0) {
                            break;
                        }
                        prefix = prefix.substring(0, idx);
                    } while (true);
                    if (hit) {
                        return nestedCached;
                    }
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
        // 无泛型实参时优先复用模板缓存，避免重复解析同一类
        if (!classOrInterfaceType.getTypeArguments().isPresent()) {
            ClassNode cachedClassNode = session.getCachedClassNode(fullName);
            if (cachedClassNode != null) {
                return cachedClassNode;
            }
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
            clazz = loadClassWithNestedFallback(fullName);
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
                // 内部类/嵌套类引用：定位外层真实文件解析（会连带解析嵌套类型并缓存），再从缓存取目标
                File javaFile = resolveJavaFileForFullName(fullName);
                if (javaFile != null) {
                    ClassParser<ClassNode> classParser = newChildParser();
                    classNode = classParser.parse(javaFile, classNode.getGenericityNodeList(), parentNodeNameMap, baseNode == null ? null : baseNode.getName());
                    if (classNode != null) {
                        ClassNode cachedClassNode = session.getCachedClassNode(fullName);
                        if (cachedClassNode != null) {
                            return cachedClassNode;
                        }
                        return classNode;
                    }
                }
            } catch (Exception e) {
//                logger.info("读取类异常：{}", fullName);
                return null;
            }
        }
        return null;
    }

    /**
     * 定位类全名对应的真实java文件（支持内部类：逐级剥离内层名称直至找到所在外层文件）
     */
    private File resolveJavaFileForFullName(String fullName) {
        if (StringUtil.isBlank(fullName)) {
            return null;
        }
        String fq = fullName;
        while (true) {
            File candidate = findJavaFileByFullName(fq);
            if (candidate != null) {
                return candidate;
            }
            int dot = fq.lastIndexOf('.');
            if (dot <= 0) {
                return null;
            }
            fq = fq.substring(0, dot);
        }
    }

    /**
     * 按类全名在当前文件包路径或root集下查找java文件
     */
    private File findJavaFileByFullName(String fullName) {
        String relativePath = ParseUtil.fullNameToRelativePath(fullName);
        if (StringUtil.isNotBlank(this.classNode.getFilePackagePath())) {
            File javaFile = new File(this.classNode.getFilePackagePath().concat(relativePath));
            if (javaFile.exists()) {
                return javaFile;
            }
        }
        for (String rootPath : session.getRootPaths()) {
            File javaFile = new File(rootPath.concat(relativePath));
            if (javaFile.exists()) {
                return javaFile;
            }
        }
        return null;
    }


}
