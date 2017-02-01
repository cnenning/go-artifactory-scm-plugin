package com.github.cnenning.artiscm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cnenning.artiscm.ArtifactoryClient.Revision;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

@Extension
public class ArtifactoryScmPlugin extends AbstractArtifactoryPlugin implements GoPlugin {

	public static final String EXTENSION_NAME = "scm";
	private static final List<String> GO_SUPPORTED_VERSIONS = Arrays.asList("1.0");
	private static final GoPluginIdentifier GO_PLUGIN_ID = new GoPluginIdentifier(EXTENSION_NAME, GO_SUPPORTED_VERSIONS);

	public static final String REQUEST_SCM_CONFIGURATION = "scm-configuration";
	public static final String REQUEST_SCM_VIEW = "scm-view";
	public static final String REQUEST_SCM_VALIDATE_CONFIGURATION = "validate-scm-configuration";
	public static final String REQUEST_SCM_CHECK_CONNECTION = "check-scm-connection";
	public static final String REQUEST_SCM_LATEST_REVISION = "latest-revision";
	public static final String REQUEST_SCM_LATEST_REVISIONS_SINCE = "latest-revisions-since";
	public static final String REQUEST_SCM_CHECKOUT = "checkout";

	public ArtifactoryScmPlugin() {
		logger.debug("extension instance created");
	}

	@Override
	public GoPluginIdentifier pluginIdentifier() {
		return GO_PLUGIN_ID;
	}

	@Override
	protected GoPluginApiResponse handleApiRequest(String name, String body) throws IOException, ParseException
	{
		if (REQUEST_SCM_CONFIGURATION.equals(name)) {
			return toJson(handleScmConfig());
		} else if (REQUEST_SCM_VIEW.equals(name)) {
			return toJson(handleScmView());
		} else if (REQUEST_SCM_VALIDATE_CONFIGURATION.equals(name)) {
			return toJson(handleScmValidation(body));
		} else if (REQUEST_SCM_CHECK_CONNECTION.equals(name)) {
			return toJson(handleCheckScmConnection(body));
		} else if (REQUEST_SCM_LATEST_REVISION.equals(name)) {
			return toJson(handleLatestRevision(body));
		} else if (REQUEST_SCM_LATEST_REVISIONS_SINCE.equals(name)) {
			return toJson(handleLatestRevisionsSince(body));
		} else if (REQUEST_SCM_CHECKOUT.equals(name)) {
			return toJson(handleCheckout(body));
		}
		return null;
	}

	private Map<String, Object> handleScmConfig() {
		// url
		Map<String, Object> map = new HashMap<>();
		map.put("display-name", "url");
		map.put("default-value", "http://artifactory.company.com/repository/path-to/dir-with-versions");
		map.put("part-of-identity", Boolean.TRUE);

		Map<String, Object> wrapper = new HashMap<>();
		wrapper.put("url", map);

		// filename pattern
		map = new HashMap<>();
		map.put("display-name", "filename regex");
		map.put("default-value", "");
		map.put("part-of-identity", Boolean.TRUE);
		wrapper.put("pattern", map);

		// version pattern
		map = new HashMap<>();
		map.put("display-name", "version regex");
		map.put("default-value", "");
		map.put("part-of-identity", Boolean.TRUE);
		wrapper.put("version_regex", map);

		// username
		map = new HashMap<>();
		map.put("display-name", "username");
		map.put("default-value", "");
		map.put("part-of-identity", Boolean.FALSE);
		wrapper.put("username", map);

		// password
		map = new HashMap<>();
		map.put("display-name", "password");
		map.put("default-value", "");
		map.put("part-of-identity", Boolean.FALSE);
		map.put("secure", Boolean.TRUE);
		wrapper.put("username", map);

		// dummy id
		map = new HashMap<>();
		map.put("display-name", "dummy id");
		map.put("default-value", "");
		map.put("part-of-identity", Boolean.TRUE);
		wrapper.put("dummy_id", map);

		// version only
		map = new HashMap<>();
		map.put("display-name", "version.txt only");
		map.put("default-value", "false");
		map.put("part-of-identity", Boolean.FALSE);
		wrapper.put("version_only", map);

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

		String url = configValue(config, "url");
		String pattern = configValue(config, "pattern");
		logger.debug("validating url: " + url);
		logger.debug("validating pattern: " + pattern);

		List<String> validationMessagesUrl = validateUrl(url);
		List<String> validationMessagesPattern = validatePattern(pattern, false);

		addValidationErrors(valiErrors, "url", validationMessagesUrl);
		addValidationErrors(valiErrors, "pattern", validationMessagesPattern);

		return valiErrors;
	}

	private Map<String, Object> handleCheckScmConnection(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String url = configValue(config, "url");
		String versionRegex = configValue(config, "version_regex");
		return checkConnection(url, versionRegex, userPw(config));
	}

	private Map<String, Object> handleLatestRevision(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map config = new ObjectMapper().readValue(inputJson, Map.class);
		String url = configValue(config, "url");
		String versionRegex = configValue(config, "version_regex");
		logger.debug("obtaining latest revision of: " + url + ", with regex: " + versionRegex);
		Revision revision = new ArtifactoryClient().latestRevision(url, versionRegex, httpClient, userPw(config));
		Map<String, Object> revisionJson = buildRevisionJson(revision);

		Map<String, Object> map = new HashMap<>();
		map.put("revision", revisionJson);
		return map;
	}

	private Map<String, Object> handleLatestRevisionsSince(String inputJson) throws JsonParseException, JsonMappingException, IOException {
		Map apiInput = new ObjectMapper().readValue(inputJson, Map.class);
		String url = configValue(apiInput, "url");
		String versionRegex = configValue(apiInput, "version_regex");
		Date since = dateFromApiInput(apiInput);
		logger.debug("obtaining latest revisions since '" + since + "' of: " + url + ", with regex: " + versionRegex);
		List<Revision> revisions = new ArtifactoryClient().latestRevisionsSince(url, versionRegex, httpClient, userPw(apiInput), since);

		List<Map<String, Object>> revJsonList = new ArrayList<>(revisions.size());
		for (Revision revision : revisions) {
			Map<String, Object> revisionJson = buildRevisionJson(revision);
			revJsonList.add(revisionJson);
		}
		Map<String, Object> map = new HashMap<>();
		map.put("revisions", revJsonList);
		return map;
	}

	private Map<String, Object> handleCheckout(String inputJson) {
		String status;
		String msg;
		try {
			Map apiInput = new ObjectMapper().readValue(inputJson, Map.class);
			String url = configValue(apiInput, "url");
			String pattern = configValue(apiInput, "pattern");
			String targetDirPath = targetDirFromApiInput(apiInput);
			String rev = revisonFromApiInput(apiInput);
			boolean versionOnly = versionOnly(apiInput);

			// create target dir
			File targetDir = new File(targetDirPath);
			if (!targetDir.exists()) {
				logger.info("creating target dir: " + targetDirPath);
				targetDir.mkdirs();
			}

			// do checkout
			if (!versionOnly) {
				logger.debug("checking out, rev: '" + rev + "' from: " + url + ", pattern: " + pattern);

				url = url + rev;
				new ArtifactoryClient().downloadFiles(url, httpClient, userPw(apiInput), targetDir, pattern);
			} else {
				logger.debug("creating version file, rev: '" + rev + "' in: " + targetDir);

				File versionFile = new File(targetDir, "version.txt");
				try(PrintWriter writer = new PrintWriter(new FileOutputStream(versionFile))) {
					writer.println(rev);
				}
			}
			status = "success";
			msg = "Successfully checked out";
		} catch (Exception e) {
			status = "failure";
			msg = e.toString();
			logger.error("could not checkout", e);
		}
		Map<String, Object> map = new HashMap<>();
		map.put("status", status);
		map.put("messages", Arrays.asList(msg));
		return map;
	}

	protected String configValue(Map config, String key) {
		return configValue(config, "scm-configuration", key);
	}

	protected boolean versionOnly(Map config) {
		String str = configValue(config, "version_only");
		return isTrue(str);
	}

	protected UserPw userPw(Map config) {
		return new UserPw(configValue(config, "username"), configValue(config, "password"));
	}
}
