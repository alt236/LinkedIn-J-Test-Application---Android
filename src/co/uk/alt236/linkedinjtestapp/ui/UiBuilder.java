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
package co.uk.alt236.linkedinjtestapp.ui;

import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import co.uk.alt236.linkedinjtestapp.R;

public class UiBuilder {
	private final LayoutInflater inflater;

	public UiBuilder(LayoutInflater inflater) {
		super();
		this.inflater = inflater;
	}
	
	public View createSection(String title, String text){
		if(text == null || text.trim().length() == 0){
			return new View(inflater.getContext());
		}
		
		View v = inflater.inflate(R.layout.item_list_section, null);
		setText(((TextView) v.findViewById(R.id.text1)), title);
		setText(((TextView) v.findViewById(R.id.text2)), text);
		
		Linkify.addLinks(((TextView) v.findViewById(R.id.text2)), Linkify.ALL);
		
		return v;
	}


	public View createTwoLineListItem(String s1, String s2){
		if(s1 == null || s1.trim().length() == 0){
			return new View(inflater.getContext());
		}
		
		View v = inflater.inflate(R.layout.item_list_2liner, null);
		setText(((TextView) v.findViewById(R.id.text1)) ,s1);
		setText(((TextView) v.findViewById(R.id.text2)) ,s2);
		return v;
	}
	
	public View createFourLineListItem(String s1, String s2, String s3, String s4){
		if(s1 == null || s1.trim().length() == 0){
			return new View(inflater.getContext());
		}
		
		View v = inflater.inflate(R.layout.item_list_4liner, null);
		setText(((TextView) v.findViewById(R.id.text1)) ,s1);
		setText(((TextView) v.findViewById(R.id.text2)) ,s2);
		setText(((TextView) v.findViewById(R.id.text3)) ,s3);
		setText(((TextView) v.findViewById(R.id.text4)) ,s4);
		return v;
	}
	
	
	
	private void setText(TextView tv, String text){
		if(text == null || text.trim().length() == 0){
			tv.setText("");
			tv.setVisibility(View.GONE);
			return;
		} else {
			tv.setText(text);
			tv.setVisibility(View.VISIBLE);
		}
	}
}
