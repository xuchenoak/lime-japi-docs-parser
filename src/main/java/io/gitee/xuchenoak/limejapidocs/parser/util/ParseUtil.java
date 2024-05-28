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

import java.math.BigDecimal;
import java.math.BigInteger;
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


}
