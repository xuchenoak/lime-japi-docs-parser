package io.gitee.xuchenoak.limejapidocs.parser.util;


import java.util.Collection;

/**
 * list工具
 *
 * @author xuchenoak
 **/
public class ListUtil {

    public static boolean isBlank(Collection<?> list) {
        return list == null || list.isEmpty();
    }

    public static boolean isNotBlank(Collection<?> list) {
        return !isBlank(list);
    }

}
