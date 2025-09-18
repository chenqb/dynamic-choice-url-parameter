package com.chen.qa;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.util.FormValidation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.*;

public class DynamicChoiceUrlParameterDefinition extends ParameterDefinition {

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(DynamicChoiceUrlParameterDefinition.class.getName());

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void configureLogging() {
        LogManager.getLogManager()
                .getLogger(DynamicChoiceUrlParameterDefinition.class.getName())
                .setLevel(Level.INFO);
    }

    private final String url;
    private final String jsonPath;
    private final String filter;

    @DataBoundConstructor
    public DynamicChoiceUrlParameterDefinition(
            String name, String url, String jsonPath, String description, String filter) {
        super(name, description);
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON 路径不能为空");
        }
        this.url = url;
        this.jsonPath = jsonPath;
        this.filter = filter;
    }

    public String getUrl() {
        return url;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public String getFilter() {
        return filter;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        LOGGER.log(Level.INFO, "Creating value from JSONObject: {0}", jo.toString());
        DynamicChoiceUrlParameterValue value = req.bindJSON(DynamicChoiceUrlParameterValue.class, jo);
        value.setDescription(getDescription());
        LOGGER.log(Level.INFO, "Created parameter value: {0} with value ''{1}''", new Object[] {
            value.getName(), value.getValue()
        });
        return value;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        LOGGER.info("Entering createValue(StaplerRequest req)");
        String[] values = req.getParameterValues("value");
        if (values == null || values.length == 0 || values[0].isEmpty()) {
            LOGGER.warning("Could not find 'value' in request. Returning empty parameter value.");
            return new DynamicChoiceUrlParameterValue(getName(), "", getDescription());
        }
        String selectedValue = values[0];
        LOGGER.info("Found value '" + selectedValue + "' for parameter '" + getName() + "'");
        return new DynamicChoiceUrlParameterValue(getName(), selectedValue, getDescription());
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        LOGGER.log(Level.INFO, "getDefaultParameterValue() called, creating empty value.");
        return new DynamicChoiceUrlParameterValue(getName(), "", getDescription());
    }

    public List<String> getOptions() {
        List<String> options = new ArrayList<>();
        options.add(""); // 空值作为第一个选项
        try {
            LOGGER.log(Level.INFO, "开始从 URL 获取选项: {0}", url);
            URL u = new URL(url);
            String urlPath = u.getPath().toLowerCase();
            String content;
            String contentType = null;

            // 使用HttpURLConnection来获取Content-Type
            HttpURLConnection connection = (HttpURLConnection) u.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(30000); // 30秒读取超时
            connection.setRequestProperty("User-Agent", "Jenkins-DynamicChoiceUrlParameter/1.2.0");

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            }

            // 获取Content-Type
            contentType = connection.getContentType();
            LOGGER.log(Level.INFO, "获取到内容类型: {0}", contentType);
            LOGGER.log(Level.INFO, "获取到内容: {0}", content);

            // 判断内容类型：优先使用Content-Type，其次使用文件扩展名
            boolean isJson = false;
            boolean isXml = false;
            boolean isText = false;

            if (contentType != null) {
                if (contentType.toLowerCase().contains("application/json")
                        || contentType.toLowerCase().contains("text/json")) {
                    isJson = true;
                } else if (contentType.toLowerCase().contains("application/xml")
                        || contentType.toLowerCase().contains("text/xml")) {
                    isXml = true;
                } else if (contentType.toLowerCase().contains("text/plain")) {
                    isText = true;
                }
            }

            // 如果Content-Type无法确定，则回退到文件扩展名判断
            if (!isJson && !isXml && !isText) {
                if (urlPath.endsWith(".json")) {
                    isJson = true;
                } else if (urlPath.endsWith(".xml")) {
                    isXml = true;
                } else if (urlPath.endsWith(".txt")) {
                    isText = true;
                } else {
                    // 默认尝试JSON解析（适用于API接口）
                    isJson = true;
                    LOGGER.log(Level.INFO, "无法确定内容类型，默认尝试JSON解析");
                }
            }

            if (isJson) {
                // JSON a
                Object jsonData = JSONSerializer.toJSON(content);
                if (jsonData instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject) jsonData;
                    
                    // 检查是否使用了数组字段提取语法 (如 data.versions[].version)
                    if (jsonPath.contains("[].")) {
                        // 解析数组字段提取语法
                        String[] parts = jsonPath.split("\\[\\]\\.", 2);
                        if (parts.length == 2) {
                            String arrayPath = parts[0];
                            String fieldName = parts[1];
                            
                            // 获取数组
                            Object arrayObj = getValueByPath(jsonObject, arrayPath);
                            if (arrayObj instanceof JSONArray) {
                                JSONArray array = (JSONArray) arrayObj;
                                for (Object item : array) {
                                    if (item instanceof JSONObject) {
                                        JSONObject itemObj = (JSONObject) item;
                                        if (itemObj.has(fieldName)) {
                                            Object fieldValue = itemObj.get(fieldName);
                                            options.add(fieldValue == null ? "" : fieldValue.toString());
                                        }
                                    }
                                }
                            } else {
                                return List.of("错误：路径未指向数组");
                            }
                        } else {
                            return List.of("错误：无效的数组字段提取语法，请使用 data.versions[].version 格式");
                        }
                    } else {
                        // 原有的简单路径解析
                        Object current = getValueByPath(jsonObject, jsonPath);
                        
                        if (current instanceof JSONArray) {
                            for (Object o : (JSONArray) current) {
                                options.add(o == null ? "" : o.toString());
                            }
                        } else {
                            return List.of("错误：JSON 路径未指向列表");
                        }
                    }
                }
            } else if (isXml) {
                LOGGER.log(Level.INFO, "解析 XML 内容");
                try {
                    javax.xml.parsers.DocumentBuilderFactory factory =
                            javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                    org.w3c.dom.Document doc =
                            builder.parse(new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

                    // 使用 XPath 查找父节点
                    XPathFactory xPathfactory = XPathFactory.newInstance();
                    XPath xpath = xPathfactory.newXPath();
                    String parentExpression = "/" + jsonPath.replace('.', '/');
                    LOGGER.log(Level.INFO, "使用XPath查找父节点: {0}", parentExpression);

                    org.w3c.dom.Node parentNode =
                            (org.w3c.dom.Node) xpath.evaluate(parentExpression, doc, XPathConstants.NODE);

                    if (parentNode != null) {
                        // 遍历父节点的所有子元素
                        org.w3c.dom.NodeList childNodes = parentNode.getChildNodes();
                        for (int i = 0; i < childNodes.getLength(); i++) {
                            org.w3c.dom.Node item = childNodes.item(i);
                            if (item.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                String value = item.getTextContent().trim();
                                if (!value.isEmpty()) {
                                    options.add(value);
                                }
                            }
                        }
                        LOGGER.log(Level.INFO, "xml 列表", options);
                    } else {
                        return List.of("错误: XML 路径无效");
                    }
                } catch (javax.xml.parsers.ParserConfigurationException
                        | org.xml.sax.SAXException
                        | XPathExpressionException e) {
                    LOGGER.log(Level.SEVERE, "解析 XML 时发生错误", e);
                    return List.of("错误: " + e.getMessage());
                }
            } else if (isText) {
                // TXT 内容处理
                for (String line : content.split("\\r?\\n")) {
                    if (!line.trim().isEmpty()) {
                        options.add(line.trim());
                    }
                }
            } else {
                LOGGER.log(Level.WARNING, "不支持的内容类型: {0}", contentType != null ? contentType : "未知");
                return List.of("错误：不支持的内容类型");
            }
            LOGGER.log(Level.INFO, "最终获取到的选项: {0}", options);

            // 如果 filter 不为空，则进行过滤
            if (filter != null && !filter.trim().isEmpty()) {
                LOGGER.log(Level.INFO, "使用正则表达式进行过滤: {0}", filter);
                List<String> filteredOptions = new ArrayList<>();
                filteredOptions.add(""); // 空值选项不过滤
                for (String option : options) {
                    if (!option.isEmpty() && option.matches(filter)) {
                        filteredOptions.add(option);
                    }
                }
                LOGGER.log(Level.INFO, "过滤后的选项: {0}", filteredOptions);
                options = filteredOptions; // 使用过滤后的选项继续处理
            }
            
            // 对选项进行排序（保留空值在第一位）
            if (options.size() > 1) {
                String emptyOption = options.get(0); // 保存空值选项
                List<String> nonEmptyOptions = new ArrayList<>(options.subList(1, options.size()));
                nonEmptyOptions.sort(String::compareToIgnoreCase); // 按名称排序，忽略大小写
                options.clear();
                options.add(emptyOption); // 重新添加空值选项到第一位
                options.addAll(nonEmptyOptions); // 添加排序后的选项
                LOGGER.log(Level.INFO, "选项已按名称排序: {0}", options);
            }
            return options;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "获取选项时发生错误", e);
            return List.of("Error: " + e.getMessage());
        }
    }

    /**
     * 根据路径获取JSON对象中的值
     */
    private Object getValueByPath(JSONObject jsonObject, String path) {
        String[] paths = path.split("\\.");
        Object current = jsonObject;
        for (String p : paths) {
            if (current instanceof JSONObject && ((JSONObject) current).has(p)) {
                current = ((JSONObject) current).get(p);
            } else {
                return null;
            }
        }
        return current;
    }

    @Extension
    @Symbol({"dynamicChoiceUrl"})
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Dynamic Choice URL Parameter";
        }

        @Override
        public ParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (req == null) {
                return super.newInstance((StaplerRequest) null, formData);
            }
            return req.bindJSON(DynamicChoiceUrlParameterDefinition.class, formData);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckUrl(@QueryParameter String url) {
            if (url == null || url.isEmpty()) {
                return FormValidation.error("URL 不能为空");
            }
            return FormValidation.ok();
        }
    }
}
