package org.stagemonitor.tracing.profiler;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.core.metrics.MetricUtils;
import org.stagemonitor.core.util.JsonUtils;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.tracing.TracingPlugin;
import org.stagemonitor.tracing.sampling.PreExecutionInterceptorContext;
import org.stagemonitor.tracing.sampling.RateLimitingPreExecutionInterceptor;
import org.stagemonitor.tracing.utils.RateLimiter;
import org.stagemonitor.tracing.utils.SpanUtils;
import org.stagemonitor.tracing.wrapper.SpanWrapper;
import org.stagemonitor.tracing.wrapper.StatelessSpanEventListener;
import org.stagemonitor.util.StringUtils;

import io.opentracing.Span;

public class CallTreeSpanEventListener extends StatelessSpanEventListener {

	private final TracingPlugin tracingPlugin;
	private RateLimiter rateLimiter;

	public CallTreeSpanEventListener(TracingPlugin tracingPlugin) {
		this.tracingPlugin = tracingPlugin;
		rateLimiter = RateLimitingPreExecutionInterceptor.getRateLimiter(tracingPlugin.getProfilerRateLimitPerMinute());
		tracingPlugin.getProfilerRateLimitPerMinuteOption().addChangeListener(new ConfigurationOption.ChangeListener<Double>() {
			@Override
			public void onChange(ConfigurationOption<?> configurationOption, Double oldValue, Double newValue) {
				rateLimiter = RateLimitingPreExecutionInterceptor.getRateLimiter(newValue);
			}
		});
	}

	@Override
	public void onStart(SpanWrapper spanWrapper) {
		final SpanContextInformation contextInfo = SpanContextInformation.forSpan(spanWrapper);
		if (contextInfo.isSampled() && contextInfo.getPreExecutionInterceptorContext() != null) {
			determineIfEnableProfiler(contextInfo);
			if (contextInfo.getPreExecutionInterceptorContext().isCollectCallTree()) {
				contextInfo.setCallTree(Profiler.activateProfiling("total"));
			}
		}
	}

	private void determineIfEnableProfiler(SpanContextInformation spanContext) {
		final PreExecutionInterceptorContext interceptorContext = spanContext.getPreExecutionInterceptorContext();
		if (spanContext.isExternalRequest()) {
			interceptorContext.shouldNotCollectCallTree("this is a external request (span.kind=client)");
			return;
		}
		double callTreeRateLimit = tracingPlugin.getProfilerRateLimitPerMinute();
		if (!tracingPlugin.isProfilerActive()) {
			interceptorContext.shouldNotCollectCallTree("stagemonitor.profiler.active=false");
		} else if (callTreeRateLimit <= 0) {
			interceptorContext.shouldNotCollectCallTree("stagemonitor.profiler.sampling.rateLimitPerMinute <= 0");
		} else if (RateLimitingPreExecutionInterceptor.isRateExceeded(rateLimiter)) {
			interceptorContext.shouldNotCollectCallTree("rate limit is reached");
		}
	}

	@Override
	public void onFinish(SpanWrapper spanWrapper, String operationName, long durationNanos) {
		final SpanContextInformation contextInfo = SpanContextInformation.forSpan(spanWrapper);
		if (contextInfo.getCallTree() != null) {
			try {
				Profiler.stop();
				if (contextInfo.isSampled()) {
					determineIfExcludeCallTree(contextInfo);
					if (isAddCallTreeToSpan(contextInfo, operationName)) {
						addCallTreeToSpan(contextInfo, spanWrapper, operationName);
					}
				}
			} finally {
				Profiler.clearMethodCallParent();
			}
		}
	}

	private void determineIfExcludeCallTree(SpanContextInformation contextInfo) {
		final double percentileLimit = tracingPlugin.getExcludeCallTreeFromReportWhenFasterThanXPercentOfRequests();
		if (!MetricUtils.isFasterThanXPercentOfAllRequests(contextInfo.getDurationNanos(), percentileLimit, contextInfo.getTimerForThisRequest())) {
			contextInfo.getPostExecutionInterceptorContext().excludeCallTree("the duration of this request is faster than the percentile limit");
		}
	}

	private boolean isAddCallTreeToSpan(SpanContextInformation info, String operationName) {
		return info.getCallTree() != null
				&& info.getPostExecutionInterceptorContext() != null
				&& !info.getPostExecutionInterceptorContext().isExcludeCallTree()
				&& StringUtils.isNotEmpty(operationName);
	}

	private void addCallTreeToSpan(SpanContextInformation info, Span span, String operationName) {
		final CallStackElement callTree = info.getCallTree();
		callTree.setSignature(operationName);
		final double minExecutionTimeMultiplier = tracingPlugin.getMinExecutionTimePercent() / 100;
		if (minExecutionTimeMultiplier > 0d) {
			callTree.removeCallsFasterThan((long) (callTree.getExecutionTime() * minExecutionTimeMultiplier));
		}
		span.setTag(SpanUtils.CALL_TREE_JSON, JsonUtils.toJson(callTree));
		span.setTag(SpanUtils.CALL_TREE_ASCII, callTree.toString(true));
	}
}
