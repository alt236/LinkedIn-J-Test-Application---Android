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
package co.uk.alt236.linkedinjtestapp;

import java.util.ArrayList;
import java.util.EnumSet;

import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import co.uk.alt236.linkedinjtestapp.ui.ImageLoader;
import co.uk.alt236.linkedinjtestapp.ui.UiBuilder;
import co.uk.alt236.linkedinjtestapp.util.Const;
import co.uk.alt236.linkedinjtestapp.util.ConstDoNotCheckin;

import com.commonsware.cwac.merge.MergeAdapter;
import com.google.code.linkedinapi.client.LinkedInApiClient;
import com.google.code.linkedinapi.client.LinkedInApiClientException;
import com.google.code.linkedinapi.client.LinkedInApiClientFactory;
import com.google.code.linkedinapi.client.enumeration.ProfileField;
import com.google.code.linkedinapi.client.oauth.LinkedInAccessToken;
import com.google.code.linkedinapi.client.oauth.LinkedInOAuthService;
import com.google.code.linkedinapi.client.oauth.LinkedInOAuthServiceFactory;
import com.google.code.linkedinapi.client.oauth.LinkedInRequestToken;
import com.google.code.linkedinapi.schema.Activity;
import com.google.code.linkedinapi.schema.Connections;
import com.google.code.linkedinapi.schema.Education;
import com.google.code.linkedinapi.schema.Educations;
import com.google.code.linkedinapi.schema.ImAccount;
import com.google.code.linkedinapi.schema.ImAccounts;
import com.google.code.linkedinapi.schema.MemberUrl;
import com.google.code.linkedinapi.schema.MemberUrlResources;
import com.google.code.linkedinapi.schema.Person;
import com.google.code.linkedinapi.schema.PersonActivities;
import com.google.code.linkedinapi.schema.PhoneNumber;
import com.google.code.linkedinapi.schema.PhoneNumbers;
import com.google.code.linkedinapi.schema.Position;
import com.google.code.linkedinapi.schema.Positions;

// Code based on example by Selvin: http://stackoverflow.com/questions/5804257/posting-linkedin-message-from-android-application

public class Main extends TabActivity {
	private final String TAG = this.getClass().getName();

	private static final EnumSet<ProfileField> ProfileParameters = EnumSet.allOf(ProfileField.class);

	private final LinkedInOAuthService oAuthService = 
			LinkedInOAuthServiceFactory.getInstance().createLinkedInOAuthService(ConstDoNotCheckin.APP_KEY, ConstDoNotCheckin.APP_SECRET);
	private final LinkedInApiClientFactory factory = 
			LinkedInApiClientFactory.newInstance(ConstDoNotCheckin.APP_KEY, ConstDoNotCheckin.APP_SECRET);

	// List of tabs
	private static final String TAB_MAIN = "tab_main";
	private static final String TAB_EDUCATION = "tab_education";
	private static final String TAB_POSITIONS = "tab_positions";
	private static final String TAB_ACTIVITIES = "tab_activities";
	private static final String TAB_CONNECTIONS = "tab_connections";
	private static final String TAB_CONTACT = "tab_contact";

	private ArrayList<String> tabList = new ArrayList<String>();

	private ImageView imageViewPicture;
	private TextView textViewName;
	private TextView textViewHeading;
	private TextView textViewLocation;

	private Button buttonClearTokens;
	private Button buttonReload;

	private ImageLoader mImageLoader;

	private ListView listViewMain;
	private ListView listViewEducation;
	private ListView listViewConnections;
	private ListView listViewPositions;
	private ListView listViewActivities;
	private ListView listViewContactInfo;

	private MergeAdapter mergeMain;
	private MergeAdapter mergeEducation;
	private MergeAdapter mergeConnections;
	private MergeAdapter mergePositions;
	private MergeAdapter mergeActivities;
	private MergeAdapter mergeContactInfo;

	private UiBuilder mUiBuilder;

	private Person mCurrentPerson;

	public void addTab(TabHost tabHost, String tabSpec, String title, int contentId) {
		tabHost.addTab(tabHost.newTabSpec(tabSpec)
				.setIndicator(title)
				.setContent(contentId));
	}

	void authenticationFinish(final Uri uri) {
		if (uri != null && uri.getScheme().equals(Const.OAUTH_CALLBACK_SCHEME)) {
			final String problem = uri.getQueryParameter(Const.OAUTH_QUERY_PROBLEM);
			if (problem == null) {
				final SharedPreferences pref = getSharedPreferences(Const.OAUTH_PREF, MODE_PRIVATE);
				final LinkedInAccessToken accessToken = oAuthService
						.getOAuthAccessToken(
								new LinkedInRequestToken(
										uri.getQueryParameter(Const.OAUTH_QUERY_TOKEN),
										pref.getString(Const.PREF_REQTOKENSECRET, null)),
										uri.getQueryParameter(Const.OAUTH_QUERY_VERIFIER));
				pref.edit()
				.putString(Const.PREF_TOKEN, accessToken.getToken())
				.putString(Const.PREF_TOKENSECRET, accessToken.getTokenSecret())
				.remove(Const.PREF_REQTOKENSECRET).commit();

				new AsyncGetCurrentUserInfo().execute(accessToken);
			} else {
				Toast.makeText(
						this,
						"Application down due OAuth problem: " + problem,
						Toast.LENGTH_LONG).show();
				finish();
			}
		}
	}

	void authenticationStart() {
		final LinkedInRequestToken liToken = oAuthService.getOAuthRequestToken(Const.OAUTH_CALLBACK_URL);
		final String uri = liToken.getAuthorizationUrl();
		getSharedPreferences(Const.OAUTH_PREF, MODE_PRIVATE)
		.edit()
		.putString(Const.PREF_REQTOKENSECRET, liToken.getTokenSecret())
		.commit();
		Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
		startActivity(i);
	}

	void clearTokens() {
		getSharedPreferences(Const.OAUTH_PREF, MODE_PRIVATE).edit()
		.remove(Const.PREF_TOKEN).remove(Const.PREF_TOKENSECRET)
		.remove(Const.PREF_REQTOKENSECRET).commit();

		Intent intent = getIntent();
		finish();
		startActivity(intent);
	}

	private void fetchInfo(String token, String tokenSecret) {
		if (token == null || tokenSecret == null) {
			authenticationStart();
		} else {
			new AsyncGetCurrentUserInfo().execute(new LinkedInAccessToken(token, tokenSecret));
			//showCurrentUser(new LinkedInAccessToken(token, tokenSecret));
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		final SharedPreferences pref = getSharedPreferences(Const.OAUTH_PREF,	MODE_PRIVATE);
		final String token = pref.getString(Const.PREF_TOKEN, null);
		final String tokenSecret = pref.getString(Const.PREF_TOKENSECRET, null);

		imageViewPicture = (ImageView) findViewById(R.id.imageMe);

		textViewName = (TextView) findViewById(R.id.textName);
		textViewHeading = (TextView) findViewById(R.id.textHeading);
		textViewLocation = (TextView) findViewById(R.id.textLocation);

		buttonReload= (Button) findViewById(R.id.buttonReload);
		buttonClearTokens = (Button) findViewById(R.id.buttonClearTokens);

		listViewMain = (ListView) findViewById(R.id.list1);
		listViewContactInfo = (ListView) findViewById(R.id.list2);
		listViewEducation = (ListView) findViewById(R.id.list3);
		listViewPositions = (ListView) findViewById(R.id.list4);
		listViewActivities = (ListView) findViewById(R.id.list5);
		listViewConnections = (ListView) findViewById(R.id.list6);

		mUiBuilder = new UiBuilder(getLayoutInflater());

		mImageLoader = new ImageLoader(
				this.getApplicationContext(), 
				android.R.drawable.gallery_thumb,
				false,
				1,
				"/data/LinkedIn API Test");

		buttonClearTokens.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				clearTokens();

			}
		});


		buttonReload.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				fetchInfo(token, tokenSecret);
			}
		}); 

		getTabHost().getTabWidget().setVisibility(View.INVISIBLE);

		setupTabs();

		// Load data
		Person person = (Person) getLastNonConfigurationInstance();
		if (person == null) {
			fetchInfo(token, tokenSecret);
		} else {
			mCurrentPerson = person;
			populateAll(person);
		}
	}


	@Override
	protected void onDestroy() {
		mImageLoader.stopThread();
		super.onDestroy();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		authenticationFinish(intent.getData());
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return mCurrentPerson;
	}

	private void populateAll(Person p) throws LinkedInApiClientException{

		getTabHost().getTabWidget().setVisibility(View.INVISIBLE);

		populateSectionMain(p, listViewMain, mergeMain, tabList.indexOf(TAB_MAIN));
		populateSectionUrlResources(p, listViewContactInfo, mergeContactInfo, tabList.indexOf(TAB_CONTACT));
		populateSectionEducation(p, listViewEducation, mergeEducation, tabList.indexOf(TAB_EDUCATION));
		populateSectionConnections(p, listViewConnections, mergeConnections, tabList.indexOf(TAB_CONNECTIONS));
		populateSectionPositions(p, listViewPositions, mergePositions, tabList.indexOf(TAB_POSITIONS));
		populateSectionActivities(p, listViewActivities, mergeActivities, tabList.indexOf(TAB_ACTIVITIES));

		getTabHost().getTabWidget().setVisibility(View.VISIBLE);
	}
	
	private void populateSectionActivities(Person p, ListView l, MergeAdapter adapter, int tabIndex){
		PersonActivities items = p.getPersonActivities();
		adapter = new MergeAdapter();

		if (items != null && items.getCount() > 0) { 

			for(Activity item: items.getActivityList()){
				adapter.addView(
						mUiBuilder.createTwoLineListItem(
								item.getLocale(),
								item.getBody())
						);
			}

		}else {
			getTabHost().getTabWidget().getChildAt(tabIndex).setVisibility(View.GONE);
		}
		l.setAdapter(adapter);
	}

	private void populateSectionConnections(Person p, ListView l, MergeAdapter adapter, int tabIndex){
		Connections items = p.getConnections();
		adapter = new MergeAdapter();

		if (items != null && items.getTotal() > 0) { 

			for(Person item: items.getPersonList()){
				adapter.addView(
						mUiBuilder.createFourLineListItem(
								item.getLastName() + 	", " + item.getFirstName(), 
								item.getHeadline(),
								getString(R.string.section_title_profile_id) + ": " + item.getId(),
								item.getPublicProfileUrl())
						);
			}
		} else {
			getTabHost().getTabWidget().getChildAt(tabIndex).setVisibility(View.GONE);
		}
		l.setAdapter(adapter);
	}

	private void populateSectionEducation(Person p, ListView l, MergeAdapter adapter, int tabIndex){
		Educations items = p.getEducations();
		adapter = new MergeAdapter();

		if (items != null && items.getTotal() > 0) { 

			for(Education item: items.getEducationList()){
				adapter.addView(
						mUiBuilder.createTwoLineListItem(
								item.getDegree(), 
								item.getSchoolName())
						);
			}
		} else {
			getTabHost().getTabWidget().getChildAt(tabIndex).setVisibility(View.GONE);
		}
		l.setAdapter(adapter);
	}

	private void populateSectionMain(Person p, ListView l, MergeAdapter adapter, int tabIndex){
		// Do the image
		String imageUrl = p.getPictureUrl();
		imageViewPicture.setTag(imageUrl);
		mImageLoader.displayImage(imageUrl, imageViewPicture);

		textViewName.setText(p.getFirstName() + " " + p.getLastName());
		textViewHeading.setText(p.getHeadline());

		if(p.getLocation() != null){
			textViewLocation.setText(p.getLocation().getName());
		}

		adapter = new MergeAdapter();

		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_industry), p.getIndustry()));
		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_summary), p.getSummary()));
		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_honors), p.getHonors()));
		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_specialties), p.getSpecialties()));
		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_status), p.getCurrentStatus()));
		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_interests), p.getInterests()));
		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_profile_id), p.getId()));
		adapter.addView(mUiBuilder.createSection(getString(R.string.section_title_profile_url), p.getPublicProfileUrl()));

		if(adapter.getCount() <= 0){
			getTabHost().getTabWidget().getChildAt(tabIndex).setVisibility(View.GONE);
		}

		l.setAdapter(adapter);
	}

	private void populateSectionPositions(Person p, ListView l, MergeAdapter adapter, int tabIndex){
		Positions items = p.getPositions();
		adapter = new MergeAdapter();

		if (items != null && items.getTotal() > 0) { 

			for(Position item: items.getPositionList()){
				adapter.addView(
						mUiBuilder.createTwoLineListItem(
								item.getTitle(), 
								item.getCompany().getName())
						);
			}
		} else {
			getTabHost().getTabWidget().getChildAt(tabIndex).setVisibility(View.GONE);
		}
		l.setAdapter(adapter);
	}

	private void populateSectionUrlResources(Person p, ListView l, MergeAdapter adapter, int tabIndex){
		MemberUrlResources urls = p.getMemberUrlResources();
		ImAccounts ims = p.getImAccounts();
		PhoneNumbers pns = p.getPhoneNumbers();

		adapter = new MergeAdapter();

		if (urls != null && urls.getMemberUrlList() != null) { 

			for(MemberUrl item: urls.getMemberUrlList()){
				adapter.addView(
						mUiBuilder.createSection(
								item.getName(),
								item.getUrl())
						);
			}
		}

		if (ims != null && ims.getImAccountList() != null) { 

			for(ImAccount item: ims.getImAccountList()){
				adapter.addView(
						mUiBuilder.createSection(
								item.getImAccountType().value(),
								item.getImAccountName())
						);
			}
		}

		if (pns != null && pns.getPhoneNumberList() != null) { 

			for(PhoneNumber item: pns.getPhoneNumberList()){
				adapter.addView(
						mUiBuilder.createSection(
								item.getPhoneType().value(),
								item.getPhoneNumber())
						);
			}
		}

		if(adapter.getCount() <= 0){
			getTabHost().getTabWidget().getChildAt(tabIndex).setVisibility(View.GONE);
		}

		l.setAdapter(adapter);
	}

	private void setupTabs(){
		final TabHost tabHost = getTabHost();	

		// Add identifiers to the list
		tabList.add(TAB_MAIN);
		tabList.add(TAB_CONTACT);
		tabList.add(TAB_EDUCATION);
		tabList.add(TAB_POSITIONS);
		tabList.add(TAB_ACTIVITIES);
		tabList.add(TAB_CONNECTIONS);

		// Add tabs to the tabhost - All of them otherwise scrolling will not work
		addTab(tabHost, tabList.get(0), getString(R.string.tab_info), R.id.tab1);
		addTab(tabHost, tabList.get(1), getString(R.string.tab_contact), R.id.tab2);
		addTab(tabHost, tabList.get(2), getString(R.string.tab_education), R.id.tab3);
		addTab(tabHost, tabList.get(3), getString(R.string.tab_positions), R.id.tab4);
		addTab(tabHost, tabList.get(4), getString(R.string.tab_activities), R.id.tab5);
		addTab(tabHost, tabList.get(5), getString(R.string.tab_connections), R.id.tab6);

	}

//	private Person getOtherUser(final LinkedInAccessToken accessToken, String firstName, String lastName, String companyName) throws LinkedInApiClientException {
//		final LinkedInApiClient client = factory.createLinkedInApiClient(accessToken);
//		Map<SearchParameter, String> searchParams = new HashMap<SearchParameter, String>(); 
//
//		client.setAccessToken(accessToken);
//
//		searchParams.put(SearchParameter.FIRST_NAME, firstName);
//		searchParams.put(SearchParameter.LAST_NAME, lastName);
//		searchParams.put(SearchParameter.COMPANY_NAME, companyName);
//
//		People people = client.searchPeople(searchParams, ProfileParameters);
//
//		for (Person person : people.getPersonList()) {
//			ApiStandardProfileRequest req = person.getApiStandardProfileRequest();
//
//			if(req != null){
//				// we only want one atm.
//				return person = client.getProfileByApiRequest(req);
//			}
//		}
//
//		return null;
//	}

	class AsyncGetCurrentUserInfo extends AsyncTask<LinkedInAccessToken, Integer, Person> {
		ProgressDialog dialog;

		@Override
		protected Person doInBackground(LinkedInAccessToken... arg0) {
			try{
				final LinkedInApiClient client = factory.createLinkedInApiClient(arg0[0]);
				client.setAccessToken(arg0[0]);
				return client.getProfileForCurrentUser(ProfileParameters);

				//return getOtherUser(arg0[0], "George", "Alecou", "");
			} catch (LinkedInApiClientException ex){
				Log.e(TAG, "LinkedInApiClientException: ", ex);
				return null;
			}
		}

		@Override
		protected void onPostExecute(Person person) {

			if(person == null){
				Toast.makeText(
						Main.this,
						R.string.application_down_due_to_linkedinapiclientexception,
						Toast.LENGTH_LONG).show();
				dialog.dismiss();
				clearTokens();
			}else{
				mCurrentPerson = person;
				populateAll(person);
				dialog.dismiss();
			}
		}

		@Override
		protected void onPreExecute() {
			dialog = new ProgressDialog(Main.this);
			dialog.setMessage(getString(R.string.please_wait_fetching_linkedin_details));
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.show();
		}
	}
}
