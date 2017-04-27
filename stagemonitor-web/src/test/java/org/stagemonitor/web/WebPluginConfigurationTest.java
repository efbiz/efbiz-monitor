package org.stagemonitor.web;

import org.junit.Before;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class WebPluginConfigurationTest {

	private WebPlugin config;

	@Before
	public void before() throws Exception {
		ConfigurationRegistry configuration = new ConfigurationRegistry(Collections.singletonList(new WebPlugin()), Collections.emptyList(), "");
		config = configuration.getConfig(WebPlugin.class);
	}

	@Test
	public void testDefaultValues() {
		assertEquals(true, config.isCollectHttpHeaders());
		assertEquals(new LinkedHashSet<String>(Arrays.asList("cookie", "authorization", WebPlugin.STAGEMONITOR_SHOW_WIDGET)), config.getExcludeHeaders());
		final Collection<Pattern> confidentialQueryParams = config.getRequestParamsConfidential();
		final List<String> confidentialQueryParamsAsString = new ArrayList<String>(confidentialQueryParams.size());
		for (Pattern confidentialQueryParam : confidentialQueryParams) {
			confidentialQueryParamsAsString.add(confidentialQueryParam.toString());
		}
		assertEquals(Arrays.asList("(?i).*pass.*", "(?i).*credit.*", "(?i).*pwd.*"), confidentialQueryParamsAsString);

		final Map<Pattern, String> groupUrls = config.getGroupUrls();
		final Map<String, String> groupUrlsAsString = new HashMap<String, String>();
		for (Map.Entry<Pattern, String> entry : groupUrls.entrySet()) {
			groupUrlsAsString.put(entry.getKey().pattern(), entry.getValue());
		}

		final Map<String, String> expectedMap = new HashMap<String, String>();
		expectedMap.put("(.*).js$", "*.js");
		expectedMap.put("(.*).css$", "*.css");
		expectedMap.put("(.*).jpg$", "*.jpg");
		expectedMap.put("(.*).jpeg$", "*.jpeg");
		expectedMap.put("(.*).png$", "*.png");

		assertEquals(expectedMap, groupUrlsAsString);

		assertEquals(true, config.isRealUserMonitoringEnabled());
		assertEquals(false, config.isCollectPageLoadTimesPerRequest());
		assertEquals(false, config.isMonitorOnlySpringMvcRequests());

	}
}
