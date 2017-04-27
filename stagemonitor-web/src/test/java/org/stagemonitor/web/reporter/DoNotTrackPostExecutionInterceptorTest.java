package org.stagemonitor.web.reporter;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.tracing.MockTracer;
import org.stagemonitor.tracing.TracingPlugin;
import org.stagemonitor.web.WebPlugin;
import org.stagemonitor.web.monitor.MonitoredHttpRequest;
import org.stagemonitor.web.monitor.filter.StatusExposingByteCountingServletResponse;

import java.util.Collections;

import javax.servlet.FilterChain;

import io.opentracing.tag.Tags;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DoNotTrackPostExecutionInterceptorTest {

	private WebPlugin webPlugin;
	private ConfigurationRegistry configuration;

	@Before
	public void setUp() throws Exception {
		configuration = mock(ConfigurationRegistry.class);
		CorePlugin corePlugin = mock(CorePlugin.class);
		TracingPlugin tracingPlugin = mock(TracingPlugin.class);
		this.webPlugin = mock(WebPlugin.class);

		when(configuration.getConfig(CorePlugin.class)).thenReturn(corePlugin);
		when(configuration.getConfig(TracingPlugin.class)).thenReturn(tracingPlugin);
		when(configuration.getConfig(WebPlugin.class)).thenReturn(webPlugin);
		when(tracingPlugin.getRateLimitServerSpansPerMinute()).thenReturn(1000000d);
		when(tracingPlugin.getOnlyReportSpansWithName()).thenReturn(Collections.emptyList());
		when(corePlugin.getElasticsearchUrl()).thenReturn("http://localhost:9200");
		when(corePlugin.getElasticsearchUrls()).thenReturn(Collections.singletonList("http://localhost:9200"));
		ElasticsearchClient elasticsearchClient = mock(ElasticsearchClient.class);
		when(elasticsearchClient.isElasticsearchAvailable()).thenReturn(true);
		when(corePlugin.getElasticsearchClient()).thenReturn(elasticsearchClient);
		when(corePlugin.getMetricRegistry()).thenReturn(new Metric2Registry());
		when(webPlugin.isHonorDoNotTrackHeader()).thenReturn(true);
		when(tracingPlugin.getTracer()).thenReturn(new MockTracer());
	}

	@Test
	public void testHonorDoNotTrack() throws Exception {
		when(webPlugin.isHonorDoNotTrackHeader()).thenReturn(true);
		final MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("dnt", "1");
		final io.opentracing.Span span = new MonitoredHttpRequest(request, mock(StatusExposingByteCountingServletResponse.class),
				mock(FilterChain.class), configuration).createSpan();

		verify(span).setTag(Tags.SAMPLING_PRIORITY.getKey(), (short) 0);
	}

	@Test
	public void testDoNotTrackDisabled() throws Exception {
		when(webPlugin.isHonorDoNotTrackHeader()).thenReturn(true);
		final MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("dnt", "0");
		final io.opentracing.Span span = new MonitoredHttpRequest(request, mock(StatusExposingByteCountingServletResponse.class),
				mock(FilterChain.class), configuration).createSpan();

		verify(span, never()).setTag(Tags.SAMPLING_PRIORITY.getKey(), (short) 0);
	}

	@Test
	public void testNoDoNotTrackHeader() throws Exception {
		when(webPlugin.isHonorDoNotTrackHeader()).thenReturn(true);
		final MockHttpServletRequest request = new MockHttpServletRequest();
		final io.opentracing.Span span = new MonitoredHttpRequest(request, mock(StatusExposingByteCountingServletResponse.class),
				mock(FilterChain.class), configuration).createSpan();

		verify(span, never()).setTag(Tags.SAMPLING_PRIORITY.getKey(), (short) 0);
	}

	@Test
	public void testDontHonorDoNotTrack() throws Exception {
		when(webPlugin.isHonorDoNotTrackHeader()).thenReturn(false);
		final MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("dnt", "1");
		final io.opentracing.Span span = new MonitoredHttpRequest(request, mock(StatusExposingByteCountingServletResponse.class),
				mock(FilterChain.class), configuration).createSpan();

		verify(span, never()).setTag(Tags.SAMPLING_PRIORITY.getKey(), (short) 0);
	}

}
