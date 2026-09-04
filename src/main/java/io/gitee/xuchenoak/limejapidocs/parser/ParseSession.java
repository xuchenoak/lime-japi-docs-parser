package io.gitee.xuchenoak.limejapidocs.parser;

import cn.hutool.core.io.FileUtil;
import io.gitee.xuchenoak.limejapidocs.parser.basenode.ClassNode;
import io.gitee.xuchenoak.limejapidocs.parser.util.ListUtil;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析会话：承载单次解析运行的全部可变状态（root路径集、真实java文件索引、类模板缓存、解析嵌套深度）。
 * 每次解析应使用独立会话，会话生命周期即解析运行生命周期——运行结束失去引用即可被回收。
 * 会话非线程安全，不要在多线程间共享同一实例；不同实例（会话）天然隔离、互不干扰。
 *
 * @author xuchenoak
 **/
public class ParseSession {

    private static final Logger logger = LoggerFactory.getLogger(ParseSession.class);

    /**
     * 构造一个全新的解析会话（root路径集、真实java文件索引、类模板缓存均为空）
     */
    public ParseSession() {
    }

    /**
     * java文件所在目录集（任意深度目录或单个java文件均可）
     */
    private final Set<String> rootPaths = new LinkedHashSet<>();

    /**
     * 真实java文件绝对路径索引：初始化时由 root 目录递归扫描收集，
     * 解析引用类时按「类全名拼相对路径」后缀匹配定位文件，避免磁盘探测
     */
    private final Set<String> realJavaFilePaths = new LinkedHashSet<>();

    /**
     * 按文件名分组的绝对路径二级索引（文件名如 User.java -> 候选绝对路径列表），
     * 供 findJavaFileByRelativePath 由 O(n) 全量遍历降为 O(1) 取候选后精确匹配
     */
    private final Map<String, List<String>> javaFilePathIndexByFileName = new HashMap<>();

    /**
     * 类节点模板缓存（root集|类全名 -> ClassNode），单次解析窗口内共享已解析类模板
     */
    private final java.util.Map<String, ClassNode> classNodeCache = new java.util.HashMap<>();

    /**
     * 当前解析嵌套深度，防止过深的类依赖链导致栈溢出
     */
    private int parseDepth = 0;

    /**
     * 缓存key的root集排序后缀（root集合变化时失效重建）
     */
    private String sortedRootKey;

    /**
     * 添加root路径（校验通过才加入）
     * 支持任意深度目录：若为目录则递归扫描其下所有 .java 文件进入真实文件索引；
     * 若为单个 .java 文件则直接加入索引。目录无需以 java 结尾。
     *
     * @param rootPath java源码目录绝对路径（任意深度）或单个 .java 文件绝对路径
     */
    public void addRootPath(String rootPath) {
        if (StringUtil.isBlank(rootPath)) {
            return;
        }
        File file = new File(rootPath);
        if (file.isFile() && rootPath.endsWith(".java")) {
            rootPaths.add(rootPath);
            addToIndex(file);
            sortedRootKey = null;
            return;
        }
        if (!file.isDirectory()) {
            logger.info("rootPath：{}不存在或非目录，已忽略", rootPath);
            return;
        }
        rootPaths.add(rootPath);
        sortedRootKey = null;
        List<File> javaFiles = FileUtil.loopFiles(file, f -> f.isFile() && f.getName().endsWith(".java"));
        if (ListUtil.isNotBlank(javaFiles)) {
            for (File javaFile : javaFiles) {
                realJavaFilePaths.add(javaFile.getAbsolutePath());
                addToIndex(javaFile);
            }
        }
    }

    /**
     * 将 java 文件绝对路径登记进按文件名分组的二级索引
     */
    private void addToIndex(File javaFile) {
        String absolutePath = javaFile.getAbsolutePath();
        realJavaFilePaths.add(absolutePath);
        String fileName = javaFile.getName();
        List<String> candidates = javaFilePathIndexByFileName.computeIfAbsent(fileName, k -> new ArrayList<>());
        candidates.add(absolutePath);
    }

    /**
     * 批量添加root路径
     *
     * @param rootPaths java源码目录绝对路径集（任意深度）
     */
    public void addRootPaths(Set<String> rootPaths) {
        if (rootPaths == null || rootPaths.size() == 0) {
            return;
        }
        for (String rootPath : rootPaths) {
            addRootPath(rootPath);
        }
    }

    /**
     * 按「类全名拼出的相对路径」在真实文件索引中后缀匹配定位真实java文件
     * 相对路径形如 /io/gitee/sample/dto/User.java（含包路径，天然唯一），
     * 因此只需找出以该相对路径结尾的绝对路径即可，跨模块、任意目录深度均成立
     * （匹配时统一把文件分隔符归一化为 /，兼容 Windows 反斜杠路径）
     *
     * @param relativePath 相对路径（类全名转出）
     * @return 匹配的真实java文件；未命中返回 null
     */
    public File findJavaFileByRelativePath(String relativePath) {
        if (StringUtil.isBlank(relativePath)) {
            return null;
        }
        String normalizedRel = relativePath.replace('\\', '/');
        int slash = normalizedRel.lastIndexOf('/');
        String fileName = slash >= 0 ? normalizedRel.substring(slash + 1) : normalizedRel;
        List<String> candidates = javaFilePathIndexByFileName.get(fileName);
        if (candidates == null) {
            return null;
        }
        for (String absolutePath : candidates) {
            if (absolutePath.replace('\\', '/').endsWith(normalizedRel)) {
                return new File(absolutePath);
            }
        }
        return null;
    }

    /**
     * 获取真实文件索引中的所有 .java 绝对路径（只读副本）
     *
     * @return 真实java文件绝对路径集
     */
    public Set<String> getRealJavaFilePaths() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(realJavaFilePaths));
    }

    /**
     * 清空root路径集
     */
    public void clearRootPaths() {
        rootPaths.clear();
        realJavaFilePaths.clear();
        javaFilePathIndexByFileName.clear();
        sortedRootKey = null;
    }

    /**
     * 获取当前会话root路径集（不可修改副本）
     *
     * @return 本会话已登记的root路径集
     */
    public Set<String> getRootPaths() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(rootPaths));
    }

    /**
     * 清空类模板缓存
     */
    public void clearCache() {
        classNodeCache.clear();
    }

    /**
     * 获取缓存中的类模板数量
     *
     * @return 当前会话缓存条目数
     */
    public int getCacheSize() {
        return classNodeCache.size();
    }

    /**
     * 校验root路径
     * 新语义：路径非空且为已存在的目录或 .java 文件时返回 true（任意深度目录均可，无需以 java 结尾）
     *
     * @param rootPath 待校验的root路径
     * @return 路径为已存在的目录或 .java 文件时返回 true
     */
    public static boolean isValidRootPath(String rootPath) {
        if (StringUtil.isBlank(rootPath)) {
            return false;
        }
        File file = new File(rootPath);
        if (file.isDirectory()) {
            return true;
        }
        if (file.isFile() && rootPath.endsWith(".java")) {
            return true;
        }
        logger.info("rootPath：{}不存在或非目录，已忽略", rootPath);
        return false;
    }

    /**
     * 构建缓存key（root集排序后缀惰性缓存，与类全名共同决定）
     */
    String cacheKey(String fullName) {
        if (sortedRootKey == null) {
            List<String> sortedRoots = new ArrayList<>(rootPaths);
            Collections.sort(sortedRoots);
            sortedRootKey = String.join(";", sortedRoots);
        }
        return fullName + "|" + sortedRootKey;
    }

    /**
     * 取缓存模板
     */
    ClassNode getCachedClassNode(String fullName) {
        return classNodeCache.get(cacheKey(fullName));
    }

    /**
     * 缓存类模板
     */
    void cacheClassNode(ClassNode node) {
        if (node != null && StringUtil.isNotBlank(node.getFullName())) {
            classNodeCache.put(cacheKey(node.getFullName()), node);
        }
    }

    /**
     * 获取当前解析嵌套深度
     *
     * @return 解析嵌套深度
     */
    int getParseDepth() {
        return parseDepth;
    }

    /**
     * 设置当前解析嵌套深度
     *
     * @param parseDepth 解析嵌套深度
     */
    void setParseDepth(int parseDepth) {
        this.parseDepth = parseDepth;
    }
}