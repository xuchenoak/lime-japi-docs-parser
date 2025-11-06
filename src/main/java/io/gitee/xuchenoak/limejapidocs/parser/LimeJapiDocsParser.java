package io.gitee.xuchenoak.limejapidocs.parser;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import io.gitee.xuchenoak.limejapidocs.parser.bean.ControllerData;
import io.gitee.xuchenoak.limejapidocs.parser.handler.ParserConfigHandler;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.ControllerNode;
import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析调用入口
 *
 * @author xuchenoak
 **/
public class LimeJapiDocsParser {

    private static final Logger logger = LoggerFactory.getLogger(LimeJapiDocsParser.class);

    /**
     * 构建接口文档数据
     *
     * @param parserConfigHandler 解析配置控制类
     */
    public static void build(ParserConfigHandler parserConfigHandler) {
        if (parserConfigHandler == null) {
            throw new RuntimeException("ParserConfigHandler为空");
        }
        if (parserConfigHandler.getParserConfig() == null) {
            throw new RuntimeException("ParserConfig为空");
        }
        Set<String> javaFileDirs = parserConfigHandler.getParserConfig().getJavaFilePaths();
        if (ListUtil.isBlank(javaFileDirs)) {
            throw new RuntimeException("未配置Java源码路径");
        }
        ClassParser.addRootPaths(javaFileDirs);
        Set<String> filterControllerPackages = parserConfigHandler.getParserConfig().getFilterControllerPackages();
        if (ListUtil.isNotBlank(filterControllerPackages)) {
            javaFileDirs = packageToFileDir(javaFileDirs, filterControllerPackages);
            if (ListUtil.isBlank(javaFileDirs)) {
                throw new RuntimeException("指定解析的controller包路径不存在");
            }
        }
        List<File> javaFileList = new ArrayList<>();
        for (String javaFileDir : javaFileDirs) {
            List<File> files = getJavaFileListByDir(javaFileDir);
            if (ListUtil.isBlank(files)) {
                logger.info("该目录下无.java文件：{}", javaFileDir);
                continue;
            }
            javaFileList.addAll(files);
        }
        if (ListUtil.isBlank(javaFileList)) {
            throw new RuntimeException("未找到可解析.java文件");
        }
        List<ControllerData> controllerDataList = new ArrayList<>();
        int sort = 1;
        for (File file : javaFileList) {
            ControllerNode controllerNode = ControllerParser.createParser(parserConfigHandler)
                    .parse(file);
            if (controllerNode == null) {
                continue;
            }
            ControllerData controllerData = controllerNode.getControllerData();
            if (controllerData == null) {
                continue;
            }
            controllerData.setSort(sort);
            controllerData.setCreateTime(parserConfigHandler.getParseTime());
            controllerDataList.add(controllerData);
            parserConfigHandler.controllerDataHandle(controllerData);
            parserConfigHandler.controllerNodeHandle(controllerNode);
            logger.info("\n成功解析-{}：{}", sort, controllerNode.getFullName());
            sort++;
        }
        logger.info("解析完成！共解析了{}个Controller类", controllerDataList.size());
        parserConfigHandler.parseFinishedHandle(controllerDataList);
    }

    /**
     * 遍历获取Java文件
     *
     * @param dir
     * @return
     */
    private static List<File> getJavaFileListByDir(String dir) {
        return FileUtil.loopFiles(dir, file -> file != null && file.exists() && file.getName().endsWith(".java"));
    }

    /**
     * 包转路径
     *
     * @param javaFileDirs 源路径集
     * @param packages     包名集
     * @return
     */
    private static Set<String> packageToFileDir(Set<String> javaFileDirs, Set<String> packages) {
        if (ListUtil.isBlank(javaFileDirs) || ListUtil.isBlank(packages)) {
            return null;
        }
        Set<String> set = new HashSet<>();
        for (String javaFileDir : javaFileDirs) {
            for (String p : packages) {
                String dir = StrUtil.format("{}/{}", javaFileDir, p.replace(".", "/"));
                if (FileUtil.exist(dir)) {
                    set.add(dir);
                }
            }
        }
        return set;
    }

}
