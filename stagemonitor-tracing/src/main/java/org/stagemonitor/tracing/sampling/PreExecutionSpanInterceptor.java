package org.stagemonitor.tracing.sampling;

import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.StagemonitorSPI;

/**
 * Allows implementers omit reporting a {@link io.opentracing.Span}
 * <p/>
 * The pre interceptor is executed before the monitoring of the current request starts.
 * The big difference to {@link PostExecutionSpanInterceptor} is that when a request is excluded from
 * reporting by a pre interceptor, the call tree won't be collected for that request.
 * <p/>
 * To add an interceptor, call <code>Stagemonitor.getPlugin(TracingPlugin.class).registerPreInterceptor(PostExecutionSpanInterceptor)</code>
 * or place a file under <code>META-INF/services/org.stagemonitor.tracing.sampling.PreExecutionSpanInterceptor</code>
 * and insert the canonical class name of your implementation.
 */
public abstract class PreExecutionSpanInterceptor implements StagemonitorSPI {

	public void init(ConfigurationRegistry configuration) {
	}

	/**
	 * This method is called before a span gets reported.
	 * <p/>
	 * The implementer of this method can decide whether or not to report the span or to exclude certain properties.
	 *
	 * @param context contextual information about the current report that is about to happen
	 */
	public abstract void interceptReport(PreExecutionInterceptorContext context);
}
