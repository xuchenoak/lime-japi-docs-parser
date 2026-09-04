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
import java.util.regex.Pattern;

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
            // 路径级粗滤：仅按包目录段/类全名相对路径缩小 controller 候选文件的 parse 范围（不读文件内容），
            // 结果必为 ControllerParser 精确校验的超集，被引用类（含内部嵌套类）仍经全量真实索引定位，不受影响
            javaFileList = filterFilesByConfig(javaFileList, parserConfigHandler.getParserConfig());

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
     * 绝对路径集转 File 列表
     */
    private static List<File> toFiles(Set<String> paths) {
        List<File> files = new ArrayList<>();
        for (String path : paths) {
            files.add(new File(path));
        }
        return files;
    }

    /**
     * 路径级粗滤待解析的 controller 候选文件（不读文件内容，仅按文件路径判断）：
     * 1. filterControllerPackages：无 * 按「包目录段」包含匹配（配置任意一级包命中该包及全部子包，段边界精确）；
     *    含 * 按「路径通配正则」（* 单段 / ** 多段）匹配，可出现在任意位置
     * 2. filterControllerNames / ignoreControllerNames：按「类全名转相对路径」后缀匹配（public controller 类名=文件名）
     * 语义：粗滤结果必为 ControllerParser 精确校验的超集——只缩小 parse 范围，不遗漏真正的 controller 文件；
     * 多余放行的反例文件由 ControllerParser 基于真实 AST 精确拒绝。本方法只读候选列表，不改动会话真实索引，
     * 因此 controller 引用的其它类（含内部嵌套类/DTO/VO/record）仍经全量真实索引定位，不受影响。
     *
     * @param javaFileList 全部 .java 候选文件
     * @param config       解析配置
     * @return 粗滤后的候选文件列表
     */
    private static List<File> filterFilesByConfig(List<File> javaFileList, ParserConfig config) {
        Set<String> filterControllerPackages = config.getFilterControllerPackages();
        Set<String> filterControllerNames = config.getFilterControllerNames();
        Set<String> ignoreControllerNames = config.getIgnoreControllerNames();
        boolean hasFilter = ListUtil.isNotBlank(filterControllerPackages)
                || ListUtil.isNotBlank(filterControllerNames)
                || ListUtil.isNotBlank(ignoreControllerNames);
        if (!hasFilter) {
            return javaFileList;
        }
        // 预转：无 * 包名 → 目录段（com.zwfw → com/zwfw）；含 * 包名 → 路径通配正则；类全名 → 相对路径（/com/zwfw/X.java）
        List<String> packageSegments = new ArrayList<>();
        List<Pattern> packagePathPatterns = new ArrayList<>();
        if (ListUtil.isNotBlank(filterControllerPackages)) {
            for (String p : filterControllerPackages) {
                if (p.contains("*")) {
                    packagePathPatterns.add(toPackagePathPattern(p));
                } else {
                    packageSegments.add(p.replace('.', '/'));
                }
            }
        }
        List<String> filterNameRelativePaths = toRelativePaths(filterControllerNames);
        List<String> ignoreNameRelativePaths = toRelativePaths(ignoreControllerNames);
        List<File> result = new ArrayList<>();
        for (File file : javaFileList) {
            String normalizedPath = file.getAbsolutePath().replace('\\', '/');
            if (ListUtil.isNotBlank(packageSegments) || ListUtil.isNotBlank(packagePathPatterns)) {
                boolean pkgHit = ListUtil.isNotBlank(packageSegments)
                        && packageSegments.stream().anyMatch(seg -> normalizedPath.contains("/" + seg + "/"));
                if (!pkgHit && ListUtil.isNotBlank(packagePathPatterns)) {
                    pkgHit = packagePathPatterns.stream().anyMatch(pat -> pat.matcher(normalizedPath).find());
                }
                if (!pkgHit) {
                    continue;
                }
            }
            if (ListUtil.isNotBlank(filterNameRelativePaths)) {
                boolean nameHit = filterNameRelativePaths.stream().anyMatch(normalizedPath::endsWith);
                if (!nameHit) {
                    continue;
                }
            }
            if (ListUtil.isNotBlank(ignoreNameRelativePaths)) {
                boolean ignored = ignoreNameRelativePaths.stream().anyMatch(normalizedPath::endsWith);
                if (ignored) {
                    continue;
                }
            }
            result.add(file);
        }
        return result;
    }

    /**
     * 包名模式转「路径通配正则」：包路径 pkg.replace('.','/') 后，* 匹配单个目录段（不含 /），** 匹配任意多段；
     * 段边界以 (^|/) 与尾部 / 锚定，用于在归一化绝对路径上 find 匹配（粗滤，保持精确结果超集）
     */
    private static Pattern toPackagePathPattern(String filterPackage) {
        String pkgPath = filterPackage.replace('.', '/');
        StringBuilder sb = new StringBuilder("(^|/)");
        for (int i = 0; i < pkgPath.length(); i++) {
            char c = pkgPath.charAt(i);
            if (c == '*') {
                if (i + 1 < pkgPath.length() && pkgPath.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i++;
                } else {
                    sb.append("[^/]+");
                }
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '/') {
                sb.append(c);
            } else {
                sb.append('\\').append(c);
            }
        }
        sb.append('/');
        return Pattern.compile(sb.toString());
    }

    /**
     * 类全名集转相对路径集（供路径级粗滤后缀匹配）
     */
    private static List<String> toRelativePaths(Set<String> fullNames) {
        List<String> relativePaths = new ArrayList<>();
        if (ListUtil.isNotBlank(fullNames)) {
            for (String fullName : fullNames) {
                String relativePath = ParseUtil.fullNameToRelativePath(fullName);
                if (StringUtil.isNotBlank(relativePath)) {
                    relativePaths.add(relativePath);
                }
            }
        }
        return relativePaths;
    }

}
