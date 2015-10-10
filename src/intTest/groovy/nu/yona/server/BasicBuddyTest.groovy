package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class BasicBuddyTest extends Specification {

	def baseURL = "http://localhost:8080"
	@Shared
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinUsername
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnUsername
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL 

	YonaServer yonaServer = new YonaServer(baseURL)

	def 'Add user Richard Quin'(){
		given:

		when:
			def response = yonaServer.addUser("""{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickName":"RQ",
				"emailAddress":"rich@quin.net",
				"mobileNumber":"+12345678",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"gambling"
				]
			}""", richardQuinPassword)
			richardQuinURL = yonaServer.stripQueryString(response.responseData._links.self.href)
			richardQuinUsername = response.responseData.vpnProfile.username;

		then:
			response.status == 201
			richardQuinURL.startsWith(baseURL + yonaServer.USERS_PATH)

		cleanup:
			println "URL Richard: " + richardQuinURL
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
			def response = yonaServer.addUser("""{
				"firstName":"Bob",
				"lastName":"Dunn",
				"nickName":"BD",
				"emailAddress":"bob@dunn.net",
				"mobileNumber":"+13456789",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"programming"
				]
			}""", bobDunnPassword)
			bobDunnURL = yonaServer.stripQueryString(response.responseData._links.self.href)
			bobDunnUsername = response.responseData.vpnProfile.username;

		then:
			response.status == 201
			bobDunnURL.startsWith(baseURL + yonaServer.USERS_PATH)

		cleanup:
			println "URL Bob: " + bobDunnURL
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			def response = yonaServer.requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob",
						"lastName":"Dunn",
						"emailAddress":"bob@dunn.net",
						"mobileNumber":"+13456789"
					}
				},
				"message":"Would you like to be my buddy?",
			}""", richardQuinPassword)
			richardQuinBobBuddyURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def response = yonaServer.getDirectMessages(bobDunnURL, bobDunnPassword)
			bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href

		then:
			response.status == 200
			response.responseData._links.self.href == bobDunnURL + yonaServer.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectRequestMessages[0].requestingUser.firstName == "Richard"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
			def response = yonaServer.postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
				"properties":{
					"message":"Yes, great idea!"
				}
			}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Richard checks his direct messages'(){
		given:

		when:
			def response = yonaServer.getDirectMessages(richardQuinURL, richardQuinPassword)
			richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href

		then:
			response.status == 200
			response.responseData._links.self.href == richardQuinURL + yonaServer.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectResponseMessages[0].respondingUser.firstName == "Bob"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href) 
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
			def response = yonaServer.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
				"properties":{
				}
			}""", richardQuinPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Richard checks he has no anonymous messages'(){
		given:

		when:
			def response = yonaServer.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded == null
	}

	def 'Bob checks he has no anonymous messages'(){
		given:

		when:
			def response = yonaServer.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded == null
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
			def response = yonaServer.postToAnalysisEngine("""{
			"accessorID":"${richardQuinUsername}",
			"category":"poker",
			"url":"http://www.poker.com"
			}""")

		then:
			response.status == 200
	}

	def 'Bob checks he has anonymous messages and finds a conflict for Richard'(){
		given:

		when:
			def response = yonaServer.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ"
			response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[0].url =~ /poker/
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
			def response = yonaServer.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[0].url =~ /poker/
	}
}