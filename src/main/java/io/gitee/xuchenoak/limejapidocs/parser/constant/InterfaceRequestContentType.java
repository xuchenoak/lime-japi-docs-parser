package io.gitee.xuchenoak.limejapidocs.parser.constant;

/**
 * 接口请求参数类型
 *
 * @author xuchenoak
 **/
public enum InterfaceRequestContentType {

    FORM_URLENCODED("application/x-www-form-urlencoded"),
    FORM_DATA("multipart/form-data"),
    JSON("application/json");

    private String paramType;

    InterfaceRequestContentType(String paramType) {
        this.paramType = paramType;
    }

    public String getParamType() {
        return paramType;
    }
}
