package org.stagemonitor.tracing.reporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.tracing.TracingPlugin;
import org.stagemonitor.tracing.utils.SpanUtils;

import java.util.Map;

public class LoggingSpanReporter extends SpanReporter {

	private static final Logger logger = LoggerFactory.getLogger(LoggingSpanReporter.class);
	private TracingPlugin tracingPlugin;

	@Override
	public void init(ConfigurationRegistry configuration) {
		this.tracingPlugin = configuration.getConfig(TracingPlugin.class);
	}

	@Override
	public void report(SpanContextInformation context) {
		logger.info(getLogMessage(context));
	}

	String getLogMessage(SpanContextInformation context) {
		ReadbackSpan span = context.getReadbackSpan();
		StringBuilder sb = new StringBuilder();
		sb.append("\n###########################\n");
		sb.append("# Span report             #\n");
		sb.append("###########################\n");
		appendLine(sb, "name", span.getName());
		appendLine(sb, "duration", span.getDuration());
		appendLine(sb, "traceId", span.getTraceId());
		appendLine(sb, "spanId", span.getId());
		appendLine(sb, "parentId", span.getParentId());
		sb.append("###########################\n");
		sb.append("# Tags                    #\n");
		sb.append("###########################\n");
		for (Map.Entry<String, Object> entry : span.getTags().entrySet()) {
			if (!SpanUtils.CALL_TREE_JSON.equals(entry.getKey())) {
				appendLine(sb, entry.getKey(), entry.getValue());
			}
		}
		sb.append("###########################\n");
		return sb.toString();
	}

	private void appendLine(StringBuilder sb, Object key, Object value) {
		if (value != null) {
			sb.append("# ").append(key).append(": ").append(value).append('\n');
		}
	}

	@Override
	public boolean isActive(SpanContextInformation spanContext) {
		return tracingPlugin.isLogSpans();
	}
}
