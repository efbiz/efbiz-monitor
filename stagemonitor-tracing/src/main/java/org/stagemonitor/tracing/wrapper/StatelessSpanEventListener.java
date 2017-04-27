package org.stagemonitor.tracing.wrapper;

/**
 * You should use this class as a base class for your {@link SpanEventListener} if it is stateless. That means it does
 * not use instance variables which are dependent on the parameters of any {@link SpanEventListener} method.
 * <p/>
 * In this case, the {@link SpanEventListener} and the {@link SpanEventListenerFactory} are the same object.
 */
public abstract class StatelessSpanEventListener extends AbstractSpanEventListener implements SpanEventListenerFactory {

	/**
	 * Reuses the {@link AbstractSpanEventListener} instance as is is known to be stateless.
	 *
	 * @return the reused {@link AbstractSpanEventListener} instance
	 */
	@Override
	public final SpanEventListener create() {
		return this;
	}
}
