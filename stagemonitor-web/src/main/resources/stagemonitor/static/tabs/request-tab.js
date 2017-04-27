function renderRequestTab(requestTrace) {
	if (requestTrace) {
		stagemonitor.requestTrace = requestTrace;
	}
	var $requestTab = $("#request-tab");
	if (!stagemonitor.requestTrace) {
		$requestTab.hide();
		// show metrics tab as fallback
		if ($("#call-stack-tab").hasClass('active') || $requestTab.hasClass('active')) {
			$("#metrics-tab").addClass('active');
			$("#stagemonitor-metrics").addClass('active');
			$("#metrics-tab").find("a").trigger('click');
		}
		return;
	} else {
		$requestTab.show();
	}

	var thresholdExceededGlobal = false;
	var requestsMetrics = processRequestsMetrics(stagemonitor.requestTrace);

	function processRequestsMetrics(requestData) {
		var exceededThreshold = function (key, value) {
			switch (key) {
				case "external_requests.jdbc.count": {
					return value > localStorage.getItem("widget-settings-db-count-threshold");
				}
				case "duration_ms":
					return value > localStorage.getItem("widget-settings-execution-threshold-milliseconds");
				case "error":
					return value && localStorage.getItem("widget-settings-notify-on-error") != "false";
				default:
					return false;
			}
		};

		var commonNames = {
			"name": {name: "Request name", description: "Usecase / Request verb and path"},
			"duration_ms": {name: "Server execution time in ms", description: "The time in ms it took to process the request in the server."},
			"duration_cpu_ms": {name: "Execution time for the CPU", description: "The amount of time in ms it took the CPU to process the request."},
			"error": {name: "Error", description: "true, if there was an error while processing the request, false otherwise."},
			"exception.class": {name: "Exception class", description: "The class of the thrown exception. (Only present, if there was a exception)"},
			"exception.message": {name: "Exception message", description: "The message of the thrown exception. (Only present, if there was a exception)"},
			"exception.stack_trace": {name: "Exception stack trace", description: ""},
			"stackTrace": {name: "Stacktrace", descrption: "The full stack trace of the thrown exception. (Only present, if there was a exception)"},
			"parameters": {name: "Parameters", description: "The query sting of the request. You can obfuscate sensitive parameters.", formatter: function(params) {
				var value = "";
				for (var key in params) {
					value += ", " + key + ": " + params[key];
				}
				return value.substring(2);
			}},
			"clientIp": {name: "Client IP", description: "The IP of the client who initiated the HTTP request."},
			"http.url": {name: "URL", description: "The requested URL."},
			"http.status_code": {name: "Status code", description: "The HTTP status code of a request."},
			"method": {name: "Method", description: "The HTTP method of the request."},
			"@timestamp": {name: "Timestamp", description: "The date and time the request entered the server."},
			"application": {name: "Application name", description: "The name of the application that handled the request. This value is obtained from the display-name of web.xml. Alternatively, you can use the stagemonitor.applicationName property of the stagemonitor.properties configuration file."},
			"host": {name: "Host accessing", description: "The name of the host of the server that handled the request."},
			"instance": {name: "Instance", description: "The name of the instance of the application that handled the request. The instance name is useful, if you have different environments for the same application (maybe even on the same host). However, it leads to errors if you have a application with the same instance name on the same host. By default, the instance name is the domain name of the server and it is obtained from the first incoming request. You can also choose to set a fixed instance name."},
			"externalRequestStats": {name: "Execution time and count of external requests", description: "", formatter: function(externalRequestStats) {
				var value = "";
				for (var i = 0; i < externalRequestStats.length; i++) {
					var stats = externalRequestStats[i];
					if (i > 0) {
						value += "\n";
					}
					value += stats.requestType + ":\n";
					value += "Number of requests: " + stats.executionCount + "\n";
					value += "Execution time: " + stats.executionTime.toFixed(2) + " ms\n";
				}
				return value;
			}}
		};
		var excludedProperties = ["call_tree_json", "http.headers", "user_agent", "pageLoadTime", "jaeger"];
		var metrics = [];
		var flatRequestData = dotify(requestData);
		for (var key in flatRequestData) {

			var isKeyIncluded = true;
			for (var i = 0; i < excludedProperties.length; i++) {
				var excludedProperty = excludedProperties[i];
				if (key.startsWith(excludedProperty)) {
					isKeyIncluded = false;
					break;
				}
			}
			if (isKeyIncluded && flatRequestData[key] != null) {
				var nameAndDescription = commonNames[key] || {name: key, description: ""};
				var valueFormatter = nameAndDescription.formatter || function(val) {
					return val.toString();
				};
				var thresholdExceeded = exceededThreshold(key, flatRequestData[key]);
				if (thresholdExceeded) {
					thresholdExceededGlobal = true;
				}

				metrics.push({
					key: key,
					name: nameAndDescription.name,
					description: nameAndDescription.description,
					value: valueFormatter(flatRequestData[key]),
					exceededThreshold: thresholdExceeded
				});
			}
		}
		return {
			metrics: metrics,
			userAgent: requestData.user_agent,
			headers: (requestData.http || {}).headers
		};
	}

	function dotify(obj) {
		var res = {};
		(function recurse(obj, current) {
			for(var key in obj) {
				var value = obj[key];
				var newKey = (current ? current + "." + key : key);  // joined key with dot
				if(value && typeof value === "object") {
					recurse(value, newKey);  // it's a nested object, so do it again
				} else {
					res[newKey] = value;  // it's not an object, so set the property
				}
			}
		})(obj);
		return res;
	}

	stagemonitor.thresholdExceeded |= thresholdExceededGlobal;

	var pageLoadTimeModel = getPageLoadTimeModel(stagemonitor.requestTrace.pageLoadTime);
	if (pageLoadTimeModel) {
		stagemonitor.thresholdExceeded |= pageLoadTimeModel.totalThresholdExceeded;
		stagemonitor.thresholdExceeded |= pageLoadTimeModel.serverThresholdExceeded;
	}

	$.get(stagemonitor.contextPath + "/stagemonitor/static/tabs/request-tab.html", function (template) {
		var metricsTemplate = Handlebars.compile($(template).html());
		var renderedMetricsTemplate = metricsTemplate(requestsMetrics);
		var $stagemonitorRequest = $("#stagemonitor-request");
		$stagemonitorRequest.html(renderedMetricsTemplate);
		$(".tip").tooltip({html: true});
		doRenderPageLoadTime(pageLoadTimeModel);
	});
}

function getPageLoadTimeModel(data) {
	if (!data) {
		return;
	}
	var thresholdMs = localStorage.getItem("widget-settings-execution-threshold-milliseconds");
	var model = {
		networkMs: data.timeToFirstByte - data.serverTime,
		networkPercent: (((data.timeToFirstByte - data.serverTime) / data.totalPageLoadTime) * 100).toFixed(2),
		serverMs: data.serverTime,
		serverPercent: ((data.serverTime / data.totalPageLoadTime) * 100).toFixed(2),
		serverThresholdExceeded: data.serverTime > thresholdMs,
		domProcessingMs: data.domProcessing,
		domProcessingPercent: ((data.domProcessing / data.totalPageLoadTime) * 100).toFixed(2),
		pageRenderingMs: data.pageRendering,
		pageRenderingPercent: ((data.pageRendering / data.totalPageLoadTime) * 100).toFixed(2),
		totalMs: data.totalPageLoadTime,
		totalThresholdExceeded: data.totalPageLoadTime > thresholdMs
	};
	model["pageRenderingPercent"] = (100 - model.networkPercent - model.serverPercent - model.domProcessingPercent).toFixed(2);
	return model;
}

function doRenderPageLoadTime(model) {
	if (!model) {
		return;
	}

	$.get(stagemonitor.contextPath + "/stagemonitor/static/tabs/request-tab-page-load-time.html", function (template) {
		var pageLoadTimeTemplate = Handlebars.compile($(template).html());
		var renderedMetricsTemplate = pageLoadTimeTemplate(model);
		var $stagemonitorRequest = $("#stagemonitor-request");
		$stagemonitorRequest.prepend(renderedMetricsTemplate);
		$(".tip").tooltip({html: true});
	});
}
