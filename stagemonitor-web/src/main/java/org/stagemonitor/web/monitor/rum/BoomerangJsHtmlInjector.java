package org.stagemonitor.web.monitor.rum;

import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.tracing.SpanContextInformation;
import org.stagemonitor.web.WebPlugin;
import org.stagemonitor.web.monitor.filter.HtmlInjector;

import java.util.concurrent.TimeUnit;

public class BoomerangJsHtmlInjector extends HtmlInjector {

	public static final String BOOMERANG_FILENAME = "boomerang-56c823668fc.min.js";
	private WebPlugin webPlugin;
	private String boomerangTemplate;
	private ConfigurationRegistry configuration;

	@Override
	public void init(HtmlInjector.InitArguments initArguments) {
		this.configuration = initArguments.getConfiguration();
		this.webPlugin = initArguments.getConfiguration().getConfig(WebPlugin.class);
		this.boomerangTemplate = buildBoomerangTemplate(initArguments.getServletContext().getContextPath());
	}

	private String buildBoomerangTemplate(String contextPath) {
		String beaconUrl = webPlugin.isRealUserMonitoringEnabled() ?
				"      beacon_url: " + "'" + contextPath + "/stagemonitor/public/rum'" + ",\n" : "";
		return "<script src=\"" + contextPath + "/stagemonitor/public/static/rum/" + BOOMERANG_FILENAME + "\"></script>\n" +
				"<script>\n" +
				"   BOOMR.init({\n" +
				beaconUrl +
				"      log: null\n" +
				"   });\n" +
				"   BOOMR.addVar(\"requestName\", \"${requestName}\");\n" +
				"   BOOMR.addVar(\"serverTime\", ${serverTime});\n" +
				"</script>";
	}

	@Override
	public boolean isActive(HtmlInjector.IsActiveArguments isActiveArguments) {
		// if widget is enabled, inject as well to render page load time statistics in widget
		// metrics won't be collected in this case, because the beacon_url is then set to null
		return webPlugin.isRealUserMonitoringEnabled() || webPlugin.isWidgetAndStagemonitorEndpointsAllowed(isActiveArguments.getHttpServletRequest(), configuration);
	}

	@Override
	public void injectHtml(HtmlInjector.InjectArguments injectArguments) {
		final SpanContextInformation spanContext = injectArguments.getSpanContext();
		if (spanContext == null || spanContext.getSpan() == null) {
			return;
		}
		injectArguments.setContentToInjectBeforeClosingBody(boomerangTemplate
				.replace("${requestName}", String.valueOf(spanContext.getOperationName()))
				.replace("${serverTime}", Long.toString(TimeUnit.NANOSECONDS.toMillis(spanContext.getDurationNanos()))));
	}

}
