package com.github.cnenning.artiscm.integrationtest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cnenning.artiscm.ArtifactoryPkgPlugin;
import com.github.cnenning.artiscm.ArtifactoryScmPlugin;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

public class IntegrationTests {

	public static final String APP_NAME = "app-name";

	private static Server server;

	private static String SERVER_URL;
	private static String APP_URL;
	private static final int port = port();
	private final static String CONTEXT_ROOT = "arti";
	private final static String JAX_RS_CONTEXT = "repo";

	private static File TMP_DIR;

	protected static int port() {
		final int min = 10000;
		final int max = 65000;
		Random rand = new Random(System.currentTimeMillis());
		return rand.nextInt((max - min) + 1) + min;
	}

	@BeforeClass
	public static void startServer() throws Exception {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
		SERVER_URL = "http://localhost:" + port + "/" + CONTEXT_ROOT + "/" + JAX_RS_CONTEXT;
		APP_URL = SERVER_URL + "/" + APP_NAME + "/";
		sf.setAddress(SERVER_URL);

		List<Class<?>> resourceClasses = new ArrayList<>();
		resourceClasses.add(ArtiTestJaxrsResource.class);
		sf.setResourceClasses(resourceClasses);

		server = sf.create();
	}

	@AfterClass
	public static void cleanupServer() throws Exception {
		server.stop();
		server.destroy();
	}

	@Before
	public void setupTmpDir() throws Exception {
		TMP_DIR = File.createTempFile("test", Long.toString(System.nanoTime()));
		TMP_DIR.delete();
		TMP_DIR.mkdir();
		TMP_DIR.deleteOnExit();
	}

	@After
	public void cleanupTmpDir() throws Exception {
		for (File file : TMP_DIR.listFiles()) {
			file.delete();
		}
		TMP_DIR.delete();
	}

	protected ArtifactoryScmPlugin createPluginScm() {
		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		plugin.initializeGoApplicationAccessor(new GoApplicationAccessor(){
			
			@Override
			public GoApiResponse submit(GoApiRequest request)
			{
				return new DefaultGoApiResponse(200);
			}
		});
		return plugin;
	}

	protected ArtifactoryPkgPlugin createPluginPkg() {
		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		plugin.initializeGoApplicationAccessor(new GoApplicationAccessor(){
			
			@Override
			public GoApiResponse submit(GoApiRequest request)
			{
				return new DefaultGoApiResponse(200);
			}
		});
		return plugin;
	}

	protected GoPluginApiRequest createRequest(String name, String body) {
		DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest("scm", "1.0", name);
		request.setRequestBody(body);
		return request;
	}

	@Test
	public void settingsParsing() throws Exception {
		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		plugin.initializeGoApplicationAccessor(new GoApplicationAccessor(){
			
			@Override
			public GoApiResponse submit(GoApiRequest request)
			{
				String json = "{"
						+ "\"connectTimeout\": \"1\","
						+ "\"socketTimeout\": \"2\","
						+ "\"connectionRequestTimeout\": \"3\","
						+ "\"proxyUrl\": \"http://proxy.example.com:1234\""
						+ "}";
				DefaultGoApiResponse response = new DefaultGoApiResponse(200);
				response.setResponseBody(json);
				return response;
			}
		});

		// just make sure there is no exception
	}

	@Test
	public void pluginSettings() throws Exception {
		String requestJson =
				"{}"
		;
		GoPluginApiRequest request = createRequest("go.plugin-settings.get-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Map map = new ObjectMapper().readValue(response.responseBody(), Map.class);
		Map connectTimeoutMap = (Map) map.get("connectTimeout");
		Map socketTimeoutMap = (Map) map.get("socketTimeout");
		Map connectionRequestTimeoutMap = (Map) map.get("connectionRequestTimeout");
		Map proxyUrlMap = (Map) map.get("proxyUrl");

		Assert.assertEquals("240", connectTimeoutMap.get("default-value"));
		Assert.assertEquals("0", connectTimeoutMap.get("display-order"));
		Assert.assertEquals(Boolean.FALSE, connectTimeoutMap.get("required"));
		Assert.assertEquals(Boolean.FALSE, connectTimeoutMap.get("secure"));

		Assert.assertEquals("240", socketTimeoutMap.get("default-value"));
		Assert.assertEquals("1", socketTimeoutMap.get("display-order"));
		Assert.assertEquals(Boolean.FALSE, socketTimeoutMap.get("required"));
		Assert.assertEquals(Boolean.FALSE, socketTimeoutMap.get("secure"));

		Assert.assertEquals("240", connectionRequestTimeoutMap.get("default-value"));
		Assert.assertEquals("2", connectionRequestTimeoutMap.get("display-order"));
		Assert.assertEquals(Boolean.FALSE, connectionRequestTimeoutMap.get("required"));
		Assert.assertEquals(Boolean.FALSE, connectionRequestTimeoutMap.get("secure"));

		Assert.assertEquals("", proxyUrlMap.get("default-value"));
		Assert.assertEquals("3", proxyUrlMap.get("display-order"));
		Assert.assertEquals(Boolean.FALSE, proxyUrlMap.get("required"));
		Assert.assertEquals(Boolean.FALSE, proxyUrlMap.get("secure"));
	}

	@Test
	public void settingsValidationOk() throws Exception {
		String requestJson =
				"{\"plugin-settings\": {"
					+ "\"connectTimeout\": {"
					+ "\"value\": \"5\""
					+ "}, "
					+ "\"proxyUrl\": {"
					+ "\"value\": \"http://proxy.example.com:1234\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("go.plugin-settings.validate-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().equals("[]"));
	}

	@Test
	public void settingsValidationBadInteger() throws Exception {
		String requestJson =
				"{\"plugin-settings\": {"
					+ "\"connectTimeout\": {"
					+ "\"value\": \"asdf\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("go.plugin-settings.validate-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"key\":\"connectTimeout\""));
		Assert.assertTrue(response.responseBody().contains("\"message\":\"Invalid timeout value. Must be an integer > 0 or -1.\""));
	}

	@Test
	public void settingsValidationBadProxy() throws Exception {
		String requestJson =
				"{\"plugin-settings\": {"
					+ "\"proxyUrl\": {"
					+ "\"value\": \"ftp://whatever:anc\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("go.plugin-settings.validate-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"key\":\"proxyUrl\""));
		Assert.assertTrue(response.responseBody().contains("\"message\":"));
		Assert.assertTrue(response.responseBody().contains("Exception"));
	}

	@Test
	public void settingsView() throws Exception {
		String requestJson ="{}";
		GoPluginApiRequest request = createRequest("go.plugin-settings.get-view", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("{\"template\":\"<div class"));
		Assert.assertTrue(response.responseBody().contains("GOINPUTNAME[connectTimeout].$error.server"));
	}

	@Test
	public void scmConfig() throws Exception {
		String requestJson =
				"{}"
		;
		GoPluginApiRequest request = createRequest("scm-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);

		Map map = new ObjectMapper().readValue(response.responseBody(), Map.class);
		Map urlMap = (Map) map.get("url");
		Map patternMap = (Map) map.get("pattern");
		Map dummyIdMap = (Map) map.get("dummy_id");

		Assert.assertEquals("url", urlMap.get("display-name"));
		Assert.assertEquals(Boolean.TRUE, urlMap.get("part-of-identity"));

		Assert.assertEquals("filename regex", patternMap.get("display-name"));
		Assert.assertEquals(Boolean.TRUE, dummyIdMap.get("part-of-identity"));

		Assert.assertEquals("dummy id", dummyIdMap.get("display-name"));
		Assert.assertEquals(Boolean.TRUE, dummyIdMap.get("part-of-identity"));
	}

	@Test
	public void scmConfigView() throws Exception {
		String requestJson ="{}";
		GoPluginApiRequest request = createRequest("scm-view", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("{\"template\":\"<div class"));
		Assert.assertTrue(response.responseBody().contains("GOINPUTNAME[url].$error.server"));
		Assert.assertTrue(response.responseBody().contains("GOINPUTNAME[pattern].$error.server"));
		Assert.assertTrue(response.responseBody().contains("GOINPUTNAME[dummy_id].$error.server"));
	}

	@Test
	public void scmValidationOk() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "},"
						+ "\"pattern\": {"
						+ "\"value\": \"abc\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-scm-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().equals("[]"));
	}

	@Test
	public void scmValidationUrlMissing() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-scm-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"key\":\"url\""));
		Assert.assertTrue(response.responseBody().contains("\"message\":\"URL not specified\""));
	}

	@Test
	public void scmValidationUrlBadScheme() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"ftp://foo/bar/\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-scm-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"key\":\"url\""));
		Assert.assertTrue(response.responseBody().contains("\"message\":\"URL with unknown scheme\""));
	}

	@Test
	public void scmValidationUrlNoTrailingSlash() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"http://foo/bar\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-scm-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"key\":\"url\""));
		Assert.assertTrue(response.responseBody().contains("\"message\":\"URL must end with a slash\""));
	}

	@Test
	public void scmValidationInvalidPattern() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"pattern\": {"
						+ "\"value\": \"(\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-scm-configuration", requestJson);

		ArtifactoryScmPlugin plugin = new ArtifactoryScmPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"key\":\"pattern\""));
		Assert.assertTrue(response.responseBody().contains("\"message\":\"java.util.regex.PatternSyntaxException: Unclosed group near index 1"));
	}

	@Test
	public void checkScmConnection() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("check-scm-connection", requestJson);

		ArtifactoryScmPlugin plugin = createPluginScm();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"status\":\"success\""));
	}

	@Test
	public void latestRevision() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("latest-revision", requestJson);

		ArtifactoryScmPlugin plugin = createPluginScm();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revision\":{"));
		Assert.assertTrue(response.responseBody().contains("\"revision\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T14:15:00.000Z\""));
		Assert.assertTrue(response.responseBody().contains("\"modifiedFiles\":["));
		Assert.assertTrue(response.responseBody().contains("\"fileName\":\"foo##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"action\":\"added\""));

		Assert.assertFalse(response.responseBody().contains("0.5.1"));
		Assert.assertFalse(response.responseBody().contains("0.9.5"));
	}

	@Test
	public void latestRevisionTrailingSlashMissing() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"" + APP_URL.substring(0, APP_URL.length() - 1) + "\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("latest-revision", requestJson);

		ArtifactoryScmPlugin plugin = createPluginScm();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revision\":{"));
		Assert.assertTrue(response.responseBody().contains("\"revision\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T14:15:00.000Z\""));
		Assert.assertTrue(response.responseBody().contains("\"modifiedFiles\":["));
		Assert.assertTrue(response.responseBody().contains("\"fileName\":\"foo##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"action\":\"added\""));

		Assert.assertFalse(response.responseBody().contains("0.5.1"));
		Assert.assertFalse(response.responseBody().contains("0.9.5"));
	}

	@Test
	public void latestRevisionsSince() throws Exception {
		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "},"
				+ "\"previous-revision\": {"
					+ "\"timestamp\": \"2016-01-02T08:00:00.000Z\""
				+ "}"
			+ "}"
		;
		GoPluginApiRequest request = createRequest("latest-revisions-since", requestJson);

		ArtifactoryScmPlugin plugin = createPluginScm();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revisions\":["));

		Assert.assertTrue(response.responseBody().contains("\"revision\":\"0.9.5\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"0.9.5\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-02T11:45:00.000Z\""));

		Assert.assertTrue(response.responseBody().contains("\"revision\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T14:15:00.000Z\""));
		Assert.assertTrue(response.responseBody().contains("\"modifiedFiles\":["));
		Assert.assertTrue(response.responseBody().contains("\"fileName\":\"foo##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"action\":\"added\""));

		Assert.assertFalse(response.responseBody().contains("0.5.1"));
	}

	@Test
	public void checkout() throws Exception {
		Assert.assertEquals(0, TMP_DIR.list().length);

		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "},"
				+ "\"destination-folder\": \"" + escapePath(TMP_DIR.getAbsolutePath()) + "\","
				+ "\"revision\": {"
					+ "\"revision\": \"1.2.3\""
				+ "}"
			+ "}"
		;
		GoPluginApiRequest request = createRequest("checkout", requestJson);

		ArtifactoryScmPlugin plugin = createPluginScm();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		System.out.println("got response body:");
		System.out.println(response.responseBody());
		Assert.assertTrue(response.responseBody().contains("\"status\":\"success\""));

		String[] files = TMP_DIR.list();
		Arrays.sort(files);
		Assert.assertEquals(2, files.length);
		Assert.assertEquals("foo##1.2.3.txt", files[0]);
		Assert.assertEquals("foobar##1.2.3.txt", files[1]);

		assertFileContent(new File(TMP_DIR, files[0]), "foobar");
		assertFileContent(new File(TMP_DIR, files[1]), "foobar foobar");
	}

	@Test
	public void checkoutVersionOnly() throws Exception {
		Assert.assertEquals(0, TMP_DIR.list().length);

		String requestJson =
				"{\"scm-configuration\": {"
					+ "\"url\": {"
						+ "\"value\": \"" + APP_URL + "\""
					+ "},"
					+ "\"version_only\": {"
						+ "\"value\": \"on\""
					+ "}"
				+ "},"
				+ "\"destination-folder\": \"" + escapePath(TMP_DIR.getAbsolutePath()) + "\","
				+ "\"revision\": {"
					+ "\"revision\": \"1.2.3\""
				+ "}"
			+ "}"
		;
		GoPluginApiRequest request = createRequest("checkout", requestJson);

		ArtifactoryScmPlugin plugin = createPluginScm();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		System.out.println("got response body:");
		System.out.println(response.responseBody());
		Assert.assertTrue(response.responseBody().contains("\"status\":\"success\""));

		String[] files = TMP_DIR.list();
		Arrays.sort(files);
		Assert.assertEquals(1, files.length);
		Assert.assertEquals("version.txt", files[0]);

		assertFileContent(new File(TMP_DIR, files[0]), "1.2.3");
	}

	private void assertFileContent(File file, String content) throws IOException {
		try (InputStream fileInput = new FileInputStream(file)){
			List<String> lines = IOUtils.readLines(fileInput, "UTF-8");
			Assert.assertFalse(lines.isEmpty());
			Assert.assertEquals(content, lines.get(0));
		}
	}

	@Test
	public void checkoutPattern() throws Exception {
		Assert.assertEquals(0, TMP_DIR.list().length);

		String requestJson =
				"{\"scm-configuration\": {"
						+ "\"url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "},"
						+ "\"pattern\": {"
						+ "\"value\": \"foob.*\""
						+ "}"
				+ "},"
				+ "\"destination-folder\": \"" + escapePath(TMP_DIR.getAbsolutePath()) + "\","
				+ "\"revision\": {"
					+ "\"revision\": \"1.2.3\""
				+ "}"
			+ "}"
		;
		GoPluginApiRequest request = createRequest("checkout", requestJson);

		ArtifactoryScmPlugin plugin = createPluginScm();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		System.out.println("got response body:");
		System.out.println(response.responseBody());
		Assert.assertTrue(response.responseBody().contains("\"status\":\"success\""));

		String[] files = TMP_DIR.list();
		Arrays.sort(files);
		Assert.assertEquals(1, files.length);
		Assert.assertEquals("foobar##1.2.3.txt", files[0]);

		assertFileContent(new File(TMP_DIR, files[0]), "foobar foobar");
	}

	protected String escapePath(String path) {
		return path.replaceAll("\\\\", "\\\\\\\\");
	}

	@Test
	public void pkgRepoConfig() throws Exception {
		String requestJson =
				"{}"
		;
		GoPluginApiRequest request = createRequest("repository-configuration", requestJson);

		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);

		Map map = new ObjectMapper().readValue(response.responseBody(), Map.class);
		Map urlMap = (Map) map.get("base_url");

		Assert.assertEquals("Base URL", urlMap.get("display-name"));
		Assert.assertEquals(Boolean.TRUE, urlMap.get("part-of-identity"));
	}

	@Test
	public void pkgPkgConfig() throws Exception {
		String requestJson =
				"{}"
		;
		GoPluginApiRequest request = createRequest("package-configuration", requestJson);

		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);

		Map map = new ObjectMapper().readValue(response.responseBody(), Map.class);
		Map pathMap = (Map) map.get("path");
		Map patternMap = (Map) map.get("pattern");

		Assert.assertEquals("path", pathMap.get("display-name"));
		Assert.assertEquals(Boolean.TRUE, pathMap.get("part-of-identity"));

		Assert.assertEquals("filename regex", patternMap.get("display-name"));
		Assert.assertEquals(Boolean.TRUE, patternMap.get("part-of-identity"));
	}

	@Test
	public void pkgRepoValidationOk() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-repository-configuration", requestJson);

		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().equals("[]"));
	}

	@Test
	public void pkgRepoValidationUrlMissing() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-repository-configuration", requestJson);

		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"key\":\"base_url\""));
		Assert.assertTrue(response.responseBody().contains("\"message\":\"URL not specified\""));
	}

	@Test
	public void pkgPkgValidationOk() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"abc/\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \"xyz\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-package-configuration", requestJson);

		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().equals("[]"));
	}

	@Test
	public void pkgPkgValidationPathNoTrailingSlash() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"abc\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \"xyz\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-package-configuration", requestJson);

		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"message\":\"path must end with a slash\""));
	}

	@Test
	public void pkgPkgValidationPatternMissing() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"abc/\""
					+ "},"
					+ "\"pattern\": {"
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("validate-package-configuration", requestJson);

		ArtifactoryPkgPlugin plugin = new ArtifactoryPkgPlugin();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"message\":\"pattern is required\""));
	}

	@Test
	public void pkgCheckRepoConnection() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("check-repository-connection", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"status\":\"success\""));
	}

	@Test
	public void pkgCheckPkgConnection() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"versions/\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \"foo.*\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("check-package-connection", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"status\":\"success\""));
	}

	@Test
	public void pkgLatestRevision() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"versions/\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \"foo.*\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("latest-revision", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revision\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T10:20:00.000Z\""));

		Assert.assertTrue(response.responseBody().contains("\"FILENAME\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"FILENAME_ENCODED\":\"foobar%23%231.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"LOCATION\":\"" + APP_URL + "versions/" + "foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"LOCATION_ENCODED\":\"" + APP_URL + "versions/" + "foobar%23%231.2.3.txt\""));

		Assert.assertFalse(response.responseBody().contains("foo##1.2.3.txt"));
	}

	@Test
	public void pkgLatestRevisionSince() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"versions/\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \"foo.*\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("latest-revision-since", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revision\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T10:20:00.000Z\""));

		Assert.assertFalse(response.responseBody().contains("foo##1.2.3.txt"));
	}

	@Test
	public void pkgLatestRevisionGroups() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"versions/\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \"(foo.*##)(.*)(\\\\.txt)\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("latest-revision", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revision\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T10:20:00.000Z\""));

		Assert.assertTrue(response.responseBody().contains("\"FILENAME\":\"foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"FILENAME_ENCODED\":\"foobar%23%231.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"LOCATION\":\"" + APP_URL + "versions/" + "foobar##1.2.3.txt\""));
		Assert.assertTrue(response.responseBody().contains("\"LOCATION_ENCODED\":\"" + APP_URL + "versions/" + "foobar%23%231.2.3.txt\""));

		Assert.assertTrue(response.responseBody().contains("\"MATCHING_GROUP_2\":\"1.2.3\""));

		Assert.assertFalse(response.responseBody().contains("foo##1.2.3.txt"));
	}

	@Test
	public void pkgCheckPkgConnection_directory() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \".*\""
					+ "},"
					+ "\"isDir\": {"
					+ "\"value\": \"1\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("check-package-connection", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"status\":\"success\""));
	}

	@Test
	public void pkgLatestRevision_directory() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \".*\""
					+ "},"
					+ "\"isDir\": {"
					+ "\"value\": \"true\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("latest-revision", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revision\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T14:15:00.000Z\""));

		Assert.assertTrue(response.responseBody().contains("\"FILENAME\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"FILENAME_ENCODED\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"LOCATION\":\"" + APP_URL + "1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"LOCATION_ENCODED\":\"" + APP_URL + "1.2.3\""));

		Assert.assertFalse(response.responseBody().contains("0.9.5"));
	}

	@Test
	public void pkgLatestRevisionSince_directory() throws Exception {
		String requestJson =
				"{\"repository-configuration\": {"
						+ "\"base_url\": {"
						+ "\"value\": \"" + APP_URL + "\""
						+ "}"
				+ "}, \"package-configuration\": {"
					+ "\"path\": {"
					+ "\"value\": \"\""
					+ "},"
					+ "\"pattern\": {"
					+ "\"value\": \".*\""
					+ "},"
					+ "\"isDir\": {"
					+ "\"value\": \"TRUE\""
					+ "}"
				+ "}}"
		;
		GoPluginApiRequest request = createRequest("latest-revision-since", requestJson);

		ArtifactoryPkgPlugin plugin = createPluginPkg();
		GoPluginApiResponse response = plugin.handle(request);

		Assert.assertNotNull(response);
		Assert.assertTrue(response.responseBody().contains("\"revision\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"revisionComment\":\"1.2.3\""));
		Assert.assertTrue(response.responseBody().contains("\"timestamp\":\"2016-01-03T14:15:00.000Z\""));

		Assert.assertFalse(response.responseBody().contains("0.9.5"));
	}
}
