package io.gitee.xuchenoak.limejapidocs.parser;

import io.gitee.xuchenoak.limejapidocs.parser.bean.ControllerData;
import io.gitee.xuchenoak.limejapidocs.parser.bean.InterfaceData;
import io.gitee.xuchenoak.limejapidocs.parser.config.ParserConfig;
import io.gitee.xuchenoak.limejapidocs.parser.handler.ParserConfigHandler;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldDataNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldInfo;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the parentNodeNameMap backtracking fix:
 * a class registered by one method's return type must NOT wrongly truncate a
 * sibling method's return-type graph (and sibling fields of the same type).
 *
 * @author xuchenoak
 **/
public class ScopeServiceRegressionTest {

    private static final String FIXTURE_ROOT = new File("src/test/resources/fixtures/scopeservice/src/main/java").getAbsolutePath();

    @Test
    public void siblingMethodReturnTypesDoNotTruncateEachOther() {
        List<ControllerData> collector = new ArrayList<>();
        LimeJapiDocsParser.build(new ParserConfigHandler() {
            @Override
            public ParserConfig getParserConfig() {
                return ParserConfig.build(FIXTURE_ROOT);
            }

            @Override
            public void parseFinishedHandle(List<ControllerData> controllerDataList) {
                collector.addAll(controllerDataList);
            }
        });
        ControllerData scope = collector.stream()
                .filter(c -> "com.scope.test.controller.ScopeController".equals(c.getControllerFullName()))
                .findFirst().orElse(null);
        assertNotNull(scope);
        InterfaceData options = scope.getInterfaceDataList().stream()
                .filter(i -> "options".equals(i.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(options);

        // AjaxResult<T> -> data -> WrapperVO -> available: List<LeafVO>
        FieldDataNode resData = options.getResData();
        assertNotNull(resData);
        FieldInfo data = fieldOf(resData.getFieldInfoList(), "data");
        assertNotNull(data);
        FieldDataNode wrapper = data.getValueFieldData();
        assertNotNull(wrapper);
        FieldInfo available = fieldOf(wrapper.getFieldInfoList(), "available");
        assertNotNull(available);
        // 不再被截断为 [{}]：注释不应出现「结构同字段」
        assertFalse(available.getComment() != null && available.getComment().contains("结构同字段"),
                "available 不应被误判为递归父节点截断，实际注释：" + available.getComment());
        FieldDataNode availableArray = available.getValueFieldData();
        assertNotNull(availableArray);
        assertTrue(availableArray.isArray());
        FieldDataNode leaf = availableArray.getChildFieldData();
        assertNotNull(leaf);
        assertFalse(leaf.isLastValue());
        List<FieldInfo> leafFields = leaf.getFieldInfoList();
        assertNotNull(leafFields);
        assertTrue(leafFields.stream().anyMatch(f -> "leafId".equals(f.getName())), "LeafVO.leafId 应被展开");
        assertTrue(leafFields.stream().anyMatch(f -> "leafName".equals(f.getName())), "LeafVO.leafName 应被展开");
        assertTrue(leafFields.stream().anyMatch(f -> "selectable".equals(f.getName())), "LeafVO.selectable 应被展开");
    }

    @Test
    public void siblingFieldsOfSameTypeBothExpand() {
        // PairHolder 作为接口返回类型被解析（子节点解析路径），其两个同类型字段都必须展开
        List<ControllerData> collector = new ArrayList<>();
        LimeJapiDocsParser.build(new ParserConfigHandler() {
            @Override
            public ParserConfig getParserConfig() {
                return ParserConfig.build(FIXTURE_ROOT);
            }

            @Override
            public void parseFinishedHandle(List<ControllerData> controllerDataList) {
                collector.addAll(controllerDataList);
            }
        });
        ControllerData scope = collector.stream()
                .filter(c -> "com.scope.test.controller.ScopeController".equals(c.getControllerFullName()))
                .findFirst().orElse(null);
        assertNotNull(scope);
        InterfaceData pair = scope.getInterfaceDataList().stream()
                .filter(i -> "pair".equals(i.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(pair);
        FieldDataNode resData = pair.getResData();
        assertNotNull(resData);
        assertFalse(resData.isLastValue());

        FieldInfo first = fieldOf(resData.getFieldInfoList(), "first");
        assertNotNull(first);
        FieldDataNode firstValue = first.getValueFieldData();
        assertNotNull(firstValue);
        assertFalse(firstValue.isLastValue());
        assertTrue(firstValue.getFieldInfoList().stream().anyMatch(f -> "key".equals(f.getName())),
                "first 应展开为 PairLeaf");

        FieldInfo second = fieldOf(resData.getFieldInfoList(), "second");
        assertNotNull(second);
        FieldDataNode secondValue = second.getValueFieldData();
        assertNotNull(secondValue);
        assertFalse(secondValue.isLastValue());
        assertTrue(secondValue.getFieldInfoList().stream().anyMatch(f -> "label".equals(f.getName())),
                "second 应展开为 PairLeaf");
        assertTrue(secondValue.getFieldInfoList().stream().anyMatch(f -> "key".equals(f.getName())),
                "second 应展开为 PairLeaf");
    }

    private List<ControllerData> buildCollect(Consumer<ParserConfig> tweak) {
        List<ControllerData> collector = new ArrayList<>();
        LimeJapiDocsParser.build(new ParserConfigHandler() {
            @Override
            public ParserConfig getParserConfig() {
                ParserConfig config = ParserConfig.build(FIXTURE_ROOT);
                if (tweak != null) {
                    tweak.accept(config);
                }
                return config;
            }

            @Override
            public void parseFinishedHandle(List<ControllerData> controllerDataList) {
                collector.addAll(controllerDataList);
            }
        });
        return collector;
    }

    @Test
    public void wildcardDoubleStarControllerSuffix() {
        // **.controller：任意多段前缀 + .controller 结尾，跨包全部命中
        List<ControllerData> list = buildCollect(cfg -> cfg.addFilterControllerPackage("**.controller"));
        assertEquals(2, list.size());
    }

    @Test
    public void wildcardSingleSegmentMatchesDirectSubpackageOnly() {
        // com.scope.test.*：仅直接子包（* 单段），com.scope.test.sub.controller（更深）不命中
        List<ControllerData> list = buildCollect(cfg -> cfg.addFilterControllerPackage("com.scope.test.*"));
        assertEquals(1, list.size());
        assertEquals("com.scope.test.controller.ScopeController", list.get(0).getControllerFullName());
    }

    @Test
    public void wildcardDoubleStarMatchesAllSubpackages() {
        // com.scope.test.**：所有子包
        List<ControllerData> list = buildCollect(cfg -> cfg.addFilterControllerPackage("com.scope.test.**"));
        assertEquals(2, list.size());
    }

    @Test
    public void wildcardMidSingleSegment() {
        // com.scope.test.*.controller：中间单段，仅 com.scope.test.sub.controller 命中（4 段的 ...controller 不匹配）
        List<ControllerData> list = buildCollect(cfg -> cfg.addFilterControllerPackage("com.scope.test.*.controller"));
        assertEquals(1, list.size());
        assertEquals("com.scope.test.sub.controller.DeepScopeController", list.get(0).getControllerFullName());
    }

    @Test
    public void wildcardMidDoubleStar() {
        // com.scope.test.**.controller：** 匹配任意多层中间段。零中间段的 com.scope.test.controller
        // 不匹配（** 空段会产生 com.scope.test..controller 双点，非法包名），仅 com.scope.test.sub.controller 命中
        List<ControllerData> list = buildCollect(cfg -> cfg.addFilterControllerPackage("com.scope.test.**.controller"));
        assertEquals(1, list.size());
        assertEquals("com.scope.test.sub.controller.DeepScopeController", list.get(0).getControllerFullName());
    }

    private FieldInfo fieldOf(List<FieldInfo> fieldInfoList, String name) {
        if (fieldInfoList == null) {
            return null;
        }
        return fieldInfoList.stream().filter(f -> name.equals(f.getName())).findFirst().orElse(null);
    }
}