/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
	/**
	 * Update rate in milliseconds for interactive mode. We update once a second since seconds are
	 * displayed in interactive mode.
	 */
	private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

	/**
	 * Handler message id for updating the time periodically in interactive mode.
	 */
	private static final int MSG_UPDATE_TIME = 0;

	@Override
	public Engine onCreateEngine() {
		return new Engine();
	}

	private static class EngineHandler extends Handler {
		private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

		public EngineHandler(SunshineWatchFace.Engine reference) {
			mWeakReference = new WeakReference<>(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			SunshineWatchFace.Engine engine = mWeakReference.get();
			if (engine != null) {
				switch (msg.what) {
					case MSG_UPDATE_TIME:
						engine.handleUpdateTimeMessage();
						break;
				}
			}
		}
	}

	private class Engine extends CanvasWatchFaceService.Engine {
		final Handler mUpdateTimeHandler = new EngineHandler(this);
		private final Point displaySize = new Point();
		boolean mRegisteredTimeZoneReceiver = false;
		boolean mAmbient;
		Time mTime;
		final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mTime.clear(intent.getStringExtra("time-zone"));
				mTime.setToNow();
			}
		};
		/**
		 * Whether the display supports fewer bits for each color in ambient mode. When true, we
		 * disable anti-aliasing in ambient mode.
		 */
		boolean mLowBitAmbient;
		SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
		private int specW, specH;
		private TextView dateTextView, timeTextView, minTempTextView, maxTempTextView;
		private ImageView weatherImageView;
		private View myLayout;

		@Override
		public void onCreate(SurfaceHolder holder) {
			super.onCreate(holder);

			LayoutInflater inflater =
					(LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			myLayout = inflater.inflate(R.layout.watchface, null);
			timeTextView = (TextView) myLayout.findViewById(R.id.time_textView);
			dateTextView = (TextView) myLayout.findViewById(R.id.date_textView);
			minTempTextView = (TextView) myLayout.findViewById(R.id.min_temp_textView);
			maxTempTextView = (TextView) myLayout.findViewById(R.id.max_temp_textView);
			weatherImageView = (ImageView) myLayout.findViewById(R.id.weather_imageView);

			Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
					.getDefaultDisplay();
			display.getSize(displaySize);

			specW = View.MeasureSpec.makeMeasureSpec(displaySize.x,
					View.MeasureSpec.EXACTLY);
			specH = View.MeasureSpec.makeMeasureSpec(displaySize.y,
					View.MeasureSpec.EXACTLY);

			setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
					.setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
					.setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
					.setShowSystemUiTime(false)
					.build());

			mTime = new Time();

			preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
				@Override
				public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
					updateWeatherInfo(sharedPreferences);
				}
			};

			SharedPreferences sp = getSharedPreferences(Constants.WEATHER_PREF, Context.MODE_PRIVATE);
			sp.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
			updateWeatherInfo(sp);
		}

		private void updateWeatherInfo(SharedPreferences sharedPreferences) {
			int maxTemp = (int) sharedPreferences.getFloat(Constants.PREF_KEY_MAX_TEMP, Integer.MAX_VALUE);
			int minTemp = (int) sharedPreferences.getFloat(Constants.PREF_KEY_MIN_TEMP, Integer.MIN_VALUE);
			int weatherId = sharedPreferences.getInt(Constants.PREF_KEY_WEATHER_ID, -1);
			int resId = Utils.getIconResourceForWeatherCondition(weatherId);

			if (minTemp != Integer.MIN_VALUE && maxTemp != Integer.MAX_VALUE) {
				minTempTextView.setText(getString(R.string.temperature, minTemp));
				maxTempTextView.setText(getString(R.string.temperature, maxTemp));
			} else {
				minTempTextView.setText(null);
				maxTempTextView.setText(null);
			}

			if (resId != -1) {
				weatherImageView.setImageResource(resId);
			} else {
				weatherImageView.setImageDrawable(null);
			}
		}

		@Override
		public void onDestroy() {
			SharedPreferences sp = getSharedPreferences(Constants.WEATHER_PREF, Context.MODE_PRIVATE);
			sp.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);

			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
			super.onDestroy();
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);

			if (visible) {
				registerReceiver();

				// Update time zone in case it changed while we weren't visible.
				mTime.clear(TimeZone.getDefault().getID());
				mTime.setToNow();
			} else {
				unregisterReceiver();
			}

			// Whether the timer should be running depends on whether we're visible (as well as
			// whether we're in ambient mode), so we may need to start or stop the timer.
			updateTimer();
		}

		private void registerReceiver() {
			if (mRegisteredTimeZoneReceiver) {
				return;
			}
			mRegisteredTimeZoneReceiver = true;
			IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
			SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
		}

		private void unregisterReceiver() {
			if (!mRegisteredTimeZoneReceiver) {
				return;
			}
			mRegisteredTimeZoneReceiver = false;
			SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
		}

		@Override
		public void onPropertiesChanged(Bundle properties) {
			super.onPropertiesChanged(properties);
			mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
		}

		@Override
		public void onTimeTick() {
			super.onTimeTick();
			invalidate();
		}

		@Override
		public void onAmbientModeChanged(boolean inAmbientMode) {
			super.onAmbientModeChanged(inAmbientMode);
			if (mAmbient != inAmbientMode) {
				mAmbient = inAmbientMode;

				weatherImageView.setVisibility(mAmbient ? View.GONE : View.VISIBLE);
				invalidate();
			}

			// Whether the timer should be running depends on whether we're visible (as well as
			// whether we're in ambient mode), so we may need to start or stop the timer.
			updateTimer();
		}


		@Override
		public void onDraw(Canvas canvas, Rect bounds) {
			// Draw the background.
			if (isInAmbientMode()) {
				canvas.drawColor(Color.BLACK);
			} else {
				canvas.drawColor(getColor(R.color.background));
			}


			mTime.setToNow();
			Date date = new Date(mTime.toMillis(false));
			SimpleDateFormat dateFormat = new SimpleDateFormat("E, MMM dd yyyy", Locale.US);
			dateTextView.setText(dateFormat.format(date).toUpperCase());

			SimpleDateFormat timeFormat = mAmbient ? new SimpleDateFormat("kk:mm", Locale.US) : new SimpleDateFormat("kk:mm:ss", Locale.US);
			timeTextView.setText(timeFormat.format(date));

			myLayout.measure(specW, specH);
			myLayout.layout(0, 0, myLayout.getMeasuredWidth(),
					myLayout.getMeasuredHeight());
			myLayout.draw(canvas);
		}

		/**
		 * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
		 * or stops it if it shouldn't be running but currently is.
		 */
		private void updateTimer() {
			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
			if (shouldTimerBeRunning()) {
				mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
			}
		}

		/**
		 * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
		 * only run when we're visible and in interactive mode.
		 */
		private boolean shouldTimerBeRunning() {
			return isVisible() && !isInAmbientMode();
		}

		/**
		 * Handle updating the time periodically in interactive mode.
		 */
		private void handleUpdateTimeMessage() {
			invalidate();
			if (shouldTimerBeRunning()) {
				long timeMs = System.currentTimeMillis();
				long delayMs = INTERACTIVE_UPDATE_RATE_MS
						- (timeMs % INTERACTIVE_UPDATE_RATE_MS);
				mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
			}
		}
	}
}
