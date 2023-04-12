package io.gitee.xuchenoak.limejapidocs.parser.config;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * 解析配置类
 *
 * @author xuchenoak
 */
@Getter
public class ParserConfig {

    /**
     * java文件所在目录绝对路径（必须写到java目录）
     */
    private Set<String> javaFilePaths;

    /**
     * 自定义识别为基础数据类型的类全名
     */
    private Set<String> lastValueTypeFullName;

    /**
     * 仅扫描解析该包集合下的controller类（必须位于javaFilePaths下，若不配置默认扫描javaFilePaths下所有）
     */
    private Set<String> filterControllerPackages;

    /**
     * 仅扫描的controller类名集（非类全名）
     */
    private Set<String> filterControllerNames;

    /**
     * 需要排除的controller类名集（非类全名）
     */
    private Set<String> ignoreControllerNames;

    public ParserConfig addJavaFilePath(String... paths) {
        if (javaFilePaths == null) {
            javaFilePaths = new HashSet<>();
        }
        return inject(javaFilePaths, paths);
    }

    public ParserConfig addLastValueTypeFullName(String... fullNames) {
        if (lastValueTypeFullName == null) {
            lastValueTypeFullName = new HashSet<>();
        }
        return inject(lastValueTypeFullName, fullNames);
    }

    public ParserConfig addFilterControllerPackage(String... packages) {
        if (filterControllerPackages == null) {
            filterControllerPackages = new HashSet<>();
        }
        return inject(filterControllerPackages, packages);
    }

    public ParserConfig addFilterControllerName(String... names) {
        if (filterControllerNames == null) {
            filterControllerNames = new HashSet<>();
        }
        return inject(filterControllerNames, names);
    }

    public ParserConfig addIgnoreControllerName(String... names) {
        if (ignoreControllerNames == null) {
            ignoreControllerNames = new HashSet<>();
        }
        return inject(ignoreControllerNames, names);
    }

    private ParserConfig inject(Set<String> target, String... source) {
        if (target != null && source != null && source.length > 0) {
            for (String s : source) {
                target.add(s);
            }
        }
        return this;
    }

    private ParserConfig(){}

    public static ParserConfig build(String path, String... paths) {
        return new ParserConfig().addJavaFilePath(path).addJavaFilePath(paths);
    }

}
