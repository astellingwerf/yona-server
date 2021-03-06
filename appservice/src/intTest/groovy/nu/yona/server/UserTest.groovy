package nu.yona.server

import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class UserTest extends Specification {

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	@Shared
	def timestamp = YonaServer.getTimeStamp()
	def userCreationJSON = """{
				"firstName":"John",
				"lastName":"Doe ${timestamp}",
				"nickName":"JD ${timestamp}",
				"mobileNumber":"+${timestamp}",
				"devices":[
					"Galaxy mini"
				],
				"goals":[
					"gambling"
				]}"""
	def password = "John Doe"

	def 'Create John Doe'(){
		given:

		when:
			def response = appService.addUser(userCreationJSON, password)

		then:
			response.status == 201
			testUser(response.responseData, true)

		cleanup:
			appService.deleteUser(appService.stripQueryString(response.responseData._links.self.href), password)
	}

	def 'Get John Doe with private data'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.getUser(userURL, true, password)

		then:
			response.status == 200
			testUser(response.responseData, true)

		cleanup:
			appService.deleteUser(userURL, password)
	}

	def 'Try to get John Doe\'s private data with a bad password'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.getUser(userURL, true, "nonsense")

		then:
			response.status == 400

		cleanup:
			appService.deleteUser(userURL, password)
	}

	def 'Get John Doe without private data'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.getUser(userURL, false)

		then:
			response.status == 200
			testUser(response.responseData, false)

		cleanup:
			appService.deleteUser(userURL, password)
	}

	def 'Delete John Doe'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.deleteUser(userURL, password)

		then:
			response.status == 200
			verifyUserDoesNotExist(userURL)
	}

	void testUser(responseData, includePrivateData)
	{
		assert responseData.firstName == "John"
		assert responseData.lastName == "Doe ${timestamp}"
		assert responseData.mobileNumber == "+${timestamp}"
		if (includePrivateData) {
			assert responseData.nickName == "JD ${timestamp}"
			assert responseData.devices.size() == 1
			assert responseData.devices[0] == "Galaxy mini"
			assert responseData.goals.size() == 1
			assert responseData.goals[0] == "gambling"
			
			assert responseData._embedded.buddies != null
			assert responseData._embedded.buddies.size() == 0
		} else {
			assert responseData.nickName == null
			assert responseData.devices == null
			assert responseData.goals == null
		}
	}

	void verifyUserDoesNotExist(userURL)
	{
		def response = appService.getUser(userURL, false)
		assert response.status == 400 || response.status == 404;
	}
}
