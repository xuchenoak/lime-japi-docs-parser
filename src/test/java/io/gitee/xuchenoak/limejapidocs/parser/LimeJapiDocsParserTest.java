package io.gitee.xuchenoak.limejapidocs.parser;

import io.gitee.xuchenoak.limejapidocs.parser.bean.ControllerData;
import io.gitee.xuchenoak.limejapidocs.parser.bean.InterfaceData;
import io.gitee.xuchenoak.limejapidocs.parser.config.ParserConfig;
import io.gitee.xuchenoak.limejapidocs.parser.handler.ParserConfigHandler;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldDataNode;
import io.gitee.xuchenoak.limejapidocs.parser.parsendoe.FieldInfo;
import io.gitee.xuchenoak.limejapidocs.parser.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end behavior tests over the sample fixture project.
 *
 * @author xuchenoak
 **/
public class LimeJapiDocsParserTest {

    private static final String FIXTURE_ROOT = new File("src/test/resources/fixtures/sample/src/main/java").getAbsolutePath();

    private static final String USER_CONTROLLER = "io.gitee.sample.controller.UserController";
    private static final String EXTENDS_CONTROLLER = "io.gitee.sample.controller.ExtendsController";

    private List<ControllerData> build(boolean parseFirstParent, Consumer<ParserConfig> cfgTweak) {
        List<ControllerData> collector = new ArrayList<>();
        LimeJapiDocsParser.build(new ParserConfigHandler() {
            @Override
            public ParserConfig getParserConfig() {
                ParserConfig config = ParserConfig.build(FIXTURE_ROOT);
                if (cfgTweak != null) {
                    cfgTweak.accept(config);
                }
                return config;
            }

            @Override
            public boolean isParseControllerFirstParent() {
                return parseFirstParent;
            }

            @Override
            public void parseFinishedHandle(List<ControllerData> controllerDataList) {
                collector.addAll(controllerDataList);
            }
        });
        return collector;
    }

    private List<ControllerData> build() {
        return build(false, null);
    }

    private ControllerData controllerOf(List<ControllerData> list, String fullName) {
        return list.stream().filter(c -> fullName.equals(c.getControllerFullName())).findFirst().orElse(null);
    }

    private InterfaceData interfaceOf(ControllerData controller, String methodName) {
        return controller.getInterfaceDataList().stream()
                .filter(i -> methodName.equals(i.getMethodName())).findFirst().orElse(null);
    }

    private Set<String> interfaceUriSet(ControllerData controller) {
        Set<String> uris = new HashSet<>();
        if (controller.getInterfaceDataList() != null) {
            controller.getInterfaceDataList().forEach(i -> uris.addAll(i.getUriList()));
        }
        return uris;
    }

    @Test
    public void build_collectsOnlyControllers() {
        List<ControllerData> list = build();
        assertEquals(2, list.size());
        Set<String> fullNames = new HashSet<>();
        list.forEach(c -> fullNames.add(c.getControllerFullName()));
        assertTrue(fullNames.contains(USER_CONTROLLER));
        assertTrue(fullNames.contains(EXTENDS_CONTROLLER));
        assertTrue(fullNames.size() == 2);
        assertFalse(fullNames.contains("io.gitee.sample.svc.NonControllerService"));
        assertFalse(fullNames.contains("io.gitee.sample.dto.User"));
    }

    @Test
    public void build_filtersByControllerName() {
        // 类全名过滤（升级后不再支持简单名）
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerName(EXTENDS_CONTROLLER));
        assertEquals(1, list.size());
        assertEquals(EXTENDS_CONTROLLER, list.get(0).getControllerFullName());
    }

    @Test
    public void build_filtersByControllerFullNameExcludesOthers() {
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerName(USER_CONTROLLER));
        assertEquals(1, list.size());
        assertEquals(USER_CONTROLLER, list.get(0).getControllerFullName());
    }

    @Test
    public void build_ignoresByControllerFullName() {
        List<ControllerData> list = build(false, cfg -> cfg.addIgnoreControllerName(EXTENDS_CONTROLLER));
        assertEquals(1, list.size());
        assertEquals(USER_CONTROLLER, list.get(0).getControllerFullName());
    }

    @Test
    public void build_filtersByControllerPackage() {
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerPackage("io.gitee.sample.controller"));
        assertEquals(2, list.size());
    }

    @Test
    public void build_filtersByControllerPackageAncestorPrefix() {
        // 配置任意一级包：上级包命中其全部子包下的 controller
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerPackage("io.gitee.sample"));
        assertEquals(2, list.size());
        List<ControllerData> topList = build(false, cfg -> cfg.addFilterControllerPackage("io.gitee"));
        assertEquals(2, topList.size());
    }

    @Test
    public void build_filterControllerPackageSegmentBoundary() {
        // 段边界：配置的包必须是完整包前缀，不得按子串命中
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerPackage("io.gitee.sample.contr"));
        assertEquals(0, list.size());
    }

    @Test
    public void build_filterPackageMissingYieldsEmptyResult() {
        // 配置不存在的包不再抛「包路径不存在」异常（包过滤已移至 ControllerParser），结果为解析不到 controller
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerPackage("io.gitee.sample.missing"));
        assertEquals(0, list.size());
    }

    @Test
    public void build_throwsWhenNoJavaFiles() {
        File empty = new File("target/test-empty-java");
        if (empty.exists()) {
            deleteRecursively(empty);
        }
        empty.mkdirs();
        try {
            assertThrows(RuntimeException.class, () -> build(false, cfg -> {
                cfg.getJavaFilePaths().clear();
                cfg.addJavaFilePath(empty.getAbsolutePath());
            }));
        } finally {
            deleteRecursively(empty);
        }
    }

    @Test
    public void build_uriComposition() {
        List<ControllerData> list = build();
        ControllerData user = controllerOf(list, USER_CONTROLLER);
        assertNotNull(user);
        assertEquals("User management controller", user.getComment());
        assertEquals(1, user.getBaseUriList().size());
        assertEquals("/api/user", user.getBaseUriList().get(0));

        InterfaceData info = interfaceOf(user, "getById");
        assertEquals(1, info.getUriList().size());
        assertEquals("/api/user/info", info.getUriList().get(0));
        assertEquals(1, info.getRequestTypeList().size());
        assertEquals("GET", info.getRequestTypeList().get(0));
        assertEquals("Get user by id", info.getComment());
    }

    @Test
    public void build_mappingWithMethodArray() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData both = interfaceOf(user, "both");
        assertNotNull(both);
        assertEquals(1, both.getUriList().size());
        assertEquals("/api/user/both", both.getUriList().get(0));
        assertEquals(2, both.getRequestTypeList().size());
        assertTrue(both.getRequestTypeList().contains("GET"));
        assertTrue(both.getRequestTypeList().contains("POST"));
    }

    @Test
    public void build_multiUriDeleteAnnotaion() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData remove = interfaceOf(user, "remove");
        assertEquals(2, remove.getUriList().size());
        assertTrue(remove.getUriList().contains("/api/user/remove"));
        assertTrue(remove.getUriList().contains("/api/user/delete"));
        assertEquals(1, remove.getRequestTypeList().size());
        assertEquals("DELETE", remove.getRequestTypeList().get(0));
    }

    @Test
    public void build_requestContentTypeAndBodyData() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);

        InterfaceData create = interfaceOf(user, "create");
        assertEquals("application/json", create.getRequestContentType());
        assertNull(create.getFormData());
        assertNotNull(create.getBodyData());
        assertFalse(create.getBodyData().isLastValue());
        assertTrue(create.getBodyData().getFieldInfoList().size() > 5);

        InterfaceData update = interfaceOf(user, "update");
        assertEquals("application/json", update.getRequestContentType());
        assertNotNull(update.getBodyData());

        InterfaceData patch = interfaceOf(user, "patch");
        assertEquals("application/json", patch.getRequestContentType());
        assertNotNull(patch.getBodyData());
    }

    @Test
    public void build_formDataForPrimitiveParam() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData info = interfaceOf(user, "getById");
        assertEquals("application/x-www-form-urlencoded", info.getRequestContentType());
        assertNotNull(info.getFormData());
        assertEquals(1, info.getFormData().size());
        FieldInfo id = info.getFormData().get(0);
        assertEquals("id", id.getName());
        assertEquals("Long", id.getType());
        assertEquals("user identity", id.getComment());
    }

    @Test
    public void build_formDataForObjectParamFlattensFields() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData list = interfaceOf(user, "list");
        assertEquals("application/x-www-form-urlencoded", list.getRequestContentType());
        assertNotNull(list.getFormData());
        assertEquals(4, list.getFormData().size());
        assertTrue(list.getFormData().stream().anyMatch(f -> "keyword".equals(f.getName()) && "String".equals(f.getType())));
        assertTrue(list.getFormData().stream().anyMatch(f -> "page".equals(f.getName()) && "Integer".equals(f.getType())));
        assertTrue(list.getFormData().stream().anyMatch(f -> "startDate".equals(f.getName()) && "Date".equals(f.getType())));
    }

    @Test
    public void build_formDataForInheritedObjectParam() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData form = interfaceOf(user, "form");
        assertEquals("multipart/form-data", form.getRequestContentType());
        Set<String> names = new HashSet<>();
        form.getFormData().forEach(f -> names.add(f.getName()));
        assertTrue(names.contains("sort"));
        assertTrue(names.contains("desc"));
        assertTrue(names.contains("pageNo"));
        assertTrue(names.contains("pageSize"));
    }

    @Test
    public void build_validationInjectedForValidatedBody() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData update = interfaceOf(user, "update");
        FieldInfo nickname = fieldOf(update.getBodyData().getFieldInfoList(), "nickname");
        assertEquals("字符串非空", nickname.getValidation());
    }

    private FieldInfo fieldOf(List<FieldInfo> fieldInfoList, String name) {
        return fieldInfoList.stream().filter(f -> name.equals(f.getName())).findFirst().orElse(null);
    }

    @Test
    public void build_defaultValueInjectedForPrimitiveParam() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData info = interfaceOf(user, "getById");
        String defaultValue = info.getFormData().get(0).getDefaultValue();
        assertNotNull(defaultValue);
        assertTrue(defaultValue.matches("-?\\d+"));
    }

    @Test
    public void build_defaultValueInjectedForStringField() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData list = interfaceOf(user, "list");
        FieldInfo keyword = list.getFormData().stream().filter(f -> "keyword".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(keyword);
        String defaultValue = keyword.getDefaultValue();
        assertNotNull(defaultValue);
        assertTrue(defaultValue.startsWith("\""));
        assertTrue(defaultValue.endsWith("\""));
    }

    @Test
    public void build_resDataObjectTree() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData info = interfaceOf(user, "getById");
        FieldDataNode resData = info.getResData();
        assertNotNull(resData);
        assertFalse(resData.isLastValue());
        List<FieldInfo> fields = resData.getFieldInfoList();
        FieldInfo profile = fieldOf(fields, "profile");
        assertNotNull(profile);
        assertNotNull(profile.getValueFieldData());
        assertTrue(profile.getValueFieldData().getFieldInfoList().stream().anyMatch(f -> "level".equals(f.getName())));
    }

    @Test
    public void build_resDataArray() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData list = interfaceOf(user, "list");
        FieldDataNode resData = list.getResData();
        assertNotNull(resData);
        assertTrue(resData.isArray());
        FieldDataNode child = resData.getChildFieldData();
        assertNotNull(child);
        assertFalse(child.isLastValue());
        assertTrue(child.getFieldInfoList().stream().anyMatch(f -> "nickname".equals(f.getName())));
    }

    @Test
    public void build_resDataSelfReferenceTruncated() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData self = interfaceOf(user, "self");
        FieldDataNode resData = self.getResData();
        assertFalse(resData.isLastValue());
        FieldInfo parent = fieldOf(resData.getFieldInfoList(), "parent");
        assertNotNull(parent);
        FieldDataNode parentValue = parent.getValueFieldData();
        assertNotNull(parentValue);
        assertNotNull(parentValue.getFieldInfoList());
        assertTrue(parentValue.getFieldInfoList().isEmpty());
    }

    @Test
    public void build_plainHelperMethodNotExposed() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        assertNull(interfaceOf(user, "helper"));
    }

    @Test
    public void build_recordTypeComponentFieldsParsed() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData demo = interfaceOf(user, "recordDemo");
        assertNotNull(demo);
        assertEquals("/api/user/record", demo.getUriList().get(0));
        FieldDataNode resData = demo.getResData();
        assertNotNull(resData);
        assertFalse(resData.isLastValue());
        assertNotNull(resData.getFieldInfoList());
        assertEquals(2, resData.getFieldInfoList().size());
        FieldInfo username = resData.getFieldInfoList().stream()
                .filter(f -> "username".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(username);
        assertEquals("String", username.getType());
        assertEquals("用户名", username.getComment());
        FieldInfo age = resData.getFieldInfoList().stream()
                .filter(f -> "age".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(age);
        assertEquals("Integer", age.getType());
        assertEquals("年龄", age.getComment());
    }

    @Test
    public void build_innerClassReferenceResolved() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData inner = interfaceOf(user, "inner");
        assertNotNull(inner);
        assertEquals("/api/user/inner", inner.getUriList().get(0));
        FieldDataNode resData = inner.getResData();
        assertNotNull(resData);
        assertFalse(resData.isLastValue());
        assertNotNull(resData.getFieldInfoList());
        assertTrue(resData.getFieldInfoList().stream()
                .anyMatch(f -> "innerName".equals(f.getName()) && "String".equals(f.getType())));
    }

    @Test
    public void build_packageFilterStillResolvesReferencedNestedClasses() {
        // 路径级粗滤只缩小 controller 候选，不影响 controller 引用的外部类（含内部嵌套类）经全量真实索引解析
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerPackage("io.gitee.sample.controller"));
        ControllerData user = controllerOf(list, USER_CONTROLLER);
        assertNotNull(user);
        InterfaceData inner = interfaceOf(user, "inner");
        assertNotNull(inner);
        FieldDataNode resData = inner.getResData();
        assertNotNull(resData);
        assertFalse(resData.isLastValue());
        assertNotNull(resData.getFieldInfoList());
        assertTrue(resData.getFieldInfoList().stream()
                .anyMatch(f -> "innerName".equals(f.getName()) && "String".equals(f.getType())),
                "包过滤下 controller 引用的 Outer.Inner 内部类仍应展开");
    }

    @Test
    public void build_nestedRecordReferenceResolved() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData nestedRec = interfaceOf(user, "nestedRec");
        assertNotNull(nestedRec);
        assertEquals("/api/user/nested-rec", nestedRec.getUriList().get(0));
        FieldDataNode resData = nestedRec.getResData();
        assertNotNull(resData);
        assertFalse(resData.isLastValue());
        assertNotNull(resData.getFieldInfoList());
        FieldInfo key = resData.getFieldInfoList().stream()
                .filter(f -> "key".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(key);
        assertEquals("String", key.getType());
        assertEquals("键名", key.getComment());
        FieldInfo value = resData.getFieldInfoList().stream()
                .filter(f -> "value".equals(f.getName())).findFirst().orElse(null);
        assertNotNull(value);
        assertEquals("Long", value.getType());
    }

    @Test
    public void build_mapGenericValuesExpanded() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData edges = interfaceOf(user, "edges");
        assertNotNull(edges);
        FieldDataNode resData = edges.getResData();
        assertNotNull(resData);
        assertNotNull(resData.getFieldInfoList());

        FieldInfo profileMap = fieldOf(resData.getFieldInfoList(), "profileMap");
        assertNotNull(profileMap);
        FieldDataNode profileValue = profileMap.getValueFieldData();
        assertNotNull(profileValue);
        assertNotNull(profileValue.getFieldInfoList());
        assertEquals(1, profileValue.getFieldInfoList().size());
        FieldInfo profileMapKey = profileValue.getFieldInfoList().get(0);
        assertEquals("mapKey", profileMapKey.getName());
        assertTrue(profileMapKey.isOmitType());
        assertTrue(profileMapKey.getComment().contains("Map<String, UserProfile>"));
        FieldDataNode profileMapValue = profileMapKey.getValueFieldData();
        assertNotNull(profileMapValue);
        assertNotNull(profileMapValue.getFieldInfoList());
        assertTrue(profileMapValue.getFieldInfoList().stream().anyMatch(f -> "bio".equals(f.getName())));
        assertTrue(profileMapValue.getFieldInfoList().stream().anyMatch(f -> "level".equals(f.getName())));

        FieldInfo userListMap = fieldOf(resData.getFieldInfoList(), "userListMap");
        assertNotNull(userListMap);
        FieldDataNode userListValue = userListMap.getValueFieldData();
        assertNotNull(userListValue);
        assertNotNull(userListValue.getFieldInfoList());
        assertEquals(1, userListValue.getFieldInfoList().size());
        FieldInfo userListMapKey = userListValue.getFieldInfoList().get(0);
        assertEquals("mapKey", userListMapKey.getName());
        FieldDataNode userListValueNode = userListMapKey.getValueFieldData();
        assertNotNull(userListValueNode);
        assertTrue(userListValueNode.isArray());
        FieldDataNode userListChild = userListValueNode.getChildFieldData();
        assertNotNull(userListChild);
        assertNotNull(userListChild.getFieldInfoList());
        assertTrue(userListChild.getFieldInfoList().stream().anyMatch(f -> "nickname".equals(f.getName())));

        // Map 子类（LinkedHashMap）同构展开
        FieldInfo linkedProfileMap = fieldOf(resData.getFieldInfoList(), "linkedProfileMap");
        assertNotNull(linkedProfileMap);
        FieldDataNode linkedValue = linkedProfileMap.getValueFieldData();
        assertNotNull(linkedValue);
        assertNotNull(linkedValue.getFieldInfoList());
        assertEquals(1, linkedValue.getFieldInfoList().size());
        FieldInfo linkedMapKey = linkedValue.getFieldInfoList().get(0);
        assertEquals("mapKey", linkedMapKey.getName());
        assertTrue(linkedMapKey.getComment().contains("Map<String, UserProfile>"));
        assertNotNull(linkedMapKey.getValueFieldData().getFieldInfoList());
        assertTrue(linkedMapKey.getValueFieldData().getFieldInfoList().stream()
                .anyMatch(f -> "bio".equals(f.getName())));
    }

    @Test
    public void build_mapFormatJsonShowsCommentTypeOnly() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData edges = interfaceOf(user, "edges");
        String json = StringUtil.toFormatJsonStr(edges.getResData(), 0, 2, true, true, false, true, true);
        assertNotNull(json);
        assertTrue(json.contains("// Map<String, UserProfile>"), json);
        assertFalse(json.contains("Map<String, UserProfile> | UserProfile"), json);
        assertTrue(json.contains("\"mapKey\": {"), json);
    }

    @Test
    public void build_nestedTypeDoesNotProduceExtraController() {
        List<ControllerData> list = build();
        assertEquals(2, list.size());
        list.forEach(c -> assertFalse(c.getControllerFullName().contains("Outer")));
    }

    @Test
    public void build_parentMethodsDisabledByDefault() {
        ControllerData ext = controllerOf(build(), EXTENDS_CONTROLLER);
        Set<String> uris = interfaceUriSet(ext);
        assertEquals(1, uris.size());
        assertTrue(uris.contains("/api/ext/own"));
    }

    @Test
    public void build_parentMethodsEnabled() {
        List<ControllerData> list = build(true, null);
        ControllerData ext = controllerOf(list, EXTENDS_CONTROLLER);
        Set<String> uris = interfaceUriSet(ext);
        assertTrue(uris.contains("/api/ext/own"));
        assertTrue(uris.contains("/api/ext/parent"));
        assertTrue(uris.contains("/api/ext/child-then"));
        assertFalse(uris.contains("/api/ext/parentHelper"));

        InterfaceData overridable = interfaceOf(ext, "overridable");
        assertNotNull(overridable);
        assertEquals("overridable method whose child overrides without mapping annotation", overridable.getComment());
        InterfaceData parent = interfaceOf(ext, "parentOnly");
        assertNotNull(parent);
        assertEquals("only declared in parent controller", parent.getComment());
    }

    @Test
    public void build_controllerIdDeterministicBasedOnFileAndTime() {
        List<ControllerData> list = build();
        ControllerData user = controllerOf(list, USER_CONTROLLER);
        assertNotNull(user.getControllerId());
        assertNotNull(user.getCreateTime());
        list.forEach(c -> assertNotNull(c.getControllerId()));
    }

    @Test
    public void build_deterministicIdsStableAcrossRuns() {
        Date fixed = new Date(1600000000000L);
        String first = runIdBuild(fixed, true);
        String second = runIdBuild(fixed, true);
        assertEquals(first, second);
    }

    @Test
    public void build_legacyInterfaceIdDiffersEveryRun() {
        Date fixed = new Date(1600000000000L);
        String first = runIdBuild(fixed, false);
        String second = runIdBuild(fixed, false);
        assertNotEquals(first, second);
    }

    private String runIdBuild(Date parseTime, boolean deterministic) {
        List<ControllerData> collector = new ArrayList<>();
        LimeJapiDocsParser.build(new ParserConfigHandler() {
            @Override
            public ParserConfig getParserConfig() {
                return ParserConfig.build(FIXTURE_ROOT).setDeterministicId(deterministic);
            }

            @Override
            public Date getParseTime() {
                return parseTime;
            }

            @Override
            public void parseFinishedHandle(List<ControllerData> controllerDataList) {
                collector.addAll(controllerDataList);
            }
        });
        ControllerData user = controllerOf(collector, USER_CONTROLLER);
        InterfaceData info = interfaceOf(user, "getById");
        return user.getControllerId() + "|" + info.getInterfaceId();
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    @Test
    public void build_acceptsModuleRootDirNotEndingWithJava() {
        // 配置更上一层的目录（非 /java 结尾），递归扫描仍能解析跨包引用
        File moduleRoot = new File("src/test/resources/fixtures/sample");
        List<ControllerData> collector = new ArrayList<>();
        LimeJapiDocsParser.build(new ParserConfigHandler() {
            @Override
            public ParserConfig getParserConfig() {
                return ParserConfig.build(moduleRoot.getAbsolutePath());
            }

            @Override
            public void parseFinishedHandle(List<ControllerData> controllerDataList) {
                collector.addAll(controllerDataList);
            }
        });
        assertEquals(2, collector.size());
        ControllerData user = controllerOf(collector, USER_CONTROLLER);
        assertNotNull(user);
        // 跨包/跨目录引用（controller 引 dto.User、record 等）仍能解析出字段
        InterfaceData info = interfaceOf(user, "getById");
        assertNotNull(info);
        assertNotNull(info.getResData());
        assertFalse(info.getResData().isLastValue());
    }

    @Test
    public void build_filterPackageWithModuleRootDir() {
        // 任意目录下，filterControllerPackages 仍精准按包过滤
        File moduleRoot = new File("src/test/resources/fixtures/sample");
        List<ControllerData> collector = new ArrayList<>();
        LimeJapiDocsParser.build(new ParserConfigHandler() {
            @Override
            public ParserConfig getParserConfig() {
                return ParserConfig.build(moduleRoot.getAbsolutePath())
                        .addFilterControllerPackage("io.gitee.sample.controller");
            }

            @Override
            public void parseFinishedHandle(List<ControllerData> controllerDataList) {
                collector.addAll(controllerDataList);
            }
        });
        assertEquals(2, collector.size());
    }
}