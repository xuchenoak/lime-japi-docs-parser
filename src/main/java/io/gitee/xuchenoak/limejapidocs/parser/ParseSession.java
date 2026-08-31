package io.gitee.xuchenoak.limejapidocs.parser;

import io.gitee.xuchenoak.limejapidocs.parser.basenode.ClassNode;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析会话：承载单次解析运行的全部可变状态（root路径集、类模板缓存、解析嵌套深度）。
 * 每次解析应使用独立会话，会话生命周期即解析运行生命周期——运行结束失去引用即可被回收。
 * 会话非线程安全，不要在多线程间共享同一实例；不同实例（会话）天然隔离、互不干扰。
 *
 * @author xuchenoak
 **/
public class ParseSession {

    private static final Logger logger = LoggerFactory.getLogger(ParseSession.class);

    /**
     * 构造一个全新的解析会话（root路径集、类模板缓存均为空）
     */
    public ParseSession() {
    }

    /**
     * java文件所在包路径集（必须到java文件夹）
     */
    private final Set<String> rootPaths = new LinkedHashSet<>();

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
     *
     * @param rootPath java源码绝对路径，必须以 java 结尾、不带尾部 /
     */
    public void addRootPath(String rootPath) {
        if (!isValidRootPath(rootPath)) {
            return;
        }
        rootPaths.add(rootPath);
        sortedRootKey = null;
    }

    /**
     * 批量添加root路径
     *
     * @param rootPaths java源码绝对路径集
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
     * 获取当前会话root路径集（不可修改副本）
     *
     * @return 本会话已登记的root路径集
     */
    public Set<String> getRootPaths() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(rootPaths));
    }

    /**
     * 清空root路径集
     */
    public void clearRootPaths() {
        rootPaths.clear();
        sortedRootKey = null;
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
     *
     * @param rootPath 待校验的root路径
     * @return 路径以 java 结尾且非空时返回 true
     */
    public static boolean isValidRootPath(String rootPath) {
        if (StringUtil.isNotBlank(rootPath) && rootPath.endsWith("java")) {
            return true;
        }
        logger.info("rootPath：{}不符合要求，请保持路径末尾为“java”，无需加“/”或“\\”", rootPath);
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