package com.github.cnenning.artiscm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

public class ArtifactoryClient {

	public void downloadFiles(String url, HttpClient client, String targetDirPath) throws ClientProtocolException, IOException {
		// create target dir
		File targetDir = new File(targetDirPath);
		if (!targetDir.exists()) {
			System.out.print("creating target dir: ");
			System.out.println(targetDirPath);
			targetDir.mkdirs();
		}

		// add trailing slash
		if (!url.endsWith("/")) {
			url += "/";
		}

		List<Revision> files = files(url, client);
		for (Revision rev : files) {
			String filename = escapeName(rev.revision);
			String completeUrl = url + filename;

			// print to sys out to see it in go-console-view
			System.out.print("downloading ");
			System.out.println(completeUrl);

			HttpGet httpget = new HttpGet(completeUrl);
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

	public boolean checkConnection(String url, HttpClient client) throws ClientProtocolException, IOException {
		Boolean ok = downloadHtml(url, client, new Callback<Boolean>() {
			@Override
			public Boolean callback(String url, HttpClient client, Document document)
			{
				return Boolean.valueOf(containsSubDir(document));
			}
		});
		return ok.booleanValue();
	}

	protected <T> T downloadHtml(String url, HttpClient client, Callback<T> callback) throws ClientProtocolException, IOException {
		// add trailing slash
		if (!url.endsWith("/")) {
			url += "/";
		}

		HttpGet httpget = new HttpGet(url);
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

	protected boolean containsSubDir(Document document) {
		Elements links = document.select("a");
		for (Element link : links) {
			String href = link.attr("href");
			if (isDir(href)) {
				return true;
			}
		}
		return false;
	}

	public Revision latestRevision(String url, HttpClient client) throws ClientProtocolException, IOException {
		return downloadHtml(url, client, new Callback<Revision>(){
			@Override
			public Revision callback(String url, HttpClient client, Document document) throws IOException
			{
				List<Revision> revisions = revisions(url, client, document, null);
				return !revisions.isEmpty() ? revisions.get(0) : null;
			}
		});
	}

	public List<Revision> latestRevisionsSince(String url, HttpClient client, final Date since) throws ClientProtocolException, IOException {
		return downloadHtml(url, client, new Callback<List<Revision>>(){
			@Override
			public List<Revision> callback(String url, HttpClient client, Document document) throws IOException
			{
				return revisions(url, client, document, since);
			}
		});
	}

	protected List<Revision> revisions(String url, HttpClient client, Document document, Date since) throws ClientProtocolException, IOException {
		List<Revision> revisions = new ArrayList<>();
		Elements links = document.select("a");
		for (Element link : links) {
			String href = link.attr("href");
			if (isDir(href)) {
				Revision rev = elementToRev(link, since);
				if (rev != null) {
					revisions.add(rev);
					if (since != null) {
						filesForRev(url, client, rev);
					}
				}
			}
		}
		if (since == null && !revisions.isEmpty()) {
			// assume revisions are ordered by date
			Revision lastRev = revisions.get(revisions.size() - 1);
			filesForRev(url, client, lastRev);
			revisions = Arrays.asList(lastRev);
		}
		return revisions;
	}

	protected void filesForRev(String url, HttpClient client, Revision rev) throws ClientProtocolException, IOException
	{
		String revUrl = url + rev.revision;
		List<Revision> fileRevs = files(revUrl, client);
		List<String> files = new ArrayList<>(fileRevs.size());
		for (Revision fileRev : fileRevs) {
			files.add(fileRev.revision);
		}
		rev.files = files;
	}

	protected List<Revision> files(String url, HttpClient client) throws ClientProtocolException, IOException {
		return downloadHtml(url, client, new Callback<List<Revision>>() {
			@Override
			public List<Revision> callback(String url, HttpClient client, Document document)
			{
				List<Revision> revisions = new ArrayList<>();
				Elements links = document.select("a");
				for (Element link : links) {
					String href = link.attr("href");
					if (isFile(href)) {
						Revision rev = elementToRev(link, null);
						if (rev != null) {
							revisions.add(rev);
						}
					}
				}
				return revisions;
			}
		});
	}

	protected Revision elementToRev(Element link, Date since) {
		String name = link.text();
		Node nextSibling = link.nextSibling();
		if (nextSibling instanceof TextNode) {
			TextNode textNode = (TextNode) nextSibling;
			String text = textNode.text();
			Date date = findDateInText(text);

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

	public static final DateFormat HTML_DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy HH:mm");

	protected Date findDateInText(String text) {
		if (text != null) {
			text = text.trim();
			// note: text contains more than just date which is OK for this java api
			try
			{
				return HTML_DATE_FORMAT.parse(text);
			}
			catch (ParseException e)
			{
				// we could log this, but never mind
			}
		}
		return new Date(0);
	}

	public static class Revision {
		String revision;
		Date timestamp;
		String comment;
		List<String> files;
	}

	protected static interface Callback<T> {
		T callback(String url, HttpClient client, Document document) throws IOException;
	}
}
