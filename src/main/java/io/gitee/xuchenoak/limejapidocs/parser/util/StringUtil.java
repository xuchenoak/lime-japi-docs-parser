package io.gitee.xuchenoak.limejapidocs.parser.util;


import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldDataNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldInfo;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串工具
 *
 * @author xuchenoak
 **/
public class StringUtil {

    public static boolean isBlank(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    private static final String BEAN_PREFIX = "{";

    private static final String BEAN_SUFFIX = "}";

    private static final String ARRAY_PREFIX = "[";

    private static final String ARRAY_SUFFIX = "]";

    private static final String ONE_RETRACT = " ";

    /**
     * 获取对象前缀或后缀
     *
     * @param fieldDataNode
     * @param prefix        true-前缀，false-后缀
     * @return
     */
    private static StringBuilder getPrefixOrSuffix(FieldDataNode fieldDataNode, boolean prefix) {
        StringBuilder str = new StringBuilder();
        if (fieldDataNode == null) {
            return str;
        }
        if (fieldDataNode.isArray()) {
            str.append(prefix ? ARRAY_PREFIX : ARRAY_SUFFIX);
            if (fieldDataNode.getChildFieldData() != null) {
                str.append(getPrefixOrSuffix(fieldDataNode.getChildFieldData(), prefix));
            }
        } else if (!fieldDataNode.isLastValue()) {
            str.append(prefix ? BEAN_PREFIX : BEAN_SUFFIX);
        }
        if (prefix && fieldDataNode.isLastValue() && isNotBlank(fieldDataNode.getLastValueType())) {
            str.append(fieldDataNode.getLastValueType());
        }
        return str;
    }

    /**
     * 获取对象前缀和后缀
     *
     * @param fieldDataNode
     * @return
     */
    private static String concatPrefixAndSuffix(FieldDataNode fieldDataNode) {
        if (fieldDataNode == null) {
            return null;
        }
        StringBuilder prefixStr = getPrefixOrSuffix(fieldDataNode, true);
        StringBuilder suffixStr = getPrefixOrSuffix(fieldDataNode, false).reverse();
        if (prefixStr.toString().endsWith(" ")) {
            prefixStr.append(suffixStr);
        } else {
            prefixStr.append("%s").append(suffixStr);
        }
        return prefixStr.toString();
    }

    /**
     * 获取最里层对象字段集
     *
     * @param fieldDataNode
     * @return
     */
    private static List<FieldInfo> getRealFieldInfoList(FieldDataNode fieldDataNode) {
        if (fieldDataNode.isArray()) {
            return getRealFieldInfoList(fieldDataNode.getChildFieldData());
        }
        return fieldDataNode.getFieldInfoList();
    }

    /**
     * 转Json格式化字符串
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
        if (fieldDataNode == null) {
            return null;
        }

        String startRetract = getRetract(startRetractNum);
        int fieldRetractNum = startRetractNum + retractNum;
        String fieldRetract = getRetract(fieldRetractNum);

        // 前缀后缀符号
        String prefixAndSuffix = concatPrefixAndSuffix(fieldDataNode);

        // 对象
        int flag = 0;
        StringBuilder str = new StringBuilder();
        List<FieldInfo> fieldInfoList = getRealFieldInfoList(fieldDataNode);
        if (ListUtil.isNotBlank(fieldInfoList)) {
            if (prefixAndSuffix.contains("%s")) {
                str.append("\n");
            }
            for (FieldInfo fieldInfo : fieldInfoList) {
                if (!fieldInfo.ok()) {
                    continue;
                }
                // 注释行：如果需要注释且有注释则加上注释
                if (hasComment) {
                    String comment = fieldInfo.getComment();
                    if (StringUtil.isNotBlank(comment)) {
                        str.append(fieldRetract)
                                .append("// ")
                                .append(comment);
                        if (hasType) {
                            String type = fieldInfo.getType();
                            if (StringUtil.isNotBlank(type)) {
                                str.append(" | ")
                                        .append(type);
                            }
                        }
                        if (hasValid) {
                            String valid = fieldInfo.getValidation();
                            if (StringUtil.isNotBlank(valid)) {
                                str.append(" | ")
                                        .append(valid);
                            }
                        }
                        str.append("\n");
                    }
                }
                // 字段key
                str.append(fieldRetract)
                        .append(isJson ? "\"" : "")
                        .append(fieldInfo.getName())
                        .append(isJson ? "\"" : "")
                        .append(": ");
                // 字段value
                if (!fieldInfo.getValueFieldData().isLastValue()) {
                    str.append(toFormatJsonStr(fieldInfo.getValueFieldData(), fieldRetractNum, retractNum, hasComment, hasType, hasValid, isJson, addDefaultValue));
                } else {
                    str.append(addDefaultValue && StringUtil.isNotBlank(fieldInfo.getDefaultValue()) ? fieldInfo.getDefaultValue() : null);
                }
                if (++flag == fieldInfoList.size()) {
                    continue;
                }
                str.append(",\n");
            }
        }
        if (prefixAndSuffix.contains("%s") && StringUtil.isNotBlank(str.toString())) {
            str.append("\n").append(startRetract);
        }
        return String.format(prefixAndSuffix, str);
    }


    /**
     * 获取缩进空格
     *
     * @param retract 缩进数
     * @return 空格
     */
    public static String getRetract(int retract) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < retract; i++) {
            str.append(ONE_RETRACT);
        }
        return str.toString();
    }


    /**
     * 下划线转驼峰
     *
     * @param lineStr    源字符串
     * @param smallCamel 是否为小驼峰
     * @return 转化后的字符串
     */
    public static String underlineToCamel(String lineStr, boolean smallCamel) {
        if (lineStr == null || "".equals(lineStr)) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        Pattern pattern = Pattern.compile("([A-Za-z\\d]+)(_)?");
        Matcher matcher = pattern.matcher(lineStr);
        while (matcher.find()) {
            String word = matcher.group();
            sb.append(smallCamel && matcher.start() == 0 ? Character.toLowerCase(word.charAt(0)) : Character.toUpperCase(word.charAt(0)));
            int index = word.lastIndexOf('_');
            if (index > 0) {
                sb.append(word.substring(1, index).toLowerCase());
            } else {
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /**
     * 驼峰转下划线
     *
     * @param camelStr 源字符串
     * @return 转化后的字符串
     */
    public static String camelToUnderline(String camelStr) {
        if (camelStr == null || "".equals(camelStr)) {
            return "";
        }
        camelStr = String.valueOf(camelStr.charAt(0)).toUpperCase().concat(camelStr.substring(1));
        StringBuffer sb = new StringBuffer();
        Pattern pattern = Pattern.compile("[A-Z]([a-z\\d]+)?");
        Matcher matcher = pattern.matcher(camelStr);
        while (matcher.find()) {
            String word = matcher.group();
            sb.append(word.toUpperCase());
            sb.append(matcher.end() == camelStr.length() ? "" : "_");
        }
        return sb.toString();
    }

    /**
     * 获取随机字符串
     *
     * @param length          长度
     * @param leftJoin        左边拼接
     * @param rightJoin       右边拼接
     * @param prefixAndSuffix 是否前后拼接双引号
     * @return 随机字符串
     */
    public static String getRandomString(int length, String leftJoin, String rightJoin, boolean prefixAndSuffix) {
        // 定义一个字符串（A-Z，a-z，0-9）即62位；
        String str = "zxcvbnmlkjhgfdsaqwertyuiopQWERTYUIOPASDFGHJKLZXCVBNM1234567890";
        // 由Random生成随机数
        Random random = new Random();
        StringBuilder builder = new StringBuilder(prefixAndSuffix ? "\"" : "");
        if (StringUtil.isNotBlank(leftJoin)) {
            builder.append(leftJoin);
        }
        // 长度为几就循环几次
        for (int i = 0; i < length; ++i) {
            // 产生0-61的数字
            int number = random.nextInt(62);
            // 将产生的数字通过length次承载到builder中
            builder.append(str.charAt(number));
        }
        if (isNotBlank(rightJoin)) {
            builder.append(rightJoin);
        }
        if (prefixAndSuffix) {
            builder.append("\"");
        }
        // 将承载的字符转换成字符串
        return builder.toString();
    }

}
