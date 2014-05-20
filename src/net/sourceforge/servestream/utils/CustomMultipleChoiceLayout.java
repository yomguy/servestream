/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
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

package net.sourceforge.servestream.utils;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.RelativeLayout;

public class CustomMultipleChoiceLayout extends RelativeLayout implements
		Checkable {

	private boolean mIsChecked;
    private List<Checkable> mCheckableViews;
	
    public CustomMultipleChoiceLayout(Context context) {
		super(context);
		initialize();
	}

	public CustomMultipleChoiceLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize();
	}

	public CustomMultipleChoiceLayout(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		initialize();
	}
	
	@Override
	public void setChecked(boolean checked) {
		mIsChecked = checked;
	    for (Checkable c : mCheckableViews) {
	    	c.setChecked(checked);
	    }
	}

	@Override
	public boolean isChecked() {
		return mIsChecked;
	}

	@Override
	public void toggle() {
		mIsChecked = !mIsChecked;
        for (Checkable c : mCheckableViews) {
            c.toggle();
        }
	}
	
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		final int childCount = getChildCount();
		for (int i = 0; i < childCount; ++i) {
			findCheckableChildren(getChildAt(i));
		}
	}
	
    private void findCheckableChildren(View v) {
        if (v instanceof Checkable) {
        	mCheckableViews.add((Checkable) v);
        }

        if (v instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) v;
            final int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; ++i) {
                findCheckableChildren(viewGroup.getChildAt(i));
            }
        }
    }
	
    private void initialize() {
        mIsChecked = false;
        mCheckableViews = new ArrayList<Checkable>();
    }
}
