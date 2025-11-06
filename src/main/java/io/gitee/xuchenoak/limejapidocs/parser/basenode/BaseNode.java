package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.ParseUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点基类
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseNode<T extends BaseNode> {

    /**
     * 作者
     */
    private String author;

    /**
     * 注释
     */
    private String comment;

    /**
     * 类/属性/方法/注解 名
     */
    private String name;

    /**
     * 类/属性/方法/注解 全名
     */
    private String fullName;

    /**
     * 验证说明
     */
    private String validation;

    /**
     * 是否忽略（默认false）
     */
    private Boolean ignore = Boolean.FALSE;

    /**
     * 注解节点集
     */
    private List<AnnotationNode> annotationNodeList;

    /**
     * 注释标签集
     */
    private List<TagNode> tagNodeList;

    /**
     * 修饰符集
     */
    private List<String> modifierList;

    /**
     * 类嵌套说明
     */
    private String nestComment;

    public boolean isValidated() {
        return this.getAnnotationNodeByName("Validated") != null;
    }

    public boolean isValid() {
        return this.getAnnotationNodeByName("Valid") != null;
    }

    public void lastBuild() {
        lastBuild(true);
    }

    public void lastBuild(boolean appendNestComment) {
        TagNode authorTagNode = getTagNodeByName(ParseUtil.TAG_NAME_AUTHOR);
        if (authorTagNode != null) {
            author = authorTagNode.getTagValue();
        }
        TagNode commentTagNode = getTagNodeByName(ParseUtil.TAG_NAME_DESCRIPTION);
        if (commentTagNode != null) {
            comment = commentTagNode.getTagValue();
        } else {
            commentTagNode = getTagNodeByName(ParseUtil.TAG_NAME_COMMENT);
            if (commentTagNode != null) {
                comment = commentTagNode.getTagValue();
            }
        }
        if (comment != null && StringUtil.isNotBlank(nestComment) && appendNestComment) {
            comment = comment.concat("（结构同字段：").concat(nestComment).concat("）");
        }
    }

    public BaseNode(T t) {
        this.author = t.getAuthor();
        this.comment = t.getComment();
        this.name = t.getName();
        this.fullName = t.getFullName();
        this.ignore = t.getIgnore();
        this.annotationNodeList = t.getAnnotationNodeList();
        this.tagNodeList = t.getTagNodeList();
        this.modifierList = t.getModifierList();
    }

    public void addModifier(String modifier) {
        if (modifierList == null) {
            modifierList = new ArrayList<>();
        }
        modifierList.add(modifier);
    }

    public boolean isPublic() {
        if (modifierList == null) {
            return false;
        }
        return modifierList.contains("public");
    }

    public boolean isPrivate() {
        if (modifierList == null) {
            return false;
        }
        return modifierList.contains("private");
    }

    public boolean isStatic() {
        if (modifierList == null) {
            return false;
        }
        return modifierList.contains("static");
    }

    public void addAnnotationNode(AnnotationNode annotationNode) {
        if (annotationNodeList == null) {
            annotationNodeList = new ArrayList<>();
        }
        annotationNodeList.add(annotationNode);
    }

    public TagNode getTagNodeByName(String name) {
        if (ListUtil.isNotBlank(tagNodeList) && StringUtil.isNotBlank(name)) {
            for (TagNode tagNode : tagNodeList) {
                if (name.equals(tagNode.getTagName())) {
                    return tagNode;
                }
            }
        }
        return null;
    }

    public TagNode getTagNodeByKey(String key) {
        if (ListUtil.isNotBlank(tagNodeList) && StringUtil.isNotBlank(key)) {
            for (TagNode tagNode : tagNodeList) {
                if (key.equals(tagNode.getTagKey())) {
                    return tagNode;
                }
            }
        }
        return null;
    }

    public void addTagNode(TagNode tagNode) {
        if (tagNodeList == null) {
            tagNodeList = new ArrayList<>();
        }
        tagNodeList.add(tagNode);
    }

    public AnnotationNode getAnnotationNodeByName(String name) {
        if (ListUtil.isNotBlank(annotationNodeList) && StringUtil.isNotBlank(name)) {
            for (AnnotationNode annotationNode : annotationNodeList) {
                if (name.equals(annotationNode.getName())) {
                    return annotationNode;
                }
            }
        }
        return null;
    }

}
