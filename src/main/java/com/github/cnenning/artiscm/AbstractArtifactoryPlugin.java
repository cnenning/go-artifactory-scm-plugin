package com.github.cnenning.artiscm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cnenning.artiscm.ArtifactoryClient.Revision;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.DefaultGoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

public abstract class AbstractArtifactoryPlugin implements GoPlugin {

	private static final String GO_API_VERSION = "1.0";

	public static final String REQUEST_SETTINGS_GET_CONFIG = "go.plugin-settings.get-configuration";
	public static final String REQUEST_SETTINGS_GET_VIEW = "go.plugin-settings.get-view";
	public static final String REQUEST_SETTINGS_VALIDATE = "go.plugin-settings.validate-configuration";
	public static final String REQUEST_SETTINGS_GET_THEM = "go.processor.plugin-settings.get";

	public static final DateFormat GO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	public static final DateTimeFormatter GO_DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	private static final int DEFAULT_TIMEOUT = 240;

	protected Logger logger = Logger.getLoggerFor(getClass());

	protected HttpClient httpClient;

	@Override
	public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
		logger.debug("initializeGoApplicationAccessor()");

		GoApiRequest request = new DefaultGoApiRequest(REQUEST_SETTINGS_GET_THEM, GO_API_VERSION, pluginIdentifier());
		GoApiResponse response = goApplicationAccessor.submit(request);

		String json = response.responseBody();

		httpClient = createHttpClient(json);
	}

	private HttpClient createHttpClient(String json) {
		Builder requestConfigBuilder = RequestConfig.custom();
		Integer connPoolSize = null;
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

			String connPoolSizeStr = (String)settings.get("connPoolSize");
			if (connPoolSizeStr != null && !connPoolSizeStr.isEmpty()) {
				connPoolSize = Integer.valueOf(connPoolSizeStr);
				logger.info("setting ConnPoolSize: " + connPoolSize);
			}
		} catch (Exception e) {
			logger.error("could not read plugin settings", e);
		}

		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		clientBuilder.setUserAgent(buildUserAgent());
		clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
		if (connPoolSize != null) {
			clientBuilder.setMaxConnPerRoute(connPoolSize.intValue());
		}
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

	protected String buildUserAgent() {
		String[] pluginInfo = readInfoFromPluginXml();
		String pluginId = pluginInfo[0];
		String pluginVersion = pluginInfo[1];
		String userAgent = "go-plugin " + (pluginId != null ? pluginId : getClass().getName()) 
				+ " " + (pluginVersion != null ? pluginVersion : "unknown version");
		return userAgent;
	}

	private String[] readInfoFromPluginXml() {
		String pluginId = null;
		String pluginVersion = null;
		try
		{
			InputStream xmlStream = getClass().getResourceAsStream("/plugin.xml");
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
			XMLEventReader eventReader = inputFactory.createXMLEventReader(xmlStream);
			while (eventReader.hasNext()) {
				XMLEvent event = eventReader.nextEvent();
				if (event.isStartElement()) {
					StartElement element = event.asStartElement();
					String name = element.getName().getLocalPart();
					if ("go-plugin".equals(name)) {
						Iterator attributes = element.getAttributes();
						while (attributes.hasNext()) {
							Attribute attribute = (Attribute)attributes.next();
							if ("id".equals(attribute.getName().getLocalPart())) {
								pluginId = attribute.getValue();
								break;
							}
						}
					} else if ("version".equals(name)) {
						event = eventReader.nextEvent();
						pluginVersion = event.asCharacters().getData();
					}
				}
			}
		}
		catch (Exception e)
		{
			logger.error("could not read plugin.xml", e);
		}
		return new String[]{pluginId, pluginVersion};
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
			} else {
				GoPluginApiResponse response = handleApiRequest(name, body);
				if (response == null) {
					logger.warn("unknown request: " + name);
				}
				return response;
			}
		} catch (Exception e) {
			return error(name, body, e);
		} finally {
			long endTime = System.currentTimeMillis();
			logger.debug("operation took: " + (endTime - startTime) + " ms");
		}
	}

	protected abstract GoPluginApiResponse handleApiRequest(String name, String body) throws IOException, ParseException;

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

		map = new HashMap<>();
		map.put("display-name", "Connection Pool Size");
		map.put("default-value", "");
		map.put("display-order", "4");
		map.put("required", Boolean.FALSE);
		map.put("secure", Boolean.FALSE);
		wrapper.put("connPoolSize", map);

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

		Map connPoolSize = (Map)config.get("connPoolSize");
		if (connPoolSize != null) {
			String connPoolSizeStr = (String)connPoolSize.get("value");
			try {
				Integer.valueOf(connPoolSizeStr);
			} catch (Exception e) {
				Map<String, String> error = new HashMap<>();
				error.put("key", "connPoolSize");
				error.put("message", "Must be an integer");
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

	protected boolean validateTimeoutValue(String val) {
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

	protected void addValidationErrors(List<Object> listOfMaps, String key, List<String> errors) {
		for (String msg : errors) {
			Map<String, String> error = new HashMap<>();
			error.put("key", key);
			error.put("message", msg);
			listOfMaps.add(error);
		}
	}

	protected List<String> validateUrl(String url) {
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

	protected List<String> validatePattern(String patternStr, boolean required) {
		List<String> valiErrors = new ArrayList<>();
		if (patternStr != null && !patternStr.isEmpty()) {
			try {
				Pattern.compile(patternStr);
			} catch (Exception e) {
				valiErrors.add(e.toString());
			}
		} else if (required) {
			valiErrors.add("pattern is required");
		}
		return valiErrors;
	}

	protected GoPluginApiResponse toJson(Object data) throws JsonProcessingException {
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

	protected String configValue(Map config, String apiKey, String key) {
		if (config != null) {
			Map keysMap = (Map)config.get(apiKey);
			if (keysMap != null) {
				Map innerMap = (Map)keysMap.get(key);
				if (innerMap != null) {
					return (String)innerMap.get("value");
				}
			}
		}
		return null;
	}

	protected Date dateFromApiInput(Map input) {
		Map keysMap = (Map)input.get("previous-revision");
		String timestamp = (String) keysMap.get("timestamp");
		try {
			return GO_DATE_FORMATTER.parseDateTime(timestamp).toDate();
		} catch (Exception e) {
			logger.warn("could not parse date: '" + timestamp + "', json map: " + keysMap);
			logger.debug(e.getMessage(), e);
		}
		return new Date(0);
	}

	protected String targetDirFromApiInput(Map input) {
		return (String) input.get("destination-folder");
	}

	protected String revisonFromApiInput(Map input) {
		Map keysMap = (Map)input.get("revision");
		return (String) keysMap.get("revision");
	}

	protected Map<String, Object> buildRevisionJson(Revision rev) {
		Map<String, Object> map = new HashMap<>();
		map.put("revision", rev.revision);
		map.put("timestamp", GO_DATE_FORMAT.format(rev.timestamp));
		map.put("revisionComment", rev.comment);
		if (rev.files != null) {
			List<Object> files = new ArrayList<>(rev.files.size());
			for (String filename : rev.files) {
				Map<String, String> file = new HashMap<>();
				file.put("fileName", filename);
				file.put("action", "added");
				files.add(file);
			}
			if (!files.isEmpty()) {
				map.put("modifiedFiles", files);
			}
		}
		return map;
	}

	protected Map<String, Object> checkConnection(String url, String pattern) {
		return checkConnection(url, pattern, true);
	}
	protected Map<String, Object> checkConnection(String url, String pattern, boolean directory) {
		logger.debug("checking connection to: " + url);
		String status = "fail";
		List<String> messages = new ArrayList<>();

		// do validation first
		List<String> validationErrors = validateUrl(url);
		if (!validationErrors.isEmpty()) {
			for (String valiError : validationErrors) {
				messages.add(valiError);
			}
		} else {
			// do connection check
			String message;
			try {
				String foundChild;
				String msgOk;
				String msgFail;
				if (!directory) {
					foundChild = new ArtifactoryClient().checkFiles(url, pattern, httpClient);
					msgOk = "Successfully found file " + foundChild;
					msgFail = "could not find files matching pattern";
				} else {
					foundChild = new ArtifactoryClient().checkSubDirs(url, pattern, httpClient);
					msgOk = "Successfully found directory " + foundChild;
					msgFail = "could not find sub-dirs in provided url";
				}
				if (foundChild != null) {
					status = "success";
					message = msgOk;
				} else {
					message = msgFail;
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

	boolean isTrue(String value) {
		return "true".equals(value) || "TRUE".equals(value) || "1".equals(value) || "on".equals(value);
	}
}
