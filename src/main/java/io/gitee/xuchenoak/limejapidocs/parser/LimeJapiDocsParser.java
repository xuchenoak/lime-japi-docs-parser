package io.gitee.xuchenoak.limejapidocs.parser;

import io.gitee.xuchenoak.limejapidocs.parser.bean.ControllerData;
import io.gitee.xuchenoak.limejapidocs.parser.config.ParserConfig;
import io.gitee.xuchenoak.limejapidocs.parser.exception.CustomException;
import io.gitee.xuchenoak.limejapidocs.parser.handler.ParserConfigHandler;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.ControllerNode;
import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.ParseUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
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
        // 每次解析建立独立会话：本次解析新增的所有状态（root、真实文件索引、模板缓存、嵌套深度）均存于会话内，
        // 方法结束会话即失去引用被回收，服务进程无驻留、多线程调用互不干扰
        ParseSession session = new ParseSession();
        try {
            if (parserConfigHandler == null) {
                throw CustomException.instance("ParserConfigHandler为空");
            }
            if (parserConfigHandler.getParserConfig() == null) {
                throw CustomException.instance("ParserConfig为空");
            }
            Set<String> javaFileDirs = parserConfigHandler.getParserConfig().getJavaFilePaths();
            if (ListUtil.isBlank(javaFileDirs)) {
                throw CustomException.instance("未配置Java源码路径");
            }
            // 初始化：配置目录（任意深度）递归扫描，建立真实java文件索引
            session.addRootPaths(javaFileDirs);
            if (session.getRealJavaFilePaths().isEmpty()) {
                throw CustomException.instance("未找到可解析.java文件");
            }
            List<File> javaFileList = toFiles(session.getRealJavaFilePaths());
            // 三字段统一在文件索引层过滤（build 阶段）
            javaFileList = filterByConfig(javaFileList, parserConfigHandler.getParserConfig());

            List<ControllerData> controllerDataList = new ArrayList<>();
            int sort = 1;
            for (File file : javaFileList) {
                ControllerNode controllerNode = ControllerParser.createParser(parserConfigHandler, session)
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
        } finally {
            // 会话释放：清空内部状态并释放线程局部解析器，仅剩局部引用随即可被GC回收
            session.clearCache();
            session.clearRootPaths();
            ClassParser.removeJavaParser();
        }
    }

    /**
     * 三字段统一在文件索引层过滤待解析文件：
     * 1. filterControllerPackages（仅扫描包，按顶层类型包名匹配）；过滤后为空且配置非空则判定包不存在
     * 2. filterControllerNames（仅扫描类全名）
     * 3. ignoreControllerNames（排除类全名）
     * 仅当任一过滤配置存在时才轻量读取文件头解析顶层类全名，避免无谓开销
     *
     * @param javaFileList 候选文件集
     * @param config       解析配置
     * @return 过滤后的文件集
     */
    private static List<File> filterByConfig(List<File> javaFileList, ParserConfig config) {
        Set<String> filterControllerPackages = config.getFilterControllerPackages();
        Set<String> filterControllerNames = config.getFilterControllerNames();
        Set<String> ignoreControllerNames = config.getIgnoreControllerNames();
        boolean hasFilter = ListUtil.isNotBlank(filterControllerPackages)
                || ListUtil.isNotBlank(filterControllerNames)
                || ListUtil.isNotBlank(ignoreControllerNames);
        if (!hasFilter) {
            return javaFileList;
        }
        List<File> result = new ArrayList<>();
        for (File file : javaFileList) {
            String fullName = ParseUtil.readTopLevelFullName(file);
            if (StringUtil.isBlank(fullName)) {
                continue;
            }
            if (ListUtil.isNotBlank(filterControllerPackages)) {
                String packageName = fullName.substring(0, fullName.lastIndexOf("."));
                if (!filterControllerPackages.contains(packageName)) {
                    continue;
                }
            }
            if (ListUtil.isNotBlank(filterControllerNames) && !filterControllerNames.contains(fullName)) {
                continue;
            }
            if (ListUtil.isNotBlank(ignoreControllerNames) && ignoreControllerNames.contains(fullName)) {
                continue;
            }
            result.add(file);
        }
        // 仅扫描包配置存在但一个文件都未命中 → 判定指定包路径不存在
        if (ListUtil.isNotBlank(filterControllerPackages) && ListUtil.isBlank(result)) {
            throw CustomException.instance("指定解析的controller包路径不存在");
        }
        return result;
    }

    /**
     * 绝对路径集转 File 列表
     */
    private static List<File> toFiles(Set<String> paths) {
        List<File> files = new ArrayList<>();
        for (String path : paths) {
            files.add(new File(path));
        }
        return files;
    }

}
