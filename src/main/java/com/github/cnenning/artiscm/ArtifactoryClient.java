package com.github.cnenning.artiscm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import com.thoughtworks.go.plugin.api.logging.Logger;

public class ArtifactoryClient {

	protected Logger logger = Logger.getLoggerFor(getClass());

	public void downloadFiles(String url, HttpClient client, UserPw userPw, File targetDir, String patternStr) throws ClientProtocolException, IOException {
		// add trailing slash
		if (!url.endsWith("/")) {
			url += "/";
		}

		Pattern pattern = null;
		if (patternStr != null && !patternStr.isEmpty()) {
			pattern = Pattern.compile(patternStr);
		}

		List<Revision> files = files(url, client, userPw);
		for (Revision rev : files) {
			String filename = rev.revision;
			if (pattern != null) {
				Matcher matcher = pattern.matcher(filename);
				if (!matcher.matches()) {
					continue;
				}
			}

			filename = escapeName(filename);
			String completeUrl = url + filename;

			logger.info("downloading " + completeUrl);

			HttpGet httpget = new HttpGet(completeUrl);
			configureMethod(httpget, userPw);
			HttpResponse response = client.execute(httpget);
			try {
				InputStream contentStream = response.getEntity().getContent();
				FileOutputStream outStream = new FileOutputStream(new File(targetDir, rev.revision));
				IOUtils.copy(contentStream, outStream);
			} finally {
				EntityUtils.consumeQuietly(response.getEntity());
			}
		}
	}

	protected void configureMethod(HttpGet httpget, UserPw userPw) {
		if (userPw != null && userPw.username != null && userPw.password != null) {
			String basicAuthVal = userPw.username + ":" + userPw.password;
			try {
				byte[] encoded = new Base64().encode(basicAuthVal.getBytes("UTF-8"));
				basicAuthVal = new String(encoded, "UTF-8");
				httpget.addHeader("Authorization", "Basic " + basicAuthVal);
			} catch (UnsupportedEncodingException e) {
				logger.error("no utf-8", e);
			}
		}
	}

	protected String escapeName(String str) {
		try
		{
			return URLEncoder.encode(str, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			e.printStackTrace();
			return str;
		}
	}

	public String checkSubDirs(final String url, final String pattern, final HttpClient client, UserPw userPw) throws ClientProtocolException, IOException {
		Revision latest = latestChild(url, pattern, true, client, userPw);
		return latest != null ? latest.revision : null;
	}

	public String checkFiles(String url, String patternStr, HttpClient client, UserPw userPw) throws ClientProtocolException, IOException {
		Revision latest = latestChild(url, patternStr, false, client, userPw);
		return latest != null ? latest.revision : null;
	}

	protected <T> T downloadHtml(String url, HttpClient client, UserPw userPw, Callback<T> callback) throws ClientProtocolException, IOException {
		// add trailing slash
		if (!url.endsWith("/")) {
			url += "/";
		}

		HttpGet httpget = new HttpGet(url);
		configureMethod(httpget, userPw);
		HttpResponse response = client.execute(httpget);
		try {
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode > 399) {
				throw new IOException("status code: " + statusCode);
			}
			String charsetName = charsetName(response);
			InputStream contentStream = response.getEntity().getContent();
			Document document = Jsoup.parse(contentStream, charsetName, url);
		
			return callback.callback(url, client, document);

		} finally {
			EntityUtils.consumeQuietly(response.getEntity());
		}
	}

	protected final static String CHARSET_KEY = "; charset=";

	protected String charsetName(HttpResponse response) {
		Header contentType = response.getFirstHeader("Content-Type");
		if (contentType != null) {
			String value = contentType.getValue();
			if (value != null) {
				int indexOfCharset = value.indexOf(CHARSET_KEY);
				if (indexOfCharset > 0) {
					return value.substring(indexOfCharset + CHARSET_KEY.length());
				}
			}
		}
		return null;
	}

	public static final String PARENT_DIR = "../";

	protected boolean isDir(String href) {
		return href != null && href.endsWith("/") && !href.equals(PARENT_DIR);
	}

	public static final Set<String> HASH_FILE_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"md5",
		"sha1")));

	protected boolean isFile(String href) {
		if (href != null && !href.endsWith("/")) {
			int lastIndexOfDot = href.lastIndexOf(".");
			if (lastIndexOfDot > 0) {
				String extension = href.substring(lastIndexOfDot + 1, href.length());
				return !HASH_FILE_EXTENSIONS.contains(extension);
			} else {
				return true;
			}
		}
		return false;
	}

	public Revision latestRevision(final String url, final String versionRegex, final HttpClient client, final UserPw userPw)
			throws ClientProtocolException, IOException {
		return downloadHtml(url, client, userPw, new Callback<Revision>(){
			@Override
			public Revision callback(String url, HttpClient client, Document document) throws IOException
			{
				List<Revision> revisions = revisions(url, versionRegex, client, userPw, document, null);
				Revision rev = !revisions.isEmpty() ? revisions.get(0) : null;
				if (rev == null) {
					logger.info("Could not find revision!\nurl: " + url + "\nversion regex: " + versionRegex + "\nhtml: " + document.outerHtml());

					// build empty/null rev to avoid NPE
					rev = new Revision();
					rev.revision = "bad data, check logs";
					rev.timestamp = new Date(0);
					rev.files = Collections.emptyList();
				}
				return rev;
			}
		});
	}

	public List<Revision> latestRevisionsSince(final String url, final String versionRegex, final HttpClient client, final UserPw userPw, final Date since)
			throws ClientProtocolException, IOException {
		return downloadHtml(url, client, userPw, new Callback<List<Revision>>(){
			@Override
			public List<Revision> callback(String url, HttpClient client, Document document) throws IOException
			{
				return revisions(url, versionRegex, client, userPw, document, since);
			}
		});
	}

	protected List<Revision> revisions(String url, String versionRegex, HttpClient client, UserPw userPw, Document document, Date since)
			throws ClientProtocolException, IOException {
		List<Revision> revisions = new ArrayList<>();
		Elements links = document.select("a");
		Pattern pattern = versionRegex != null
			? Pattern.compile(versionRegex)
			: null;
		for (Element link : links) {
			String href = link.attr("href");
			if (isDir(href)) {
				boolean matches = true;
				if (pattern != null) {
					matches = pattern.matcher(href).matches();
				}
				if (matches) {
					Revision rev = elementToRev(link, since, url);
					if (rev != null) {
						revisions.add(rev);
						if (since != null) {
							filesForRev(url, client, userPw, rev);
						}
					}
				}
			}
		}
		if (since == null && !revisions.isEmpty()) {
			// assume revisions are ordered by date
			Revision lastRev = revisions.get(revisions.size() - 1);
			filesForRev(url, client, userPw, lastRev);
			revisions = Arrays.asList(lastRev);
		}
		return revisions;
	}

	protected void filesForRev(String url, HttpClient client, UserPw userPw, Revision rev) throws ClientProtocolException, IOException
	{
		String revUrl = url + rev.revision;
		List<Revision> fileRevs = files(revUrl, client, userPw);
		List<String> files = new ArrayList<>(fileRevs.size());
		for (Revision fileRev : fileRevs) {
			files.add(fileRev.revision);
		}
		rev.files = files;
	}

	protected List<Revision> files(String url, HttpClient client, UserPw userPw) throws ClientProtocolException, IOException {
		return children(url, false, client, userPw);
	}

	protected List<Revision> children(final String url, final boolean directories, final HttpClient client, final UserPw userPw)
			throws ClientProtocolException, IOException {
		return downloadHtml(url, client, userPw, new Callback<List<Revision>>() {
			@Override
			public List<Revision> callback(String url, HttpClient client, Document document)
			{
				List<Revision> revisions = new ArrayList<>();
				Elements links = document.select("a");
				for (Element link : links) {
					String href = link.attr("href");
					boolean isCorrectType = directories
						? isDir(href)
						: isFile(href);
					if (isCorrectType) {
						Revision rev = elementToRev(link, null, url);
						if (rev != null) {
							revisions.add(rev);
						}
					}
				}
				return revisions;
			}
		});
	}

	public Revision latestChild(String url, String patternStr, boolean directory, HttpClient client, UserPw userPw)
			throws ClientProtocolException, IOException {
		Pattern pattern = null;
		if (patternStr != null && !patternStr.isEmpty()) {
			pattern = Pattern.compile(patternStr);
		}

		Revision latest = null;
		List<Revision> children = children(url, directory, client, userPw);
		for (Revision rev : children) {
			String name = rev.revision;
			Matcher matcher = null;
			if (pattern != null) {
				matcher = pattern.matcher(name);
				if (!matcher.matches()) {
					continue;
				}
			}
			if (latest == null || latest.timestamp.compareTo(rev.timestamp) < 0) {
				latest = rev;
				if (matcher != null) {
					int groupCount = matcher.groupCount();
					List<String> groups = new ArrayList<>(groupCount);
					for (int i=0; i<groupCount; i++) {
						String group = matcher.group(i);
						groups.add(group);
					}
					latest.matchingGroups = groups;
				}
			}
		}
		return latest;
	}

	protected Revision elementToRev(Element link, Date since, String url) {
		String name = link.text();
		Node nextSibling = link.nextSibling();
		if (nextSibling instanceof TextNode) {
			TextNode textNode = (TextNode) nextSibling;
			String text = textNode.text();
			Date date = findDateInText(text, url);

			if (since == null || date.getTime() > since.getTime()) {
				// remove trailing slash
				if (name.endsWith("/")) {
					name = name.substring(0, name.length() - 1);
				}

				Revision rev = new Revision();
				rev.revision = name;
				rev.comment = name;
				rev.timestamp = date;
				return rev;
			}
		}
		return null;
	}

	public static final String HTML_DATE_FORMAT_STR = "dd-MMM-yyyy HH:mm";
	public static final DateTimeFormatter HTML_DATE_FORMATTER = DateTimeFormat.forPattern(HTML_DATE_FORMAT_STR);

	protected Date findDateInText(String text, String url) {
		if (text != null) {
			text = text.trim();
			try {
				text = text.substring(0, HTML_DATE_FORMAT_STR.length());
				return HTML_DATE_FORMATTER.parseDateTime(text).toDate();
			} catch (Exception e) {
				logger.warn("could not parse date: '" + text + "', url: " + url);
				logger.debug(e.getMessage(), e);
			}
		}
		return new Date(0);
	}

	public static class Revision {
		String revision;
		Date timestamp;
		String comment;
		List<String> files;
		List<String> matchingGroups;
	}

	protected static interface Callback<T> {
		T callback(String url, HttpClient client, Document document) throws IOException;
	}
}
