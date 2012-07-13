/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package webapp

// Our application config - you can maintain it here or alternatively you could
// stick it in a conf.json text file and specify that on the command line when
// starting this verticle

def env = container.env

// Configuration for the web server
def webServerConf = [

  // Normal web server stuff

  port: (env['VCAP_APP_PORT'] ?: '8080') as int,
  host: env['VCAP_APP_HOST'] ?: 'localhost',
  /* ssl: true, */

  // Configuration for the event bus client side bridge
  // This bridges messages from the client side to the server side event bus
  bridge: true,

  // This defines which messages from the client we will let through
  // to the server side
  inbound_permitted: [
    // Allow calls to login
    [
      address: 'vertx.basicauthmanager.login'
    ],
    // Allow calls to get static album data from the persistor
    [
      address : 'vertx.mongopersistor',
      match : [
        action : 'find',
        collection : 'albums'
      ]
    ],
    // And to place orders
    [
      address : 'vertx.mongopersistor',
      requires_auth : true,  // User must be logged in to send let these through
      match : [
        action : 'save',
        collection : 'orders'
      ]
    ]
  ],

  // This defines which messages from the server we will let through to the client
  outbound_permitted: [
    [:]
  ]
]

// Configuration for MongoDb 
def mongoConf = [:]

if (env['VCAP_SERVICES']) {
  def vcapEnv = new groovy.json.JsonSlurper().parseText(env['VCAP_SERVICES'])

  vcapEnv['mongodb-1.8'].credentials.with {
    mongoConf.host = host[0]
    mongoConf.port = port[0] as int
    mongoConf.db_name = db[0]
    mongoConf.username = username[0]
    mongoConf.password = password[0]
  }
}


// Now we deploy the modules that we need

container.with {

  // Deploy a MongoDB persistor module

  deployVerticle('mongo-persistor', mongoConf, 1) {

    // And when it's deployed run a script to load it with some reference
    // data for the demo
    deployVerticle('StaticData.groovy')
  }

  // Deploy an auth manager to handle the authentication

  deployVerticle('auth-mgr')

  // Start the web server, with the config we defined above

  deployVerticle('web-server', webServerConf)
}