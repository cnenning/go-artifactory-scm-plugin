package com.github.cnenning.artiscm;

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Assert;
import org.junit.Test;

public class ArtifactoryClientTests {

	@Test
	public void charsetName() {
		HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, null));
		response.addHeader("Content-Type", "text/html; charset=UTF-8");

		String charsetName = new ArtifactoryClient().charsetName(response);

		Assert.assertEquals("UTF-8", charsetName);
	}

	@Test
	public void charsetNameNotPresent() {
		HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, null));

		String charsetName = new ArtifactoryClient().charsetName(response);

		Assert.assertNull(charsetName);
	}

	@Test
	public void charsetNameHeaderIncomplete() {
		HttpResponse response = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, null));
		response.addHeader("Content-Type", "text/html");

		String charsetName = new ArtifactoryClient().charsetName(response);

		Assert.assertNull(charsetName);
	}

	@Test
	public void isDir() {
		boolean dir = new ArtifactoryClient().isDir("foo/");
		Assert.assertTrue(dir);
	}

	@Test
	public void isDirNull() {
		boolean dir = new ArtifactoryClient().isDir(null);
		Assert.assertFalse(dir);
	}

	@Test
	public void isDirNoSlash() {
		boolean dir = new ArtifactoryClient().isDir("foo");
		Assert.assertFalse(dir);
	}


	@Test
	public void isDirParent() {
		boolean dir = new ArtifactoryClient().isDir("../");
		Assert.assertFalse(dir);
	}

	@Test
	public void isFile() {
		boolean File = new ArtifactoryClient().isFile("foo");
		Assert.assertTrue(File);
	}

	@Test
	public void isFileNull() {
		boolean File = new ArtifactoryClient().isFile(null);
		Assert.assertFalse(File);
	}

	@Test
	public void isFileSlash() {
		boolean File = new ArtifactoryClient().isFile("foo/");
		Assert.assertFalse(File);
	}

	@Test
	public void isFileHash() {
		boolean File = new ArtifactoryClient().isFile("foo.md5");
		Assert.assertFalse(File);
	}

	@Test
	public void findDateInText() {
		//Date date = new ArtifactoryClient().findDateInText("01-Jan-2016 09:30", "");
		Date date = new ArtifactoryClient().findDateInText("16-Nov-2016 01:40 4.88 MB", "");
		Assert.assertTrue(date.getTime() > 0);
	}

	@Test
	public void findDateInTextInvalid() {
		Date date = new ArtifactoryClient().findDateInText("invalid", "");
		Assert.assertEquals(0, date.getTime());
	}

	@Test
	public void addBasicAuth() {
		HttpGet method = new HttpGet();
		UserPw userPw = new UserPw("username", "password");
		new ArtifactoryClient().configureMethod(method, userPw);
		Header authHeader = method.getFirstHeader("Authorization");
		Assert.assertEquals("Basic dXNlcm5hbWU6cGFzc3dvcmQ=", authHeader.getValue());
	}
}
