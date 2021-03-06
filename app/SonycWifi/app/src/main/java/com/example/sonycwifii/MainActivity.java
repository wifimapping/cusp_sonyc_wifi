package com.example.sonycwifii;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.PowerManager;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * Main activity of the app. Sets up layout so that users can click to begin a scan of the wifi APs in the area
 * and sends the results to a database on CUSP's servers.
 * 
 * @author Nicholas Hagans
 */
public class MainActivity extends Activity {
	Button scan;
	Button stop;
	TextView text;
	TextView ssid;
	TextView errorText;
	TextView quitStatus;
	ToggleButton toggle;
	int counter;
	int numOfSSID;
	WifiManager wifi;
	LocationManager locationManager;
	boolean isScanning = false;
	boolean isUploading = true;
	boolean wifiConnected;
	boolean locationConnected;
	boolean success = false;
	boolean clicked = true;
	String a;
	String entry;
	List<ScanResult> result;
	PackageInfo pInfo;
	String version;
	String responseStr;
	String mac = null;

	


	/**
	 * Sets layout state and instantiates start scan and stop scan buttons.
	 * 
	 * @param savedInstanceState
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		scan = (Button) findViewById(R.id.scan);
		stop = (Button) findViewById(R.id.stop);
		toggle = (ToggleButton) findViewById(R.id.togglebutton);
		toggle.setChecked(true);
		clicked = toggle.isChecked();
		File folder = new File(Environment.getExternalStorageDirectory() + "/WifiResults");
		if (folder.exists()) {
			isUploading = true;
			success = true;
			new uploadAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			Log.i("doInBackground", "Resumed/Starting Upload Task");
		}
		else {
			success = folder.mkdir();
			if (success) {
				isUploading = true;
				new uploadAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		}

	}

	/**
	 * Stops background upload when app is paused.
	 * 
	 */
//	@Override
//	public void onPause() {
//		super.onPause();
//		isUploading = false;
//		Log.i("doInBackground", "Paused");
//		isScanning = false;
//
//	}

	/**
	 * On start or resume, if folder exists start uploading background task
	 */
//	@Override
//	public void onResume() {
//		super.onResume();
//		Log.i("doInBackground", "Resume");
//		File folder = new File(Environment.getExternalStorageDirectory() + "/WifiResults");
//		if (folder.exists()) {
//			isUploading = true;
//			new uploadAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//			Log.i("doInBackground", "Resumed/Starting Upload Task");
//		}
//	}

	/**
	 * When scan button clicked, a folder is created if it doesn't exist already, the button is made unclickable, the wifi manager and location manager system services are
	 * retrieved, and the background scan task is started.
	 * 
	 * @param view
	 */
	public void startScan(View view) {
		counter = 0;
		numOfSSID = 0;
		File folder = new File(Environment.getExternalStorageDirectory() + "/WifiResults");
//		boolean success = true;
//		if (!folder.exists()) {
//			success = folder.mkdir();
//			Log.i("doInBackground", "Folder Created!");
//		}
		if (success) {
			Log.i("doInBackground", "Folder exists, continuing!");
			scan.setEnabled(false);
			wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

			if (!wifi.isWifiEnabled())
				wifiConnected = wifi.setWifiEnabled(true);

			/// TODO - while loops like this can lock up the UI thread. changed the above line as if wifi is already enabled, it gets stuck in this while loop (check changes on git)
			while(!wifi.isWifiEnabled())
				try {Thread.sleep(100);} catch (InterruptedException e) {e.printStackTrace();}
			isScanning = true;
			new myAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, wifi, locationManager);
		} else {
			Log.i("doInBackground", "Something went wrong with folder creation!");
			TextView ssid = (TextView) findViewById(R.id.ssid);
			a = "Ooops! Couldn't make/find folder!";
			ssid.setText(a);
		}

	}

	/**
	 * When stop button clicked, the scan is stopped and the scan button is changed to clickable.
	 * 
	 * @param view
	 */
	public void stopScan(View view) {
		isScanning = false;
		scan.setEnabled(true);
		Log.i("doInBackground", "Scan stopped");
	}
	
	public void onToggleClicked(View view) {
		clicked = ((ToggleButton) view).isChecked();		
	}
	//Class now implements the LocationListener which will allow the Override (locationchanged etc) methods to fire once a location is updated
	/**
	 * Background task class. This class holds all of the methods needed to run the scan, and save/display results of the scan.
	 * 
	 * Implements LocationListener to retrieve location of phone for each scan result.
	 * 
	 * @author Nicholas Hagans
	 *
	 */
	private class myAsyncTask extends AsyncTask<Object, Void, Void> implements LocationListener {
		Location location = null;
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		boolean lockedOn1 = false;
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wl1 = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Scan WakeLock");
		
		
		
		/**
		 * Contains the scanning and saving methods.
		 * 
		 * @param params
		 * @TODO Make sure location is retrieved even when phone is freshly restarted.
		 */
		@Override
		protected Void doInBackground(Object... params) {
			try {
				pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				version = pInfo.versionName;
			} catch (NameNotFoundException e1) {e1.printStackTrace();}
			LocationManager locMan = (LocationManager) params[1];
			String version = pInfo.versionName;
			Looper.prepare();
			if (clicked) {
				lockedOn1 = true;
				wl1.acquire();
			} else {
				lockedOn1 = false;
			}
			while(isScanning){
				a = "";
				WifiManager wifi = (WifiManager) params[0];
				WifiInfo wInfo = wifi.getConnectionInfo();
				if (mac == null) {
					mac = wInfo.getMacAddress();
					mac = "" + mac.hashCode();
				}
				//				LocationManager locMan = (LocationManager) params[1];

				List<String> all = locMan.getAllProviders();

				//As this class now implements the Location listener, we pass "this" to the location update request function
				//Ive also moved the Overriden functions from your locationListener class into this myAsncTask class (at the bottom)
				//We now get more updates as we are using both providers
				String provider = getProvider(locMan);

				//CM DEBUG - Had to change this to get any locations coming through
				/// TODO - See whats up with using "gps" as the provider
				provider = getProvider(locMan);
				locMan.requestLocationUpdates(provider, 0, 0, this);

				boolean scanStarted = wifi.startScan();
				result = wifi.getScanResults();

				//				//Have to call this to get the location updates rolling in
				/// TODO make sure this works!
				location = locMan.getLastKnownLocation(provider);
				while (location == null) {
					if (!isScanning) {
						locMan.removeUpdates(this);
						return null;
					}
					locMan.requestLocationUpdates(provider, 0, 0, this);
					Time time = new Time();
					time.setToNow();
					Log.i("doInBackground", "Looking for location " + sdf.format(time.toMillis(true)));
					runOnUiThread(new Thread() {
						public void run() {
							TextView ssid = (TextView) findViewById(R.id.ssid);
							ssid.setText("Looking for location!");
						}
					});
					try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}
					location = locMan.getLastKnownLocation(provider);
				}
				if(scanStarted)
					Log.i("doInBackground", "Scan started!!");
				numOfSSID = result.size();
				Time now = new Time();
				now.setToNow();
				if (numOfSSID > 0 && location.getTime() >= now.toMillis(true) - 10000 && location.getAccuracy() < 50) {
					counter ++;
					JSONObject header = new JSONObject();
					try {
						header.put("lat", location.getLatitude());
						header.put("lng", location.getLongitude());
						header.put("altitude", location.getAltitude());
						header.put("acc", location.getAccuracy());
						//						header.put("time", location.getTime());
						header.put("time", now.toMillis(true));
						// header.put("device_mac", wInfo.getMacAddress());
						header.put("device_mac", mac);
						header.put("app_version", version);
						header.put("droid_version", android.os.Build.VERSION.RELEASE);
						header.put("device_model", Build.MODEL);
						JSONArray readingsArr = new JSONArray();
						for (int i = 0; i < result.size(); i++) {
							if (result.get(i).level <= 0 && result.get(i).level >= -120) {
								JSONObject readings = new JSONObject();
								readings.put("SSID", result.get(i).SSID);
								readings.put("BSSID", result.get(i).BSSID);
								readings.put("caps", result.get(i).capabilities);
								readings.put("level", result.get(i).level);
								readings.put("freq", result.get(i).frequency);
								readingsArr.put(readings);

								//								if (i == 0) {
								//									a = "\nLat: " + location.getLatitude() + " Lon: " + location.getLongitude() + "\n";
								//									a += result.get(0).SSID + " " + result.get(0).level + "dBm" + " " + result.get(0).BSSID + " " + result.get(0).frequency + "Hz " + result.get(0).capabilities + " " ;
								//								} else if (i < result.size()-1){
								//									a = a + "\n" + result.get(i).SSID + " " + result.get(i).level + "dBm" + " " + result.get(i).BSSID + " " + result.get(i).frequency + "Hz " + result.get(i).capabilities;
								//								}
								//								Log.i("doInBackground", "lat: " + location.getLatitude() + "\nlong" + location.getLongitude() + "\nAcc:" + location.getAccuracy());

							}
						}
						header.put("readings", readingsArr);
					} catch(JSONException ex) {ex.printStackTrace();}

					runOnUiThread(new Thread() {
						public void run() {
							TextView text = (TextView) findViewById(R.id.text);
							text.setText("Number of scans: " + counter + "\n" + numOfSSID);
							TextView ssid = (TextView) findViewById(R.id.ssid);
							ssid.setText("Time and Accuracy: " + location.getAccuracy() + "\n" + sdf.format(location.getTime()));
						}
					});
					if(isExternalStorageWritable()) {
						try{
							//							writeToFile(header.toString(5), location.getTime());
							writeToFile(header.toString(5), now.toMillis(true));
						} catch(JSONException ex) {ex.printStackTrace();}
					}
					else {
						Log.e("doInBackground", "Cannot write to storage");
					}

					try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

					//					int TIMEOUT_MILLISEC = 10000; // = 10 seconds
					//					HttpParams httpParams = new BasicHttpParams();
					//					HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC);
					//					HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC);
					//					HttpClient client = new DefaultHttpClient(httpParams);
					//					HttpPost request = new HttpPost();
					//					try {
					//						request.setEntity(new ByteArrayEntity(
					//								header.toString().getBytes("UTF8")));
					//						HttpResponse response = client.execute(request);
					//						String responseCode = response.toString();
					//
					//						if(responseCode.equals("1")){
					//							//Sweet
					//							//delete files??? Do we want to store a number of files for safety
					//						}else{
					//							//Error
					//							Log.e("doInBackground", responseCode);
					//						}
					//
					//					} catch (ClientProtocolException e) {e.printStackTrace();
					//					} catch (Exception e) {e.printStackTrace();} 
				} else if (numOfSSID <= 0 && location.getTime() >= now.toMillis(true) - 10000 && location.getAccuracy() < 50) {
					counter ++;
					JSONObject header = new JSONObject();
					try {
						header.put("lat", location.getLatitude());
						header.put("lng", location.getLongitude());
						header.put("altitude", location.getAltitude());
						header.put("acc", location.getAccuracy());
						//						header.put("time", location.getTime());
						header.put("time", now.toMillis(true));
						// header.put("device_mac", wInfo.getMacAddress());
						header.put("device_mac", mac);
						header.put("app_version", version);
						header.put("droid_version", android.os.Build.VERSION.RELEASE);
						header.put("device_model", Build.MODEL);
						JSONArray readingsArr = new JSONArray();
						for (int i = 0; i <= result.size(); i++) {
							JSONObject readings = new JSONObject();
							readings.put("SSID", null);
							readings.put("BSSID", null);
							readings.put("caps", null);
							readings.put("level", null);
							readings.put("freq", null);
							readingsArr.put(readings);
						}
						header.put("readings", readingsArr);
					} catch(JSONException ex) {ex.printStackTrace();}

					runOnUiThread(new Thread() {
						public void run() {
							TextView text = (TextView) findViewById(R.id.text);
							text.setText("Number of scans: " + counter + "\n" + numOfSSID);
							TextView ssid = (TextView) findViewById(R.id.ssid);
							ssid.setText("Time and Accuracy: " + location.getAccuracy() + "\n" + sdf.format(location.getTime()));
						}
					});
					if(isExternalStorageWritable()) {
						try{
							//							writeToFile(header.toString(5), location.getTime());
							writeToFile(header.toString(5), now.toMillis(true));
						} catch(JSONException ex) {ex.printStackTrace();}
					}
					else {
						Log.e("doInBackground", "Cannot write to storage");
					}

					try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
				}
				else {
					runOnUiThread(new Thread() {
						public void run() {
							Time now = new Time();
							now.setToNow();
							TextView ssid = (TextView) findViewById(R.id.ssid);
							ssid.setText("Location not current or not accurate enough!\nAccuracy: " + location.getAccuracy() + "\nTime according to location " + sdf.format(location.getTime()) + "\nTime according to System " + sdf.format(now.toMillis(true)));
						}
					});
				}
			}
			if (lockedOn1) {
				wl1.release();
			}
			locMan.removeUpdates(this);
			Looper.myLooper().quit();

			return null;
		}

		/**
		 * Saves the current scan result in JSON format to the phone with filename <utcTime>.txt.
		 * 
		 * @param entry
		 * @param time
		 * @return boolean determined by whether the file exists
		 */
		public boolean writeToFile(String entry, long time){
			String fileName = time + ".tmp";
			String finalFileName = time + ".txt";
			File finalFile = new File(Environment.getExternalStorageDirectory() + "/WifiResults/" + finalFileName);
			File file = new File(Environment.getExternalStorageDirectory() + "/WifiResults/" + fileName);
			Log.i("doInBackground", file.getAbsolutePath());
			try {
				FileWriter out = new FileWriter(file);
				out.append(entry);
				out.close();
				file.renameTo(finalFile);
				Log.i("doInBackground", "Written to file");
			} catch (IOException e) {
				e.printStackTrace();
				Log.e("doInBackground", "Writing failed");
			}

			return file.exists();
		}

		/**
		 * Checks if the phone's storage is writable.
		 * 
		 * @return boolean about writability
		 */
		public boolean isExternalStorageWritable() {
			String state = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(state)) {
				return true;
			}
			return false;
		}

		/**
		 * Gets the location provider that is most accurate without worrying about power usage.
		 * 
		 * @param locMan
		 * @return best provider
		 */
		public String getProvider(LocationManager locMan) {
			Criteria criteria = new Criteria();

			criteria.setAccuracy(Criteria.ACCURACY_FINE);
			criteria.setPowerRequirement(Criteria.POWER_HIGH);

			String provider = locMan.getBestProvider(criteria, true);
			return provider;
		}

		/**
		 * Sets location variable to new location when phone's location has changed.
		 * 
		 * Automatically setup with implementation of LocationListener
		 * @param loc
		 */
		@Override
		public void onLocationChanged(Location loc) {
			Log.i("doInBackground", "Location acquired");
			location = loc;


		}

		/**
		 * Automatically setup with implementation of LocationListener.
		 * 
		 * @param provider
		 * @param status
		 * @param extras
		 */
		@Override
		public void onStatusChanged(String provider, int status,
				Bundle extras) {
			Log.i("doInBackground", "Location status changed");
		}

		/**
		 * Automatically setup with implementation of LocationListener.
		 * 
		 * @param provider
		 */
		@Override
		public void onProviderEnabled(String provider) {
			Log.i("doInBackground", "Provider enabled");
		}

		/**
		 * Automatically setup with implementation of LocationListener.
		 * 
		 * @param provider
		 */
		@Override
		public void onProviderDisabled(String provider) {
			Log.i("doInBackground", "Provider disabled");
		}

	}

	/**
	 * Background task that cycles through WifiResult folder and sends text from saved scan results to CUSP server
	 * 
	 * @author Nicholas Hagans
	 *
	 */
	private class uploadAsyncTask extends AsyncTask<Void, Void, Void> {
		boolean lockedOn2 = false;
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wl2 = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Upload WakeLock");
		/**
		 * Contains uploading methods
		 * 
		 * @param params
		 */
		@Override
		protected Void doInBackground(Void... params) {
			wl2.acquire();
			while (isUploading) {
				float statusSize;
				float statusPercent;
				try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
				Log.i("doInBackground", "Looking for files to upload");
				List<String> tFileList = ReadSDCard();
				int tripped = 0;
				if (tFileList != null && !tFileList.isEmpty()) {
					Log.i("doInBackground", "About to start sorting");
					Collections.sort(tFileList);
					Log.i("doInBackground", "sorted!");
					JSONObject sendUp = new JSONObject();
					JSONArray scansArr = new JSONArray();
					int size = tFileList.size();
					Log.i("doInBackground", "Size!");
					//					String header = loadJSONFromAsset(tFileList.get(0)).toString();
					if (isScanning && size >= 100) {
						tripped = 1;
						for (int i = 0; i < 100; i++) {
							scansArr.put(loadJSONFromAsset(tFileList.get(i)));
						}
					}
					if (!isScanning && size < 100) {
						tripped = 1;
						for (int i = 0; i < size; i ++) {
							scansArr.put(loadJSONFromAsset(tFileList.get(i)));
						}
					}
					if (!isScanning && size >= 100) {
						Log.i("doInBackground", "About to get pieces");
						tripped = 1;
						for (int i = 0; i < 100; i++) {
							Log.i("doInBackground", "Getting pieces!");
							scansArr.put(loadJSONFromAsset(tFileList.get(i)));
						}
					}
					if (tripped == 1) {
						Log.i("doInBackground", "Got all pieces, about to make new JSON");
						final TextView quitStatus = (TextView) findViewById(R.id.quitStatus);
						runOnUiThread(new Thread() {
							public void run() {
								quitStatus.setText("Please don't kill app, uploading now!");
							}
						});
						try {
							sendUp.put("scans", scansArr);
							Log.i("doInBackground", "Putting in JSON");
						} catch (JSONException e1) {
							e1.printStackTrace();
						}
						//					
						//					File file = new File(Environment.getExternalStorageDirectory() + "/WifiResults/a.txt");
						//					try {
						//						FileWriter out = new FileWriter(file);
						//						out.append(header);
						//						out.close();
						//						Log.i("doInBackground", "Written to file");
						//					} catch (IOException e) {
						//						e.printStackTrace();
						//						Log.e("doInBackground", "Writing failed");
						//					}
						Log.i("doInBackground", "about to try to send up!");
						int TIMEOUT_MILLISEC = 60000; // = 60 seconds
						HttpParams httpParams = new BasicHttpParams();
						HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC);
						HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC);
						HttpClient client = new DefaultHttpClient(httpParams);
						HttpPost request = new HttpPost("");  // URL GOES HERE
						try {
							//Log.i("doInBackground", header);
							String L = sendUp.toString();
							Log.i("doInBackground", L);
							//						Log.i("doInBackground", L);
							//writeToFile(L);
							StringEntity se = new StringEntity(sendUp.toString());
							se.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
							request.setEntity(se);
							Log.i("doInBackground", "About to execute request!");
							HttpResponse response = client.execute(request);
							Log.i("doInBackground", "Request executed!");
							responseStr = EntityUtils.toString(response.getEntity());


							if(responseStr.equals("1")){
								Log.i("doInBackground", "Sent up!");
//								final TextView quitStatus = (TextView) findViewById(R.id.quitStatus);
								if (size >= 100) {
									for (int i = 0; i < 100; i++){
										Log.i("doInBackground", tFileList.get(i).toString() + "SENT UP NOW!");
										changeFileName(tFileList.get(i));
										final double num = i;
										runOnUiThread(new Thread() {
											public void run() {
												double percent = num;
												quitStatus.setText("Please don't kill app, " + num + "% done ");
											}
										});
										
									}
									deleteOldFiles();
									runOnUiThread(new Thread() {
										public void run() {
											quitStatus.setText("Safe to kill app!");
										}
									});
								} else if (size < 100) {
									for (int i = 0; i < size; i++) {
										Log.i("doInBackground", tFileList.get(i).toString() + "SENT UP NOW!");
										changeFileName(tFileList.get(i));
										final double num = i;
										final double denominator = size;
										runOnUiThread(new Thread() {
											public void run() {
												double percent = (num/denominator)*100.0;
												quitStatus.setText("Please don't kill app, " + percent + "% done ");
											}
										});
									}
									deleteOldFiles();
									runOnUiThread(new Thread() {
										public void run() {
											quitStatus.setText("Safe to kill app!");
										}
									});
								}

								//Sweet
								//delete files??? Do we want to store a number of files for safety
							}else{
								//Error
								Log.e("ResponseCode", responseStr);
								//writeToFile(L);
								//writeToFile(responseStr);
								runOnUiThread(new Thread() {
									public void run() {
										TextView errorText = (TextView) findViewById(R.id.error);
										errorText.setText(responseStr);
									}
								});
							}

						} catch (ClientProtocolException e) {e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
							// Log.e("doInBackground", e.getMessage());
						} 
					}

				}
			}
			wl2.release();
			return null;
		}

		/**
		 * Finds all text files in WifiResults folder and returns them as list.
		 * 
		 * @return List<String> tFileList list of text files
		 */
		public List<String> ReadSDCard() {
			File f = new File(Environment.getExternalStorageDirectory() + "/WifiResults");
			List<String> tFileList = new ArrayList<String>();
			File[] files = f.listFiles();

			for (int i = 0; i < files.length; i++) {
				File file = files[i];
				String filePath = file.getPath();
				if (filePath.endsWith(".txt")) {
					tFileList.add(filePath);
				}
			}
			return tFileList;
		}

		/**
		 * Read file at fileName, convert it to a JSON object, and return the JSON object.
		 * 
		 * @param fileName
		 * @return String jsonString contents of text files
		 */
		public JSONObject loadJSONFromAsset(String fileName) {
			String jsonLine = null;
			String jsonString = "";
			try {
				BufferedReader reader = new BufferedReader(new FileReader(fileName));
				while((jsonLine = reader.readLine()) != null) {
					jsonString = jsonString + jsonLine;
				}
				reader.close();

			} catch (IOException ex) {
				ex.printStackTrace();
				return null;
			}
			JSONObject json = null;
			try {
				json = new JSONObject(jsonString);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return json;
		}

		/**
		 * Changes the name of fileName file to one with '.done' appended so other method won't reupload it.
		 * 
		 * @param fileName
		 */
		public void changeFileName(String fileName) {
			File f = new File(Environment.getExternalStorageDirectory() + "/WifiResults");
			File[] files = f.listFiles();
			boolean found = false;
			int i = 0;
			while(!found) {
				File file = files[i];
				String filePath = file.getPath();
				if (filePath.equals(fileName)) {
					String newName = fileName + ".done";
					File newFileName = new File(newName);
					file.renameTo(newFileName);
					found = true;
				}
				i+=1;
			}
		}

		/**
		 * Deletes oldest 100 files that end with .done (already sent up)
		 * 
		 */
		public void deleteOldFiles() {
			File f = new File(Environment.getExternalStorageDirectory() + "/WifiResults");
			List<String> oldFiles = new ArrayList<String>();
			File[] files = f.listFiles();
			for (int i = 0; i < files.length; i++) {
				File file = files[i];
				String filePath = file.getPath();
				if (filePath.endsWith(".done")) {
					oldFiles.add(filePath);
				}
			}
			if (oldFiles != null && !oldFiles.isEmpty() && oldFiles.size() > 100) {
				Collections.sort(oldFiles);
				for (int i = 0; i < 100; i++) {
					for (int j = 0; j < files.length; j++) {
						String oldFile = oldFiles.get(i);
						File file = files[j];
						String filePath = file.getPath();
						if (filePath.equals(oldFile)) {
							file.delete();
						}
					}
				}
			}
		}

		public boolean writeToFile(String entry){
			String fileName = "b.tmp";
			String finalFileName = "b.txt";
			File finalFile = new File(Environment.getExternalStorageDirectory() + "/WifiResults" + finalFileName);
			File file = new File(Environment.getExternalStorageDirectory() + "/WifiResults" + fileName);
			Log.i("doInBackground", file.getAbsolutePath());
			try {
				FileWriter out = new FileWriter(file);
				out.append(entry);
				out.close();
				file.renameTo(finalFile);
				Log.i("doInBackground", "Written to file");
			} catch (IOException e) {
				e.printStackTrace();
				Log.e("doInBackground", "Writing failed");
			}

			return file.exists();
		}
	}



	//	@Override
	//	public boolean onCreateOptionsMenu(Menu menu) {
	//
	//		// Inflate the menu; this adds items to the action bar if it is present.
	//		getMenuInflater().inflate(R.menu.main, menu);
	//		return true;
	//	}
	//
	//	@Override
	//	public boolean onOptionsItemSelected(MenuItem item) {
	//		// Handle action bar item clicks here. The action bar will
	//		// automatically handle clicks on the Home/Up button, so long
	//		// as you specify a parent activity in AndroidManifest.xml.
	//		int id = item.getItemId();
	//		if (id == R.id.action_settings) {
	//			return true;
	//		}
	//		return super.onOptionsItemSelected(item);
	//	}

}
