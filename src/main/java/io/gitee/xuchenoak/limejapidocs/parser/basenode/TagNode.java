package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注释标签节点
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TagNode {

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签标识
     */
    private String tagKey;

    /**
     * 标签值
     */
    private String tagValue;

}
