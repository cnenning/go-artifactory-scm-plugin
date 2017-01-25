package com.github.cnenning.artiscm;

import org.junit.Assert;
import org.junit.Test;


public class ReadPluginXmlTests {
	@Test
	public void buildUserAgent() {
		String userAgent = new ArtifactoryScmPlugin().buildUserAgent();
		String expectedStart = "go-plugin artifactory-scm ";
		System.out.println("got userAgent: " + userAgent);
		Assert.assertTrue(userAgent.startsWith(expectedStart));
		Assert.assertTrue(userAgent.length() > expectedStart.length());
	}
}
