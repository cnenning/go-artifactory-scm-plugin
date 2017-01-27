package com.github.cnenning.artiscm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class ArtifactoryPluginTests {

	@Test
	public void dateFromApiInput() {
		AbstractArtifactoryPlugin plugin = new ArtifactoryScmPlugin();
		Map<String, Object> apiInput = new HashMap<>();
		Map<String, String> keysMap = new HashMap<>();
		apiInput.put("previous-revision", keysMap);
		keysMap.put("timestamp", "2017-01-26T10:50:00.000Z");
		Date date = plugin.dateFromApiInput(apiInput);
		System.out.println("got date: " + date);
		Assert.assertTrue(date.getTime() > 0);
	}
}
