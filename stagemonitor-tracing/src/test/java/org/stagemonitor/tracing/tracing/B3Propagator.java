package org.stagemonitor.tracing.tracing;

import org.stagemonitor.tracing.B3HeaderFormat;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

public class B3Propagator implements MockTracer.Propagator {
	@Override
	public <C> void inject(MockSpan.MockContext ctx, Format<C> format, C carrier) {
		((TextMap) carrier).put(B3HeaderFormat.SPAN_ID_NAME, Long.toHexString(ctx.spanId()));
		((TextMap) carrier).put(B3HeaderFormat.TRACE_ID_NAME, Long.toHexString(ctx.traceId()));
	}

	@Override
	public <C> MockSpan.MockContext extract(Format<C> format, C carrier) {
		return null;
	}
}
