import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

def cli = new CliBuilder(usage: "groovy ExportKibanaDashboard.groovy")
cli.with {
	width = 160
	_(longOpt: 'url', args: 1, 'The elasticsearch url (defaults to http://localhost:9200)')
	_(longOpt: 'type', args: 1, required: false, argName: 'index-pattern|search|dashboard|all', 'Which type to export')
	_(longOpt: 'id', args: 1, required: false, 'The id of the type to export')
	_(longOpt: 'curl', 'If set, exports as curl script')
}
def options = cli.parse(args)
if (!options) return

def addr = options.url ?: "http://localhost:9200"

if (options.type) {
	exportTypes(addr, options.type)
} else {
	["index-pattern", "search", "dashboard"].each { exportTypes(addr, it) }
}

private void exportTypes(addr, type) {
	println "Exporting ${type}s"
	new File("export/$type").mkdirs()
	def dashboards = new JsonSlurper().parse("$addr/.kibana/${type}/_search?q=*&stored_fields=&size=100".toURL())
	dashboards.hits.hits.each {
		println it._id
		def out = new FileOutputStream("export/$type/${it._id}.bulk")
		exportKibanaType(addr, type, it._id, out)
	}
}

def exportKibanaType(String addr, String type, String id, OutputStream out) {
	def hit = new JsonSlurper().parse("$addr/.kibana/$type/${URLEncoder.encode(id, "UTF-8")}".toURL())
	out << new JsonBuilder([index: [_index: hit._index, _type: hit._type, _id: hit._id, _version: 1, _version_type: "external_gte"]]).toString()
	out << "\n"
	out << new JsonBuilder(hit._source).toString()
	out << "\n"
	if (type == "dashboard") {
		new JsonSlurper().parseText(hit._source.panelsJSON).each {
			exportKibanaType(addr, it.type, it.id, out)
		}
	}
}

