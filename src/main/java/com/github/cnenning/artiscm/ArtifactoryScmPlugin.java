package com.github.cnenning.artiscm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.client.HttpClientBuilder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cnenning.artiscm.ArtifactoryClient.Revision;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.DefaultGoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

@Extension
public class ArtifactoryScmPlugin implements GoPlugin {

	public static final String EXTENSION_NAME = "scm";
	private static final List<String> GO_SUPPORTED_VERSIONS = Arrays.asList("1.0");
	private static final GoPluginIdentifier GO_PLUGIN_ID = new GoPluginIdentifier(EXTENSION_NAME, GO_SUPPORTED_VERSIONS);
	private static final String GO_API_VERSION = "1.0";

	public static final String REQUEST_SETTINGS_GET_CONFIG = "go.plugin-settings.get-configuration";
	public static final String REQUEST_SETTINGS_GET_VIEW = "go.plugin-settings.get-view";
	public static final String REQUEST_SETTINGS_VALIDATE = "go.plugin-settings.validate-configuration";
	public static final String REQUEST_SETTINGS_GET_THEM = "go.processor.plugin-settings.get";

	public static final String REQUEST_SCM_CONFIGURATION = "scm-configuration";
	public static final String REQUEST_SCM_VIEW = "scm-view";
	public static final String REQUEST_VALIDATE_SCM_CONFIGURATION = "validate-scm-configuration";
	public static final String REQUEST_CHECK_SCM_CONNECTION = "check-scm-connection";
	public static final String REQUEST_LATEST_REVISION = "latest-revision";
	public static final String REQUEST_LATEST_REVISIONS_SINCE = "latest-revisions-since";
	public static final String REQUEST_CHECKOUT = "checkout";

	public static final DateFormat GO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	private static final int DEFAULT_TIMEOUT = 240;

	private Logger logger = Logger.getLoggerFor(getClass());

	private HttpClient httpClient;

	public ArtifactoryScmPlugin() {
		logger.debug("plugin instance created");
	}

	@Override
	public GoPluginIdentifier pluginIdentifier() {
		return GO_PLUGIN_ID;
	}

	@Override
	public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
		logger.debug("initializeGoApplicationAccessor()");

		GoApiRequest request = new DefaultGoApiRequest(REQUEST_SETTINGS_GET_THEM, GO_API_VERSION, GO_PLUGIN_ID);
		GoApiResponse response = goApplicationAccessor.submit(request);

		String json = response.responseBody();

		httpClient = createHttpClient(json);
	}

	private HttpClient createHttpClient(String json) {
		Builder requestConfigBuilder = RequestConfig.custom();
		try {
			Map settings = json != null
				? new ObjectMapper().readValue(json, Map.class)
				: Collections.EMPTY_MAP;

			int timeout = timeoutFromSettings(settings, "connectTimeout");
			logger.info("setting connectTimeout: " + timeout);
			requestConfigBuilder.setConnectTimeout(timeout);

			timeout = timeoutFromSettings(settings, "socketTimeout");
			logger.info("setting socketTimeout: " + timeout);
			requestConfigBuilder.setSocketTimeout(timeout);

			timeout = timeoutFromSettings(settings, "connectionRequestTimeout");
			logger.info("setting connectionRequestTimeout: " + timeout);
			requestConfigBuilder.setConnectionRequestTimeout(timeout);

			String proxyUrl = (String)settings.get("proxyUrl");
			if (proxyUrl != null && !proxyUrl.isEmpty()) {
				logger.info("setting proxyUrl: " + proxyUrl);
				requestConfigBuilder.setProxy(HttpHost.create(proxyUrl));
			}
		} catch (Exception e) {
			logger.error("could not read plugin settings", e);
		}

		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
		return clientBuilder.build();
	}

	private int timeoutFromSettings(Map settings, String key) {
		int timeout = DEFAULT_TIMEOUT;
		try {
			timeout = Integer.parseInt((String)settings.get(key));
		} catch (Exception e) {
			// ignore
		}
		return timeout;
	}

	@Override
	public GoPluginApiResponse handle(GoPluginApiRequest requestMessage) throws UnhandledRequestTypeException {
		long startTime = System.currentTimeMillis();
		String name = requestMessage.requestName();
		String body = requestMessage.requestBody();
		logger.debug("got requestName: '" + name + "', with body: " + body);
		try {
			if (REQUEST_SETTINGS_GET_CONFIG.equals(name)) {
				return toJson(handlePluginConfig());
			} else if (REQUEST_SETTINGS_GET_VIEW.equals(name)) {
				return toJson(handlePluginConfigView());
			} else if (REQUEST_SETTINGS_VALIDATE.equals(name)) {
				return toJson(handlePluginConfigValidation(body));
			} else if (REQUEST_SCM_CONFIGURATION.equals(name)) {
				return toJson(handleScmConfig());
			} else if (REQUEST_SCM_VIEW.equals(name)) {
				return toJson(handleScmView());
			} else if (REQUEST_VALIDATE_SCM_CONFIGURATION.equals(name)) {
				return toJson(handleScmValidation(body));
			} else if (REQUEST_CHECK_SCM_CONNECTION.equals(name)) {
				return toJson(handleCheckScmConnection(body));
			} else if (REQUEST_LATEST_REVISION.equals(name)) {
				return toJson(handleLatestRevision(body));
			} else if (REQUEST_LATEST_REVISIONS_SINCE.equals(name)) {
				return toJson(handleLatestRevisionsSince(body));
			} else if (REQUEST_CHECKOUT.equals(name)) {
				return toJson(handleCheckout(body));
			}
		} catch (Exception e) {
			return error(name, body, e);
		} finally {
			long endTime = System.currentTimeMillis();
			logger.debug("operation took: " + (endTime - startTime) + " ms");
		}
		return null;
	}

	private Map<String, Object> handlePluginConfig() {
		Map<String, Object> wrapper = new HashMap<>();

		Map<String, Object> map = new HashMap<>();
		map.put("display-name", "Connect Timeout");
		map.put("default-value", String.valueOf(DEFAULT_TIMEOUT));
		map.put("display-order", "0");
		map.put("required", Boolean.FALSE);
		map.put("secure", Boolean.FALSE);
		wrapper.put("connectTimeout", map);

		map = new HashMap<>();
		map.put("display-name", "Socket Timeout");
		map.put("default-value", String.valueOf(DEFAULT_TIMEOUT));
		map.put("display-order", "1");
		map.put("required", Boolean.FALSE);
		map.put("secure", Boolean.FALSE);
		wrapper.put("socketTimeout", map);

		map = new HashMap<>();
		map.put("display-name", "Connection Request Timeout");
		map.put("default-value", String.valueOf(DEFAULT_TIMEOUT));
		map.put("display-order", "2");
		map.put("required", Boolean.FALSE);
		map.put("secure", Boolean.FALSE);
		wrapper.put("connectionRequestTimeout", map);

		map = new HashMap<>();
		map.put("display-name", "Proxy URL");
		map.put("default-value", "");
		map.put("display-order", "3");
		map.put("required", Boolean.FALSE);
		map.put("secure", Boolean.FALSE);
		wrapper.put("proxyUrl", map);

		return wrapper;
	}

	private Map<String, String> handlePluginConfigView() throws IOException {
		InputStream inputStream = getClass().getResourceAsStream("/plugin-config.html");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IOUtils.copy(inputStream, baos);
		String view = baos.toString();

		Map<String, String> map = new HashMap<>();
		map.put("template", view);
		return map;
	}

	private List<Object> handlePluginConfigValidation(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		List<Object> valiErrors = new ArrayList<>();

		config = (Map)config.get("plugin-settings");

		validateTimeout(valiErrors, config, "connectTimeout");
		validateTimeout(valiErrors, config, "socketTimeout");
		validateTimeout(valiErrors, config, "connectionRequestTimeout");

		Map proxyMap = (Map)config.get("proxyUrl");
		if (proxyMap != null) {
			String proxy = (String)proxyMap.get("value");
			try {
				HttpHost.create(proxy);
			} catch (Exception e) {
				Map<String, String> error = new HashMap<>();
				error.put("key", "proxyUrl");
				error.put("message", e.toString());
				valiErrors.add(error);
				
			}
		}

		return valiErrors;
	}

	private void validateTimeout(List<Object> valiErrors, Map config, String key) {
		String value = null;
		Map valueMap = (Map)config.get(key);
		if (valueMap != null) {
			value = (String)valueMap.get("value");
		}
		logger.debug("validating '" + key + "' = '" + value + "'");
		if (!validateTimeoutValue(value)) {
			Map<String, String> error = new HashMap<>();
			error.put("key", key);
			error.put("message", "Invalid timeout value. Must be an integer > 0 or -1.");
			valiErrors.add(error);
		}
	}

	private boolean validateTimeoutValue(String val) {
		if (val == null || val.isEmpty()) {
			return true;
		}
		try {
			int timeout = Integer.parseInt(val);
			if (timeout > 0 || timeout == -1) {
				return true;
			}
		} catch (Exception e) {
			// just log in debug
			logger.debug("could not parse timeout value: " + val, e);
		}
		return false;
	}

	private Map<String, Object> handleScmConfig() {
		// url
		Map<String, Object> map = new HashMap<>();
		map.put("display-name", "url");
		map.put("default-value", "http://artifactory.company.com/repository/path-to/dir-with-versions");
		map.put("part-of-identity", Boolean.TRUE);

		Map<String, Object> wrapper = new HashMap<>();
		wrapper.put("url", map);

		// dummy id
		map = new HashMap<>();
		map.put("display-name", "dummy id");
		map.put("default-value", "");
		map.put("part-of-identity", Boolean.TRUE);
		wrapper.put("dummy_id", map);

		return wrapper;
	}

	private Map<String, String> handleScmView() throws IOException {
		InputStream inputStream = getClass().getResourceAsStream("/scm-config.html");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IOUtils.copy(inputStream, baos);
		String view = baos.toString();

		Map<String, String> map = new HashMap<>();
		map.put("displayValue", "Artifactory SCM");
		map.put("template", view);
		return map;
	}

	private List<Object> handleScmValidation(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		List<Object> valiErrors = new ArrayList<>();

		String url = urlFromConfig(config);
		logger.debug("validating url: " + url);

		List<String> validationMessages = doScmValidation(url);

		for (String msg : validationMessages) {
			Map<String, String> error = new HashMap<>();
			error.put("key", "url");
			error.put("message", msg);
			valiErrors.add(error);
		}

		return valiErrors;
	}

	private Map<String, Object> handleCheckScmConnection(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String url = urlFromConfig(config);
		logger.debug("checking connection to: " + url);
		String status = "fail";
		List<String> messages = new ArrayList<>();

		// do validation first
		List<String> validationErrors = doScmValidation(url);
		if (!validationErrors.isEmpty()) {
			for (String valiError : validationErrors) {
				messages.add(valiError);
			}
		} else {
			// do connection check
			String message;
			try {
				boolean ok = new ArtifactoryClient().checkConnection(url, httpClient);
				if (ok) {
					status = "success";
					message = "Successfully connected";
				} else {
					message = "could not find sub-dirs in provided url";
				}
			} catch (Exception e) {
				logger.error("error while checking SCM connection", e);
				message = e.toString();
			}
			messages.add(message);
		}

		Map<String, Object> map = new HashMap<>();
		map.put("status", status);
		map.put("messages", messages);
		return map;
	}

	private List<String> doScmValidation(String url) {
		List<String> valiErrors = new ArrayList<>();
		if (url == null || url.isEmpty()) {
			valiErrors.add("URL not specified");
		} else if (!(url.startsWith("http://") || url.startsWith("https://"))) {
			valiErrors.add("URL with unknown scheme");
		} else if (!url.endsWith("/")) {
			valiErrors.add("URL must end with a slash");
		}
		return valiErrors;
	}

	private Map<String, Object> handleLatestRevision(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String url = urlFromConfig(config);
		logger.debug("obtaining latest revision of: " + url);
		Revision revision = new ArtifactoryClient().latestRevision(url, httpClient);
		Map<String, Object> revisionJson = buildRevisionJson(revision);

		Map<String, Object> map = new HashMap<>();
		map.put("revision", revisionJson);
		return map;
	}

	private Map<String, Object> handleLatestRevisionsSince(String inputJson) throws JsonParseException, JsonMappingException, IOException, ParseException {
		Map apiInput = new ObjectMapper().readValue(inputJson, Map.class);
		String url = urlFromConfig(apiInput);
		Date since = dateFromApiInput(apiInput);
		logger.debug("obtaining latest revisions since '" + since + "' of: " + url);
		List<Revision> revisions = new ArtifactoryClient().latestRevisionsSince(url, httpClient, since);

		List<Map<String, Object>> revJsonList = new ArrayList<>(revisions.size());
		for (Revision revision : revisions) {
			Map<String, Object> revisionJson = buildRevisionJson(revision);
			revJsonList.add(revisionJson);
		}
		Map<String, Object> map = new HashMap<>();
		map.put("revisions", revJsonList);
		return map;
	}

	private Map<String, Object> handleCheckout(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map apiInput = new ObjectMapper().readValue(inputJson, Map.class);
		String url = urlFromConfig(apiInput);
		String targetDir = targetDirFromApiInput(apiInput);
		String rev = revisonFromApiInput(apiInput);
		logger.debug("checking out, rev: '" + rev + "' from: " + url);

		url = url + rev;
		new ArtifactoryClient().downloadFiles(url, httpClient, targetDir);

		Map<String, Object> map = new HashMap<>();
		map.put("status", "success");
		map.put("messages", Arrays.asList("Successfully checked out"));
		return map;
	}

	private GoPluginApiResponse toJson(Object data) throws JsonProcessingException {
		String json = data == null ? null : new ObjectMapper().writeValueAsString(data);
		DefaultGoPluginApiResponse response = new DefaultGoPluginApiResponse(DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE);
		response.setResponseBody(json);

		logger.debug("sending back json: " + json);

		return response;
	}

	private GoPluginApiResponse error(String requestName, String requestBody, Exception e) {
		StringBuilder msg = new StringBuilder();
		msg.append("ERROR!");
		msg.append("\n\t");
		msg.append("request name: ");
		msg.append(requestName);
		msg.append("\n\t");
		msg.append("request body: ");
		msg.append(requestBody);
		logger.error(msg.toString(), e);
		String json = e.toString();
		DefaultGoPluginApiResponse response = new DefaultGoPluginApiResponse(DefaultGoPluginApiResponse.INTERNAL_ERROR);
		response.setResponseBody(json);
		return response;
	}

	private String urlFromConfig(Map config) {
		Map keysMap = (Map)config.get("scm-configuration");
		Map urlMap = (Map)keysMap.get("url");
		return (String)urlMap.get("value");
	}

	private Date dateFromApiInput(Map input) throws ParseException {
		Map keysMap = (Map)input.get("previous-revision");
		String timestamp = (String) keysMap.get("timestamp");
		return GO_DATE_FORMAT.parse(timestamp);
	}

	private String targetDirFromApiInput(Map input) {
		return (String) input.get("destination-folder");
	}

	private String revisonFromApiInput(Map input) {
		Map keysMap = (Map)input.get("revision");
		return (String) keysMap.get("revision");
	}

	private Map<String, Object> buildRevisionJson(Revision rev) {
		Map<String, Object> map = new HashMap<>();
		map.put("revision", rev.revision);
		map.put("timestamp", GO_DATE_FORMAT.format(rev.timestamp));
		map.put("revisionComment", rev.comment);
		List<Object> files = new ArrayList<>(rev.files.size());
		map.put("modifiedFiles", files);
		for (String filename : rev.files) {
			Map<String, String> file = new HashMap<>();
			file.put("fileName", filename);
			file.put("action", "added");
			files.add(file);
		}
		return map;
	}
}
