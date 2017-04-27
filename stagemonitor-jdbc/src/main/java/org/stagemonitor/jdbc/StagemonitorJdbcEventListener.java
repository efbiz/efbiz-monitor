package org.stagemonitor.jdbc;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import com.uber.jaeger.context.TracingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.Stagemonitor;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.core.metrics.metrics2.MetricName;
import org.stagemonitor.tracing.AbstractExternalRequest;
import org.stagemonitor.tracing.TracingPlugin;
import org.stagemonitor.tracing.metrics.ExternalRequestMetricsSpanEventListener;
import org.stagemonitor.tracing.profiler.Profiler;
import org.stagemonitor.util.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

import static org.stagemonitor.core.metrics.metrics2.MetricName.name;

public class StagemonitorJdbcEventListener extends SimpleJdbcEventListener {

	private static final Logger logger = LoggerFactory.getLogger(StagemonitorJdbcEventListener.class);
	private static final String DB_STATEMENT = "db.statement";

	private final JdbcPlugin jdbcPlugin;

	private final MetricName.MetricNameTemplate getConnectionTemplate = name("get_jdbc_connection").templateFor("url");

	private final ConcurrentMap<DataSource, MetaData> dataSourceUrlMap = new ConcurrentHashMap<DataSource, MetaData>();

	private CorePlugin corePlugin;
	private TracingPlugin tracingPlugin;

	public StagemonitorJdbcEventListener() {
		this(Stagemonitor.getConfiguration());
	}

	public StagemonitorJdbcEventListener(ConfigurationRegistry configuration) {
		this.jdbcPlugin = configuration.getConfig(JdbcPlugin.class);
		tracingPlugin = configuration.getConfig(TracingPlugin.class);
		corePlugin = configuration.getConfig(CorePlugin.class);
	}

	@Override
	public void onConnectionWrapped(ConnectionInformation connectionInformation) {
		final Metric2Registry metricRegistry = corePlugin.getMetricRegistry();
		// at the moment stagemonitor only supports monitoring connections initiated via a DataSource
		if (connectionInformation.getDataSource() instanceof DataSource && corePlugin.isInitialized()) {
			DataSource dataSource = (DataSource) connectionInformation.getDataSource();
			ensureUrlExistsForDataSource(dataSource, connectionInformation.getConnection());
			MetaData metaData = dataSourceUrlMap.get(dataSource);
			metricRegistry.timer(getConnectionTemplate.build(metaData.serviceName)).update(connectionInformation.getTimeToGetConnectionNs(), TimeUnit.NANOSECONDS);
		}
	}

	private DataSource ensureUrlExistsForDataSource(DataSource dataSource, Connection connection) {
		if (!dataSourceUrlMap.containsKey(dataSource)) {
			final DatabaseMetaData metaData;
			try {
				metaData = connection.getMetaData();
				final MetaData meta = new MetaData(metaData.getUserName(), metaData.getURL(), metaData.getDatabaseProductName());
				dataSourceUrlMap.put(dataSource, meta);
			} catch (SQLException e) {
				logger.warn(e.getMessage(), e);
			}
		}
		return dataSource;
	}

	private static class MetaData {
		private final String serviceName;
		private final String userName;
		private final String productName;

		private MetaData(String userName, String url, String productName) {
			this.userName = userName;
			this.productName = productName;
			serviceName = userName + "@" + url;
		}

	}

	@Override
	public void onBeforeAnyExecute(StatementInformation statementInformation) {
		tracingPlugin.getRequestMonitor().monitorStart(new MonitoredJdbcRequest(tracingPlugin));
	}

	@Override
	public void onAfterAnyExecute(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
		if (!TracingUtils.getTraceContext().isEmpty()) {
			final Span span = TracingUtils.getTraceContext().getCurrentSpan();
			if (statementInformation.getConnectionInformation().getDataSource() instanceof DataSource && jdbcPlugin.isCollectSql()) {
				MetaData metaData = dataSourceUrlMap.get(statementInformation.getConnectionInformation().getDataSource());
				Tags.PEER_SERVICE.set(span, metaData.serviceName);
				span.setTag("db.type", metaData.productName);
				span.setTag("db.user", metaData.userName);

				if (StringUtils.isNotEmpty(statementInformation.getSql())) {
					String sql = getSql(statementInformation.getSql(), statementInformation.getSqlWithValues());
					Profiler.addIOCall(sql, timeElapsedNanos);
					span.setTag(ExternalRequestMetricsSpanEventListener.EXTERNAL_REQUEST_METHOD, sql.substring(0, sql.indexOf(' ')).toUpperCase());
					span.setTag(DB_STATEMENT, sql);
				}

			}
			tracingPlugin.getRequestMonitor().monitorStop();
		}
	}

	private String getSql(String prepared, String sql) {
		if (StringUtils.isEmpty(sql) || !jdbcPlugin.isCollectPreparedStatementParameters()) {
			sql = prepared;
		}
		return sql.trim();
	}

	private static class MonitoredJdbcRequest extends AbstractExternalRequest {

		private MonitoredJdbcRequest(TracingPlugin tracingPlugin) {
			super(tracingPlugin.getTracer());
		}

		@Override
		protected String getType() {
			return "jdbc";
		}

	}
}
