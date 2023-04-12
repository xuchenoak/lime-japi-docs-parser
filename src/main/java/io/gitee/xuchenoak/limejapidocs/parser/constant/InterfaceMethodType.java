package io.gitee.xuchenoak.limejapidocs.parser.constant;

/**
 * 请求方式与注解映射
 *
 * @author xuchenoak
 **/
public enum InterfaceMethodType {

    GET("GET", "GetMapping"),
    PUT("PUT", "PutMapping"),
    POST("POST", "PostMapping"),
    DELETE("DELETE", "DeleteMapping"),
    PATCH("PATCH", "PatchMapping"),
    DEFAULT("DEFAULT", "RequestMapping");

    private String httpMethod;
    private String annotation;

    InterfaceMethodType(String httpMethod, String annotation) {
        this.httpMethod = httpMethod;
        this.annotation = annotation;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getAnnotation() {
        return annotation;
    }

    public static InterfaceMethodType getMethodTypeByAnnotation(String annotation) {
        if (annotation == null || annotation.length() == 0) {
            return null;
        }
        for (InterfaceMethodType methodType : InterfaceMethodType.values()) {
            if (methodType.annotation.equals(annotation)) {
                return methodType;
            }
        }
        return null;
    }

    public static InterfaceMethodType getMethodTypeByHttpMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.length() == 0) {
            return null;
        }
        for (InterfaceMethodType methodType : InterfaceMethodType.values()) {
            if (methodType.httpMethod.equals(httpMethod.toUpperCase())) {
                return methodType;
            }
        }
        return null;
    }

    public static String getHttpMethod(String annotation) {
        if (annotation == null || annotation.length() == 0) {
            return "";
        }
        for (InterfaceMethodType methodType : InterfaceMethodType.values()) {
            if (methodType.annotation.equals(annotation)) {
                return methodType.httpMethod;
            }
        }
        return "";
    }

    public static String getAnnotation(String httpMethod) {
        if (httpMethod == null || httpMethod.length() == 0) {
            return "";
        }
        for (InterfaceMethodType methodType : InterfaceMethodType.values()) {
            if (methodType.httpMethod.equals(httpMethod.toUpperCase())) {
                return methodType.annotation;
            }
        }
        return "";
    }


}
