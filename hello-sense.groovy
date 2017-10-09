/**
 *  SimpliSafe integration for SmartThings
 *
 *  Copyright 2017 Felix Gorodishter
 *  Based on sleuth work done here: https://www.chameth.com/2016/04/10/sense-api/
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

preferences {
	input(name: "username", type: "text", title: "Username", required: "true", description: "Your Sense username")
	input(name: "password", type: "password", title: "Password", required: "true", description: "Your Sense password")
}

metadata {
	definition (name: "HelloSense", namespace: "bobcat0070", author: "Felix Gorodishter") {
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Illuminance Measurement"
	}

	simulator {
		// TODO: define status and reply messages here
	}
}


tiles(scale: 2) {
	valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2) {
		state "temperature", label:'${currentValue}°', backgroundColors:[
		[value: 32, color: "#153591"],
		[value: 44, color: "#1e9cbb"],
		[value: 59, color: "#90d2a7"],
		[value: 74, color: "#44b621"],
		[value: 84, color: "#f1d801"],
		[value: 92, color: "#d04e00"],
		[value: 98, color: "#bc2323"]
		]
	}
	valueTile("humidity", "device.humidity", inactiveLabel: false, width: 2, height: 2) {
		state "humidity", label:'${currentValue}% humidity', unit:"", backgroundColors:[
		[value:   0, color: "#D6D6FF"],
		[value: 100, color: "#2E2EFF"]
		]
	}

	valueTile("illuminance", "device.illuminance", inactiveLabel: false, width: 2, height: 2) {
		state "luminosity",label:'${currentValue} lux', unit:"", backgroundColors:[
		[value:    0, color: "#000000"],
		[value:    1, color: "#060053"],
		[value:    3, color: "#3E3900"],
		[value:   12, color: "#8E8400"],
		[value:   24, color: "#C5C08B"],
		[value:   36, color: "#DAD7B6"],
		[value:  128, color: "#F3F2E9"],
		[value: 1000, color: "#FFFFFF"]
		]
	}

	standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat",  width: 2, height: 2) {
		state "default", label:'   ', action:"refresh.refresh", icon:"st.secondary.refresh"
	}
	
	main(["temperature", "humidity", "illuminance"])
	details(["temperature", "humidity", "illuminance","refresh"])
}

def installed() {
	init()
}

def updated() {
	unschedule()
	init()
}

def init() {
	runEvery5Minutes(poll)
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

// handle commands

def poll() {
	log.info "Executing 'poll'"

	log.info "Executing 'status'"
	api('status', []) { response ->
		log.trace "Status response $response.status $response.data"

		sendEvent(name: 'temperature', value: String.format("%5.1f",(response.data.temperature.value *  (9/5) + 32)))
		sendEvent(name: 'humidity', value: String.format("%5.1f",response.data.humidity.value))
		sendEvent(name: 'illuminance', value: String.format("%5.1f",response.data.light.value))
	}
}

def refresh() {
	poll()
}

def api(method, args = [], success = {}) {
	log.info "Executing 'api'"

	if (!isLoggedIn()) {
		log.debug "Need to login"
		login(method, args, success)
		return
	}

	def methods = [
		'status': [uri: "https://api.hello.is/v1/room/current", type: 'get']
	]

	def request = methods.getAt(method)

	log.debug "Starting $method : $args"
	doRequest(request.uri, args, request.type, success)
}

// Need to be logged in before this is called. So don't call this. Call api.
def doRequest(uri, args, type, success) {
	log.debug "Calling $type : $uri : $args"

	def params = [
		uri: uri,
		headers: [
			'Authorization': "Bearer $state.auth.access_token"
		],
		body: args
	]

	log.trace params

	try {
		if (type == 'post') {
			httpPost(params, success)
		} else if (type == 'get') {
			httpGet(params, success)
		}

	} catch (e) {
		log.debug "something went wrong: $e"
	}
}

def login(method = null, args = [], success = {}) { 
	log.info "Executing 'login'"
	def params = [
		uri: 'https://api.hello.is/v1/oauth2/token',
		body: [
			username: settings.username, 
			password: settings.password, 
			client_id: "8d3c1664-05ae-47e4-bcdb-477489590aa4", 
			client_secret: "4f771f6f-5c10-4104-bbc6-3333f5b11bf9",
			grant_type: "password"
		]
	]

	httpPost(params) { response ->
		log.trace "Login response, $response.status $response.data"

		state.auth = response.data

		// set the expiration to 10 minutes
		state.auth.expires_at = new Date().getTime() + 600000;
		
		api(method, args, success)
	}
}

def logout() { 
	log.info "Executing 'logout'"
	api('logout', []) { response ->
		//	log.trace "Logout response $response.status $response.data"
	}	
	state.auth = false
}

def isLoggedIn() {
	if (!state.auth) {
		log.debug "No state.auth"
		return false
	}

	//	log.trace state.auth.uid

	def now = new Date().getTime();
	//	log.trace now
	//	log.trace state.auth.expires_at
	return state.auth.expires_at > now
}
