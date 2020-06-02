package com.example.jn;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Locale;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

public class MainActivity
		extends Activity
		implements RecognitionListener, LocationListener {


	public static String tvLongi;
	public static String tvLati;
	LocationManager locationManager;


	/* Named searches allow to quickly reconfigure the decoder */
	private static final String KWS_SEARCH  = "STOP";
	private static final String MENU_SEARCH = "menu";

	private static final String KEYPHRASE = "START";

	private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

	private SpeechRecognizer         recognizer;
	private HashMap<String, Integer> captions;
	private Button                   button;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		captions = new HashMap<>();
		captions.put(KWS_SEARCH, R.string.kws_caption);
		captions.put(MENU_SEARCH, R.string.menu_caption);

		setContentView(R.layout.activity_main);


		((TextView) findViewById(R.id.caption_text)).setText("Preparing the recognizer");

		int permissionCheck = ContextCompat.checkSelfPermission(
				getApplicationContext(),
				Manifest.permission.RECORD_AUDIO
		);
		if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
											  PERMISSIONS_REQUEST_RECORD_AUDIO
			);
			return;
		}

		new SetupTask(this).execute();

	}

	public void getLocation() {
		try {
			locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5, this);
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// Recognizer initialization is a time-consuming and it involves IO,
				// so we execute it in async task
				new SetupTask(this).execute();
			}
			else {
				finish();
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (recognizer != null) {
			recognizer.cancel();
			recognizer.shutdown();
		}
	}

	/**
	 * In partial result we get quick updates about current hypothesis. In
	 * keyword spotting mode we can react here, in other modes we need to wait
	 * for final result in onResult.
	 */
	@Override
	public void onPartialResult(Hypothesis hypothesis) {
		if (hypothesis == null)
			return;

		String text = hypothesis.getHypstr();
		if (text.equals(KEYPHRASE))
			switchSearch(MENU_SEARCH);
			//else if (text.equals(DIGITS_SEARCH))
			//    switchSearch(DIGITS_SEARCH);
		else
			((TextView) findViewById(R.id.result_text)).setText(text);
	}

	@Override
	public void onResult(Hypothesis hypothesis) {
		((TextView) findViewById(R.id.result_text)).setText("");
		if (hypothesis != null) {
			String text = hypothesis.getHypstr();
			makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
			if (text.equals("OTWÓRZ PRZEGLĄDARKĘ")) {
				open_browser();
			}
			else if (text.equals("USTAW BUDZIK NA DWUNASTĄ")) {
				set_Alarm();
			}
			else if (text.equals("OTWÓRZ MAPĘ")) {
				open_Maps();
			}
			else if (text.equals("OTWÓRZ KALKULATOR")) {
				open_calculator();
			}
			else if (text.equals("OTWÓRZ KAMERĘ")) {
				open_camera();
			}
			else if (text.equals("OTWÓRZ DYKTOFON")) {
				open_dictaphone();
			}
			else if (text.equals("OTWÓRZ KALENDARZ")) {
				open_calendar();
			}
			else if (text.equals("ZADZWOŃ DO")) {
				open_contact_book();
			}
			else if (text.equals("ZAMKNIJ APLIKACJĘ")) {
				close_app();
			}
			else if (text.equals("WŁĄCZ MUZYKĘ")) {
				open_music();
			}
			else if (text.equals("WŁĄCZ RADIO")) {
				open_radio();
			}
			else
			if (text.equals("NAPISZ WIADOMOŚĆ") || text.equals("WYŚLIJ WIADOMOŚĆ") || text.equals("POKAŻ WIADOMOŚĆ")) {
				send_SMS();
			}
		}
	}

	@Override
	public void onBeginningOfSpeech() {
	}

	@Override
	public void onEndOfSpeech() {
		if (!recognizer.getSearchName().equals(KWS_SEARCH))
			switchSearch(KWS_SEARCH);
	}

	private void switchSearch(String searchName) {
		recognizer.stop();

		// If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
		if (searchName.equals(KWS_SEARCH))
			recognizer.startListening(searchName);
		else
			recognizer.startListening(searchName, 10000);

		String caption = getResources().getString(captions.get(searchName));
		((TextView) findViewById(R.id.caption_text)).setText(caption);
	}

	@Override
	public void onError(Exception error) {
		((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
	}

	@Override
	public void onTimeout() {
		switchSearch(KWS_SEARCH);
	}


	private void setupRecognizer(File assetsDir)
			throws IOException {
		// The recognizer can be configured to perform multiple searches
		// of different kind and switch between them

		recognizer = SpeechRecognizerSetup.defaultSetup()
							 .setAcousticModel(new File(assetsDir, "other.ci_cont"))
							 .setDictionary(new File(assetsDir, "other.dic"))

							 .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes
							 // a lot of space on the device)

							 .getRecognizer();
		recognizer.addListener(this);

        /* In your application you might not need to add all those searches.
          They are added here for demonstration. You can leave just one.
         */

		// Create keyword-activation search.
		recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

		// Create grammar-based search for selection between demos
		File menuGrammar = new File(assetsDir, "mymenu.gram");
		recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

	}

	@Override
	public void onLocationChanged(Location location) {
		tvLongi = String.valueOf(location.getLongitude());
		tvLati = String.valueOf(location.getLatitude());
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {

	}

	@Override
	public void onProviderEnabled(String provider) {

	}

	@Override
	public void onProviderDisabled(String provider) {

	}

	private static class SetupTask
			extends AsyncTask<Void, Void, Exception> {
		WeakReference<MainActivity> activityReference;

		SetupTask(MainActivity activity) {
			this.activityReference = new WeakReference<>(activity);
		}

		@Override
		protected Exception doInBackground(Void... params) {
			try {
				Assets assets   = new Assets(activityReference.get());
				File   assetDir = assets.syncAssets();
				activityReference.get().setupRecognizer(assetDir);
			} catch (IOException e) {
				return e;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Exception result) {
			if (result != null) {
				((TextView) activityReference.get()
									.findViewById(R.id.caption_text)).setText("Failed to init recognizer " + result);
			}
			else {
				activityReference.get().switchSearch(KWS_SEARCH);
			}
		}
	}


	private void open_browser() {
		Uri    uri    = Uri.parse("http://www.google.com");
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		startActivity(intent);
	}

	public void open_calculator() {

		final String CALCULATOR_PACKAGE = "com.android.calculator2";
		final String CALCULATOR_CLASS   = "com.android.calculator2.Calculator";

		Intent intent = new Intent();

		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		intent.setComponent(new ComponentName(
				CALCULATOR_PACKAGE,
				CALCULATOR_CLASS
		));

		startActivity(intent);

	}

	public void set_Alarm() {

		Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
		i.putExtra(AlarmClock.EXTRA_MESSAGE, "Alarm");
		i.putExtra(AlarmClock.EXTRA_HOUR, 12);
		i.putExtra(AlarmClock.EXTRA_MINUTES, 00);
		i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
		startActivity(i);

	}

	public void open_Maps() {

		getLocation();

		String uri    = String.format(Locale.getDefault(), "geo:%s,%s", tvLati, tvLongi);
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
		startActivity(intent);

	}

	public void open_camera() {
		Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
		startActivity(intent);
	}
	public void open_calendar() {
		Intent i = new Intent();

		ComponentName cn = new ComponentName("com.google.android.calendar", "com.android.calendar.LaunchActivity");
//		cn = new ComponentName("com.android.calendar", "com.android.calendar.LaunchActivity");

		i.setComponent(cn);
		startActivity(i);
	}
	public void open_contact_book() {
		Intent contactactivity = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
		startActivityForResult(contactactivity, 1);
	}
	public void close_app() {
		this.finish();
		System.exit(0);
	}
	public void open_music() {
		Intent intent = new Intent();
		intent.setAction(android.content.Intent.ACTION_VIEW);
//		intent.setDataAndType(Uri.parse(YOUR_SONG_PATH), "audio/*");
		startActivity(intent);
	}
	public void open_radio() {
		// No default radio app
	}
	public void send_SMS() {
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", "123456789", null)));
	}
	public void open_dictaphone() {
//        int ACTIVITY_RECORD_SOUND = 0;
//        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
//        startActivityForResult(intent, ACTIVITY_RECORD_SOUND);
	}

}
