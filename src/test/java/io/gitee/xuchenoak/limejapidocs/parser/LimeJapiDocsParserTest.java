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
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerName("ExtendsController"));
        assertEquals(1, list.size());
        assertEquals(EXTENDS_CONTROLLER, list.get(0).getControllerFullName());
    }

    @Test
    public void build_filtersByControllerPackage() {
        List<ControllerData> list = build(false, cfg -> cfg.addFilterControllerPackage("io.gitee.sample.controller"));
        assertEquals(2, list.size());
    }

    @Test
    public void build_throwsWhenFilterPackageMissing() {
        assertThrows(RuntimeException.class,
                () -> build(false, cfg -> cfg.addFilterControllerPackage("io.gitee.sample.missing")));
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
    public void build_recordTypeDoesNotBreakParsing() {
        ControllerData user = controllerOf(build(), USER_CONTROLLER);
        InterfaceData demo = interfaceOf(user, "recordDemo");
        assertNotNull(demo);
        assertEquals("/api/user/record", demo.getUriList().get(0));
        FieldDataNode resData = demo.getResData();
        assertNotNull(resData);
        assertFalse(resData.isLastValue());
        assertTrue(resData.getFieldInfoList() == null || resData.getFieldInfoList().isEmpty());
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
}