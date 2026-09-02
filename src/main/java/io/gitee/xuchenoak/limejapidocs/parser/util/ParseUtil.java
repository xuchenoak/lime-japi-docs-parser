package io.gitee.xuchenoak.limejapidocs.parser.util;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.AnnotationNode;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.FieldNode;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.TagNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 解析工具
 *
 * @author xuchenoak
 **/
public class ParseUtil {

    public static final String TAG_NAME_AUTHOR = "author";
    public static final String TAG_NAME_COMMENT = "comment";
    public static final String TAG_NAME_DESCRIPTION = "description";
    public static final String TAG_NAME_PARAM = "param";
    public static final String JAVA_PACKAGE_PREFIX = "java.";
    public static final String JAVA_PACKAGE_LANG = "java.lang.";

    /**
     * 基础类型及其包装类对照表（静态只读，初始化后不再被修改，可并发访问）
     */
    private static Map<String, Class> commonTypeMap = new HashMap<>();

    public static Class getCommonType(String key) {
        return commonTypeMap.get(key);
    }

    static {
        commonTypeMap.put("byte", Byte.class);
        commonTypeMap.put(Byte.class.getName(), Byte.class);
        commonTypeMap.put("short", Short.class);
        commonTypeMap.put(Short.class.getName(), Short.class);
        commonTypeMap.put("int", Integer.class);
        commonTypeMap.put(Integer.class.getName(), Integer.class);
        commonTypeMap.put("long", Long.class);
        commonTypeMap.put(Long.class.getName(), Long.class);
        commonTypeMap.put("float", Float.class);
        commonTypeMap.put(Float.class.getName(), Float.class);
        commonTypeMap.put("double", Double.class);
        commonTypeMap.put(Double.class.getName(), Double.class);
        commonTypeMap.put("char", Character.class);
        commonTypeMap.put(Character.class.getName(), Character.class);
        commonTypeMap.put("boolean", Boolean.class);
        commonTypeMap.put(Boolean.class.getName(), Boolean.class);
        commonTypeMap.put(BigDecimal.class.getName(), BigDecimal.class);
        commonTypeMap.put(BigInteger.class.getName(), BigInteger.class);
    }


    /**
     * 解析注释标签
     *
     * @param javadocOptional java注释集
     * @return 标签节点集
     */
    public static List<TagNode> parseJavaDoc(Optional<Javadoc> javadocOptional) {
        List<TagNode> tagNodeList = new ArrayList<>();
        if (javadocOptional == null) {
            return tagNodeList;
        }
        javadocOptional.ifPresent(d -> {
            String comment = d.getDescription().toText();
            if (StringUtil.isNotBlank(comment)) {
                tagNodeList.add(new TagNode(TAG_NAME_COMMENT, "", comment));
            }
            List<JavadocBlockTag> blockTags = d.getBlockTags();
            if (ListUtil.isNotBlank(blockTags)) {
                for (JavadocBlockTag blockTag : blockTags) {
                    String tagName = blockTag.getTagName();
                    String tagKey = null;
                    if (blockTag.getName().isPresent()) {
                        tagKey = blockTag.getName().get();
                    }
                    String tagValue = blockTag.getContent().toText();
                    tagNodeList.add(new TagNode(tagName, tagKey, tagValue));
                }
            }
        });
        return tagNodeList;
    }

    /**
     * 解析注解源节点
     *
     * @param annotationExprNodeList 注解源节点集
     * @return 注解节点集
     */
    public static List<AnnotationNode> parseAnnotation(NodeList<AnnotationExpr> annotationExprNodeList) {
        List<AnnotationNode> annotationNodeList = new ArrayList<>();
        if (annotationExprNodeList == null) {
            return annotationNodeList;
        }
        annotationExprNodeList.ifNonEmpty(annotationExprs -> annotationExprs.forEach(annotationExpr -> {
            List<FieldNode> fieldNodeList = new ArrayList<>();
            // 有一个参数，且不带key的注解
            if (annotationExpr.isSingleMemberAnnotationExpr()) {
                SingleMemberAnnotationExpr singleMemberAnnotationExpr = (SingleMemberAnnotationExpr) annotationExpr;
                Expression expression = singleMemberAnnotationExpr.getMemberValue();
                FieldNode fieldNode = getExpressionValue(expression);
                if (fieldNode != null) {
                    fieldNode.setName("value");
                    fieldNodeList.add(fieldNode);
                }
            }
            // 有一个或多个参数，且带key的注解
            else if (annotationExpr.isNormalAnnotationExpr()) {
                NormalAnnotationExpr normalAnnotationExpr = annotationExpr.asNormalAnnotationExpr();
                List<FieldNode> _fieldNodeList = new ArrayList<>();
                normalAnnotationExpr.getPairs().ifNonEmpty(memberValuePairs -> memberValuePairs.forEach(memberValuePair -> {
                    FieldNode fieldNode = getExpressionValue(memberValuePair.getValue());
                    if (fieldNode != null) {
                        fieldNode.setName(memberValuePair.getNameAsString());
                        _fieldNodeList.add(fieldNode);
                    }
                }));
                fieldNodeList.addAll(_fieldNodeList);
            }
            // 没有参数的注解 不处理
            else if (annotationExpr.isMarkerAnnotationExpr()) {
            }
            String annotationName = annotationExpr.getName().asString();
            annotationNodeList.add(new AnnotationNode(annotationName, fieldNodeList));
        }));
        return annotationNodeList;
    }

    /**
     * 获取表达式值
     *
     * @param expression 表达式
     */
    private static FieldNode getExpressionValue(Expression expression) {
        if (expression.isBooleanLiteralExpr()) {
            return new FieldNode(expression.asBooleanLiteralExpr().getValue());
        } else if (expression.isStringLiteralExpr()) {
            return new FieldNode(expression.asStringLiteralExpr().getValue());
        } else if (expression.isArrayInitializerExpr()) {
            List<FieldNode> nodeList = new ArrayList<>();
            expression.asArrayInitializerExpr().getValues().ifNonEmpty(expressions -> expressions.forEach(_expression -> {
                FieldNode fieldNode = getExpressionValue(_expression);
                if (fieldNode != null) {
                    nodeList.add(fieldNode);
                }
            }));
            if (nodeList.size() > 0) {
                return new FieldNode(nodeList.stream().map(FieldNode::getValue).collect(Collectors.toList()), true);
            }
        } else if (expression.isLongLiteralExpr()) {
            return new FieldNode(expression.asLongLiteralExpr().getValue());
        } else if (expression.isIntegerLiteralExpr()) {
            return new FieldNode(expression.asIntegerLiteralExpr().getValue());
        } else if (expression.isFieldAccessExpr()) {
            return new FieldNode(expression.asFieldAccessExpr().getNameAsString());
        }
        return null;
    }

    /**
     * 类全名转java文件路径
     *
     * @param fullName 类全名
     * @return 文件路径
     */
    public static String fullNameToRelativePath(String fullName) {
        if (StringUtil.isBlank(fullName)) {
            return null;
        }
        return "/".concat(fullName.replace(".", "/")).concat(".java");
    }

    /**
     * 轻量读取java文件的顶层类型全名（包名.顶层类型名），仅扫描文件头，不做完整语法解析。
     * 用于初始化建索引 / build 阶段按全名过滤 controller 文件，速度快于完整 JavaParser parse。
     * 仅处理最常规的「package 声明 + 顶层 class/interface/record/enum」写法，非常规写法返回 null。
     *
     * @param javaFile java文件
     * @return 顶层类型全名；无法识别时返回 null
     */
    public static String readTopLevelFullName(File javaFile) {
        if (javaFile == null || !javaFile.isFile()) {
            return null;
        }
        String packageName = null;
        String topTypeName = null;
        int braceDepth = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(javaFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                    continue;
                }
                // package 声明（仅文件头，braceDepth==0 时）
                if (braceDepth == 0 && packageName == null && trimmed.startsWith("package ")) {
                    int semi = trimmed.indexOf(';');
                    if (semi > "package ".length()) {
                        packageName = trimmed.substring("package ".length(), semi).trim();
                    }
                    continue;
                }
                // 追踪花括号深度，识别顶层类型
                if (topTypeName == null) {
                    String typeName = matchTopLevelType(trimmed);
                    if (typeName != null && braceDepth == 0) {
                        topTypeName = typeName;
                    }
                }
                braceDepth += countChar(trimmed, '{') - countChar(trimmed, '}');
            }
        } catch (Exception e) {
            return null;
        }
        if (StringUtil.isBlank(topTypeName)) {
            return null;
        }
        return StringUtil.isNotBlank(packageName) ? packageName.concat(".").concat(topTypeName) : topTypeName;
    }

    /**
     * 匹配顶层类型声明（class/interface/record/enum <Name>），返回类型名
     */
    private static String matchTopLevelType(String trimmed) {
        // 跳过注释行剩余部分
        int commentIdx = trimmed.indexOf("//");
        if (commentIdx >= 0) {
            trimmed = trimmed.substring(0, commentIdx).trim();
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        for (String keyword : new String[]{"class", "interface", "record", "enum"}) {
            // 要求关键字后紧跟空白再跟标识符，且不以 @ 开头（注解）
            if (trimmed.startsWith("@")) {
                return null;
            }
            int idx = trimmed.indexOf(keyword);
            if (idx < 0) {
                continue;
            }
            int nameStart = idx + keyword.length();
            if (nameStart < trimmed.length() && Character.isWhitespace(trimmed.charAt(nameStart))) {
                int cursor = nameStart;
                while (cursor < trimmed.length() && Character.isWhitespace(trimmed.charAt(cursor))) {
                    cursor++;
                }
                int nameEnd = cursor;
                while (nameEnd < trimmed.length()
                        && (Character.isLetterOrDigit(trimmed.charAt(nameEnd)) || trimmed.charAt(nameEnd) == '_')) {
                    nameEnd++;
                }
                if (nameEnd > cursor) {
                    return trimmed.substring(cursor, nameEnd);
                }
            }
        }
        return null;
    }

    /**
     * 统计字符串中某字符出现次数
     */
    private static int countChar(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }


}
