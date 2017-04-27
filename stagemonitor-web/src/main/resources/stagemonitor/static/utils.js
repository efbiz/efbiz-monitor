var utils = (function () {
	RegExp.quote = function (str) {
		return (str + '').replace(/([.?*+^$[\]\\(){}|-])/g, "\\$1");
	};

	Object.values = function (obj) {
		var vals = [];
		for( var key in obj ) {
			if ( obj.hasOwnProperty(key) ) {
				vals.push(obj[key]);
			}
		}
		return vals;
	};

	// http://stackoverflow.com/questions/646628/how-to-check-if-a-string-startswith-another-string
	if (typeof String.prototype.startsWith != 'function') {
		String.prototype.startsWith = function (str){
			return this.slice(0, str.length) == str;
		};
	}

	function loadScript(path) {
		var result = $.Deferred(),
			script = document.createElement("script");
		script.async = "async";
		script.type = "text/javascript";
		script.src = path;
		script.onload = script.onreadystatechange = function (_, isAbort) {
			if (!script.readyState || /loaded|complete/.test(script.readyState)) {
				if (isAbort)
					result.reject();
				else
					result.resolve();
			}
		};
		script.onerror = function () {
			result.reject();
		};
		$("head")[0].appendChild(script);
		return  result.promise();
	}

	// toast notification settings
	$.growl(false, {
		allow_dismiss: true,
		placement: {
			from: "top",
			align: "right"
		},
		mouse_over: "pause",
		delay: 5000
	});

	return {
		loadScripts: function (scripts, callback) {
			$.when.apply(null, $.map(scripts, loadScript)).done(function () {
				callback();
			});
		},
		clone: function (object) {
			return JSON.parse(JSON.stringify(object));
		},
		objectToValuesArray: function (obj) {
			var data = [];
			for (var propertyName in obj) {
				data.push(obj[propertyName]);
			}
			return data;
		},
		generateUUID: function () {
			var d = window.performance && window.performance.now && window.performance.now() || new Date().getTime();
			return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
				var r = (d + Math.random() * 16) % 16 | 0;
				d = Math.floor(d / 16);
				return (c == 'x' ? r : (r & 0x7 | 0x8)).toString(16);
			});
		},
		successMessage: function(message) {
			$.growl(message, { type: "success" });
		},
		errorMessage: function(fallbackMessage, xhr) {
			var errorMessage = fallbackMessage;
			if (xhr && xhr.responseText && xhr.responseText.indexOf("<body") == -1) {
				errorMessage = utils.htmlEscape(xhr.responseText);
			}
			$.growl(errorMessage, { type: "danger" });
		},
		htmlEscape: function(str) {
			if (!str) {
				return str;
			}
			return String(str)
				.replace(/&/g, '&amp;')
				.replace(/"/g, '&quot;')
				.replace(/'/g, '&#39;')
				.replace(/</g, '&lt;')
				.replace(/>/g, '&gt;');
		}, 
		matches: function(metric, metricMatcher) {
			if (metricMatcher.name && metric.name !== metricMatcher.name) {
				return false;
			}

			for (var tag in metricMatcher.tags || {}) {
				var value = metricMatcher.tags[tag];
				if (value && value !== '*' && metric.tags[tag] !== value) {
					return false;
				}
			}
			return true;
		},
		metricAsString: function(metric, valueType) {
			return metric.name + JSON.stringify(metric.tags).split('"').join('') + ' ' + valueType;
		}
	}
})();