package com.resort_cloud.nansei.nansei_tablet;

import android.os.Bundle;

import androidx.annotation.Nullable;

import net.osmand.plus.activities.MapActivity;

public class FullMapActivity extends MapActivity {

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public int getRootViewId(){
		return 0;
	}
}
