/*******************************************************************************
 * Copyright 2012 Alexandros Schillings
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package co.uk.alt236.linkedinjtestapp.util;

public class Const {	
	public static final String OAUTH_PREF = "LIKEDIN_OAUTH";
	public static final String PREF_TOKEN = "token";
	public static final String PREF_TOKENSECRET = "tokenSecret";
	public static final String PREF_REQTOKENSECRET = "requestTokenSecret";
	
	public static final String OAUTH_CALLBACK_SCHEME = "x-oauthflow-linkedin";
	public static final String OAUTH_CALLBACK_HOST = "linkedinApiTestCallback";
	public static final String OAUTH_CALLBACK_URL = String.format("%s://%s", OAUTH_CALLBACK_SCHEME, OAUTH_CALLBACK_HOST);
	public static final String OAUTH_QUERY_TOKEN = "oauth_token";
	public static final String OAUTH_QUERY_VERIFIER = "oauth_verifier";
	public static final String OAUTH_QUERY_PROBLEM = "oauth_problem";
}
