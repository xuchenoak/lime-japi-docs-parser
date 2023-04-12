package io.gitee.xuchenoak.limejapidocs.parser.basenode;

import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点基类
 *
 * @author xuchenoak
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImportNode {

    /**
     * 类名
     */
    private String className;

    /**
     * 类全名
     */
    private String fullName;

    public String fullNameToRelativePath() {
        if (StringUtil.isBlank(fullName)) {
            return null;
        }
        return "/".concat(fullName.replace(".", "/")).concat(".java");
    }

}
