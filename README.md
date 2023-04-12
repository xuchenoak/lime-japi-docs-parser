# lime-japi-docs-parser

## 1 简介

lime-japi-docs-parser是一个Java Controller接口解析器，可以从Java源码中解析出Controller类及其接口方法的名称、注释、注解、方法参数和方法返回值等信息，这些信息可简单用于进行接口文档权限配置或快速生成接口文档等。解析器内部还提供了抽象类ClassParser，继承后就可以自己实现对任意类的解析。

支持JDK：1.8+

## 2 安装
### 2.1 引入依赖（方式一）
```xml
<!-- maven lime-japi-docs-parser -->
<dependency>
    <groupId>io.gitee.xuchenoak</groupId>
    <artifactId>lime-japi-docs-parser</artifactId>
    <version>1.0.1</version>
</dependency>
```
### 2.2 下载jar包（方式二）
Maven中央库下载地址：https://repo1.maven.org/maven2/io/gitee/xuchenoak/lime-japi-docs-parser/

## 3 使用
### 3.1 源码注释
源码类、属性、方法的注释可参考如下：
```java
/**
 * 测试类（或用@description注释：@description 测试类）
 * @author testUser
 * @create 2022/11/22
 * ……
 **/
public class Test {

    /** 属性注释方式1（或用@description注释：@description 属性注释方式1） */
    private String testFieldOne;

    /**
     * 属性注释方式2（或用@description注释：@description 属性注释方式2）
     */
    private String testFieldTwo;

    /**
     * 方法注释（或用@description注释：@description 方法注释）
     * @param username 用户名（方法参数注释用@param）
     * @param age 年龄
     * @param depts 部门列表
     **/
    public User addUser(String username, Integer age, List<String> depts) {
        // ……
    }
    
}
```
### 3.2 调用入口
```java
public static void main(String[] args) {
    
    // 直接调用build方法即可（ParserConfigHandler详见3.2）
    LimeJapiDocsParser.build(new ParserConfigHandler() {

        /**
         * 提供解析相关配置
         * @return
         */
        @Override
        public ParserConfig getParserConfig() {
            // 路径为java源码绝对路径（必须写到java目录，且只能以java结尾，不带“/”）
            return ParserConfig.build("F:/**/src/main/java");
        }

        /**
         * 解析完成处理
         * @param controllerDataList Controller及其接口方法数据集
         */
        @Override
        public void parseFinishedHandle(List<ControllerData> controllerDataList) {
            // 处理解析的Controller及其接口方法数据集
        }
        
    });
}
```
### 3.3 ParserConfigHandler
```java
// 解析配置控制接口
public interface ParserConfigHandler {

    // 提供解析相关配置（ParserConfig详见3.3）
    ParserConfig getParserConfig();

    // 提供解析时间
    default Date getParseTime() {
        // ControllerData的createTime与该时间一致，生成的controllerId由Controoler类File的MD5与该时间组成
        // 默认new Date()
        return new Date();
    }

    // Controller类解析节点数据处理
    default void controllerNodeHandle(ControllerNode controllerNode) {
        // 解析完成一个Controller得到controllerNode会调用该方法
        // 默认不进行处理
    }

    // Controller类及其接口方法数据处理
    default void controllerDataHandle(ControllerData controllerData) {
        // 解析完成一个Controller得到controllerData会调用该方法
        // 默认不进行处理
    }

    // 参数验证注入处理
    default void paramValidInjectHandle(List<AnnotationNode> annotationNodeList, FieldInfo fieldInfo) {
        // annotationNodeList为该参数的注解集
        // fieldInfo为参数对象
        // 默认进行了部分注解的验证实现，可参考默认实现进行重写
    }

    // 参数默认值注入处理
    default void paramDefaultValueInjectHandle(List<AnnotationNode> annotationNodeList, FieldInfo fieldInfo) {
        // annotationNodeList为该参数的注解集
        // fieldInfo为参数对象
        // 默认注入部分参数类型的默认值，可参考默认实现进行重写
    }

    // 解析完成处理
    void parseFinishedHandle(List<ControllerData> controllerDataList);

}
```
### 3.4 ParserConfig
```java
// 解析配置类（以下属性均提供了add链式调用方法，参数也均为动态参数）
public class ParserConfig {

    // java源码绝对路径（必须写到java目录，且只能以java结尾，不带“/”）
    // 如果项目是多模块则将多模块的源码路径都加进去即可
    private Set<String> javaFilePaths;

    // 自定义识别为基础数据类型的类全名（默认已包含基础数据类型及其包装类和Big类，无需再添加），作用是遇到该类型时不再拆开解析其属性，认为是最后的类型
    private Set<String> lastValueTypeFullName;

    // 仅扫描解析该包集合下的controller类（必须位于javaFilePaths下，若不配置默认扫描javaFilePaths下所有）
    private Set<String> filterControllerPackages;

    // 仅扫描的controller类名集（非类全名，按照文件名称字符串匹配）
    private Set<String> filterControllerNames;

    // 需要排除的controller类名集（非类全名，按照文件名称字符串匹配）
    private Set<String> ignoreControllerNames;

}
```
## 4 答疑
### 4.1 如何用ClassParser解析任意类？
```java
public static void main(String[] args) {

    // 第一步：选其一添加java源码绝对路径（必须写到java目录，且只能以java结尾，不带“/”）
    ClassParser.addRootPath("");
    ClassParser.addRootPaths(new Set<String>());
    
    // 第二部：实例化抽象类ClassParser并调用其parse方法传入需要解析的java源码文件即可
    ClassNode classNode = new ClassParser(){}.parse(new File("F:/**/src/main/java/com/test/TestBean.java"));

    // 注意：如果不添加java源码绝对路径将无法解析该类内依赖的其它类
    
}
```
### 4.2 如何将FieldDataNode生成Json？
在`io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil`下提供了`toFormatJsonStr()`方法可将FieldDataNode生成Json，方式注释如下：
```java
package io.gitee.xuchenoak.limejapidocs.parser.util;

public class StringUtil {

    /**
     * FieldDataNode生成Json
     *
     * @param fieldDataNode   字段节点
     * @param startRetractNum 起始缩进空格数
     * @param retractNum      字段缩进空格数
     * @param hasComment      是否有注释
     * @param hasType         是否有类型
     * @param hasValid        是否有验证
     * @param isJson          是否为Json（key值是否加双引号）
     * @param addDefaultValue 是否填充默认值
     * @return 格式化后的Json
     */
    public static String toFormatJsonStr(FieldDataNode fieldDataNode, int startRetractNum, int retractNum, boolean hasComment, boolean hasType, boolean hasValid, boolean isJson, boolean addDefaultValue) {
       // ……
    }

}
```
## 5 更新记录

- 2023-01-01 V1.0.1发布
## 6 最后&致谢

1. 本项目的灵感源于`@YeDaxia`的项目 [JApiDocs](https://github.com/YeDaxia/JApiDocs)，它是一个可以解析Java源码并生成接口文档（支持生成html静态页或markdown等）的工具；
2. 本项目解析Java源码使用的是项目 [javaparser](http://javaparser.org/) 提供的解析器。