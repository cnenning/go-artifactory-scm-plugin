package com.github.cnenning.artiscm;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cnenning.artiscm.ArtifactoryClient.Revision;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

@Extension
public class ArtifactoryPkgPlugin extends AbstractArtifactoryPlugin {

	public static final String EXTENSION_NAME = "package-repository";
	private static final List<String> GO_SUPPORTED_VERSIONS = Arrays.asList("1.0");
	private static final GoPluginIdentifier GO_PLUGIN_ID = new GoPluginIdentifier(EXTENSION_NAME, GO_SUPPORTED_VERSIONS);

	public static final String REQUEST_PKG_REPO_CONFIG = "repository-configuration";
	public static final String REQUEST_PKG_PKG_CONFIG = "package-configuration";
	public static final String REQUEST_PKG_REPO_CONFIG_VALI = "validate-repository-configuration";
	public static final String REQUEST_PKG_PKG_CONFIG_VALI = "validate-package-configuration";
	public static final String REQUEST_PKG_REPO_CHECK_CON = "check-repository-connection";
	public static final String REQUEST_PKG_PKG_CHECK_CON = "check-package-connection";
	public static final String REQUEST_PKG_LATEST_REV = "latest-revision";
	public static final String REQUEST_PKG_LATEST_REV_SINCE = "latest-revision-since";

	public ArtifactoryPkgPlugin() {
		logger.debug("extension instance created");
	}

	@Override
	public GoPluginIdentifier pluginIdentifier() {
		return GO_PLUGIN_ID;
	}

	@Override
	protected GoPluginApiResponse handleApiRequest(String name, String body) throws IOException, ParseException {
		if (REQUEST_PKG_REPO_CONFIG.equals(name)) {
			return toJson(handleRepoConfig());
		} else if (REQUEST_PKG_PKG_CONFIG.equals(name)) {
			return toJson(handlePkgConfig());
		} else if (REQUEST_PKG_REPO_CONFIG_VALI.equals(name)) {
			return toJson(handleRepoValidation(body));
		} else if (REQUEST_PKG_PKG_CONFIG_VALI.equals(name)) {
			return toJson(handlePkgValidation(body));
		} else if (REQUEST_PKG_REPO_CHECK_CON.equals(name)) {
			return toJson(handleCheckRepoConnection(body));
		} else if (REQUEST_PKG_PKG_CHECK_CON.equals(name)) {
			return toJson(handleCheckPkgConnection(body));
		} else if (REQUEST_PKG_LATEST_REV.equals(name)) {
			return toJson(handleLatestRevision(body));
		} else if (REQUEST_PKG_LATEST_REV_SINCE.equals(name)) {
			return toJson(handleLatestRevisionSince(body));
		}
		return null;
	}

	private Map<String, Object> handleRepoConfig() {
		Map<String, Object> wrapper = new HashMap<>();

		Map<String, Object> map = new HashMap<>();
		map.put("display-name", "Base URL");
		map.put("default-value", "http://artifactory.company.com/repository/base-path");
		map.put("display-order", "0");
		map.put("part-of-identity", Boolean.TRUE);
		map.put("secure", Boolean.FALSE);
		map.put("required", Boolean.TRUE);
		wrapper.put("base_url", map);

		addConfigUserAndPassword(wrapper);

		return wrapper;
	}

	private Map<String, Object> handlePkgConfig() {
		Map<String, Object> wrapper = new HashMap<>();

		Map<String, Object> map = new HashMap<>();
		map.put("display-name", "path");
		map.put("default-value", "path/to/artifact");
		map.put("display-order", "0");
		map.put("part-of-identity", Boolean.TRUE);
		map.put("secure", Boolean.FALSE);
		map.put("required", Boolean.TRUE);
		wrapper.put("path", map);

		map = new HashMap<>();
		map.put("display-name", "filename regex");
		map.put("default-value", "");
		map.put("display-order", "1");
		map.put("part-of-identity", Boolean.TRUE);
		map.put("secure", Boolean.FALSE);
		map.put("required", Boolean.TRUE);
		wrapper.put("pattern", map);

		map = new HashMap<>();
		map.put("display-name", "is directory");
		map.put("default-value", "false");
		map.put("display-order", "2");
		map.put("part-of-identity", Boolean.FALSE);
		map.put("secure", Boolean.FALSE);
		map.put("required", Boolean.FALSE);
		wrapper.put("isDir", map);

		addConfigUserAndPassword(wrapper);

		return wrapper;
	}

	private void addConfigUserAndPassword(Map<String, Object> wrapper) {
		Map<String, Object> map = new HashMap<>();
		map.put("display-name", "username");
		map.put("default-value", "");
		map.put("display-order", "3");
		map.put("part-of-identity", Boolean.FALSE);
		map.put("secure", Boolean.FALSE);
		map.put("required", Boolean.FALSE);
		wrapper.put("username", map);

		map = new HashMap<>();
		map.put("display-name", "password");
		map.put("default-value", "");
		map.put("display-order", "4");
		map.put("part-of-identity", Boolean.FALSE);
		map.put("secure", Boolean.TRUE);
		map.put("required", Boolean.FALSE);
		wrapper.put("password", map);
	}

	private List<Object> handleRepoValidation(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		List<Object> valiErrors = new ArrayList<>();

		String baseUrl = configValueRepo(config, "base_url");
		logger.debug("validating url: " + baseUrl);

		List<String> validationMessagesBaseUrl = validateUrl(baseUrl);

		addValidationErrors(valiErrors, "base_url", validationMessagesBaseUrl);

		return valiErrors;
	}

	private List<Object> handlePkgValidation(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		List<Object> valiErrors = new ArrayList<>();

		String path = configValuePkg(config, "path");
		String pattern = configValuePkg(config, "pattern");
		logger.debug("validating url: " + path);
		logger.debug("validating pattern: " + pattern);

		List<String> validationMessagesPath = validatePath(path);
		List<String> validationMessagesPattern = validatePattern(pattern, true);

		addValidationErrors(valiErrors, "path", validationMessagesPath);
		addValidationErrors(valiErrors, "pattern", validationMessagesPattern);

		return valiErrors;
	}

	private Map<String, Object> handleCheckRepoConnection(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String url = configValueRepo(config, "base_url");
		return checkConnection(url, null, userPw(config));
	}

	private Map<String, Object> handleCheckPkgConnection(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String baseUrl = configValueRepo(config, "base_url");
		String path = configValuePkg(config, "path");
		String pattern = configValuePkg(config, "pattern");
		boolean isDirectory = isDirectory(config);
		return checkConnection(baseUrl + path, pattern, userPw(config), isDirectory);
	}

	private Map<String, Object> handleLatestRevision(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String baseUrl = configValueRepo(config, "base_url");
		String path = configValuePkg(config, "path");
		String pattern = configValuePkg(config, "pattern");
		String url = baseUrl + path;
		boolean isDirectory = isDirectory(config);
		logger.debug("obtaining latest revision of: " + url);
		ArtifactoryClient artifactoryClient = new ArtifactoryClient();
		Revision revision = artifactoryClient.latestChild(url, pattern, isDirectory, httpClient, userPw(config));
		Map<String, Object> revisionJson = buildRevisionJson(revision);

		Map<String, String> dataMap = new HashMap<>();
		revisionJson.put("data", dataMap);

		String filename = revision.revision;
		String filenameEncoded = artifactoryClient.escapeName(filename);
		dataMap.put("FILENAME", filename);
		dataMap.put("FILENAME_ENCODED", filenameEncoded);
		dataMap.put("LOCATION", url + filename);
		dataMap.put("LOCATION_ENCODED", url + filenameEncoded);
		if (revision.matchingGroups != null) {
			int i=0;
			for (String group : revision.matchingGroups) {
				dataMap.put("MATCHING_GROUP_" + i, group);
				i++;
			}
		}

		return revisionJson;
	}

	private Map<String, Object> handleLatestRevisionSince(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map<String, Object> latestRevision = handleLatestRevision(inputJson);
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String prevTimestamp = prevRevisonTimestampFromApiInput(config);
		String currentTimestamp = (String)latestRevision.get("timestamp");
		return prevTimestamp.equals(currentTimestamp)
				? new HashMap<String, Object>()
				: latestRevision;
	}

	protected List<String> validatePath(String path) {
		List<String> valiErrors = new ArrayList<>();
		if (path == null || path.isEmpty()) {
			valiErrors.add("path not specified");
		} else if (!path.endsWith("/")) {
			valiErrors.add("path must end with a slash");
		} else if (path.startsWith("/")) {
			valiErrors.add("path must not start with a slash");
		}
		return valiErrors;
	}

	protected String configValueRepo(Map config, String key) {
		return configValue(config, "repository-configuration", key);
	}

	protected String configValuePkg(Map config, String key) {
		return configValue(config, "package-configuration", key);
	}

	protected boolean isDirectory(Map config) {
		String isDirStr = configValuePkg(config, "isDir");
		return isTrue(isDirStr);
	}

	protected String prevRevisonTimestampFromApiInput(Map input) {
		Map keysMap = (Map)input.get("previous-revision");
		return keysMap != null
				? (String) keysMap.get("timestamp")
				: "";
	}

	protected UserPw userPw(Map config) {
		String username = configValuePkg(config, "username");
		String password = configValuePkg(config, "password");
		if (username == null) {
			username = configValueRepo(config, "username");
		}
		if (password == null) {
			password = configValueRepo(config, "password");
		}
		return new UserPw(username, password);
	}
}
