package com.example.TripTracker;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.PolylineOptions;

/**
 * An activity that displays a map showing the place at the device's current location.
 */
public class TripMap extends AppCompatActivity
        implements OnMapReadyCallback,
                GoogleApiClient.ConnectionCallbacks,
                GoogleApiClient.OnConnectionFailedListener,
                TripManager.TripDrawer,
                OnClickListener {

    public static final String TAG = TripMap.class.getSimpleName();
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // The entry point to Google Play services, used by the Places API and Fused Location Provider.
    private GoogleApiClient mGoogleApiClient;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // Used for selecting the current place.
    private final int mMaxEntries = 5;
    private String[] mLikelyPlaceNames = new String[mMaxEntries];
    private String[] mLikelyPlaceAddresses = new String[mMaxEntries];
    private String[] mLikelyPlaceAttributions = new String[mMaxEntries];
    private LatLng[] mLikelyPlaceLatLngs = new LatLng[mMaxEntries];

    private Timer statusTimer;
    private int tripDuration; // in seconds

    private LocationListener locationListener;
    private static LocationManager locationManager=null;

    private static TripManager tripMan;

    private Menu optionMenu;

    private View mTripStatusView;
    private TextView mTripDistanceText;
    private TextView mTripDistanceTitle;
    private View mTripSpeedView;
    private TextView mTripSpeedText;
    private View mTripDurationView;
    private TextView mTripDurationText;

    private WakeLock wakeLock;

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_trip);

        // Build the Play services client for use by the Fused Location Provider and the Places API.
        // Use the addApi() method to request the Google Places API and the Fused Location Provider.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,
                        0 /* GOOGLE_API_CLIENT_ID */,
                        this /* OnConnectionFailedListener */)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();
    //    mGoogleApiClient.connect();



        tripDuration = 0;

        statusTimer = new Timer();
        statusTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (tripMan != null && tripMan.isTracking()) {
                    tripDuration++;
                    if (mTripDurationText != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTripDurationText.setText(convertDurationToString(tripDuration));
                            }
                        });
                    }
                }
            }
        }, 0, 1000);


        locationListener = new LocationListener()
        {

            public void onLocationChanged(Location location) {
                if (tripMan != null) {
                    tripMan.updateCurrentLocation(location);
                }
                updateSpeed(location.getSpeed() * 3600 / 1000);
            }

            @Override
            public void onProviderDisabled(String provider) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onProviderEnabled(String provider) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStatusChanged(String provider, int status,Bundle extras) {
                // TODO Auto-generated method stub
            }
        };


        try {
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

            Criteria myCriteria = new Criteria();
            myCriteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationManager.requestLocationUpdates(0L, // minTime
                    0.0f, // minDistance
                    myCriteria, // criteria
                    locationListener, // listener
                    null);
        } catch (SecurityException e) {}

        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "mWakeLock");
        }

        mPrefs = getSharedPreferences("", 0);
    }


    //TODO: get wakelock to remember status when device is rotated
/*
    @Override
    public void onResume() {
        super.onResume();

        Toast.makeText(getApplicationContext(), "called resume", Toast.LENGTH_SHORT).show();
        if (mPrefs.getBoolean("isHeld", false)) {
            wakeLock.acquire();
            optionMenu.getItem(6).setTitle("Disable wake lock");
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Toast.makeText(getApplicationContext(), "called pause", Toast.LENGTH_SHORT).show();
        SharedPreferences.Editor ed = mPrefs.edit();
        ed.putBoolean("isHeld", wakeLock.isHeld());
        ed.commit();
    }
    */

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Builds the map when the Google Play services client is successfully connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Handles failure to connect to the Google Play services client.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        // Refer to the reference doc for ConnectionResult to see what error codes might
        // be returned in onConnectionFailed.
        Log.d(TAG, "Play services connection failed: ConnectionResult.getErrorCode() = "
                + result.getErrorCode());
    }

    /**
     * Handles suspension of the connection to the Google Play services client.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "Play services connection suspended");
    }

    /**
     * Sets up the options menu.
     * @param menu The options menu.
     * @return Boolean.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.trip_menu, menu);
        optionMenu = menu;

        MenuItem item = menu.findItem(R.id.action_trip_status);
        if (item != null) {
            mTripStatusView = item.getActionView();
            if (mTripStatusView != null) {
                mTripDistanceText = (TextView) (mTripStatusView.findViewById(R.id.trip_distance));
                mTripDistanceTitle = (TextView) (mTripStatusView.findViewById(R.id.trip_distance_title));
                //TripStatusView.setOnClickListener(this);
            }
        }

        item = menu.findItem(R.id.action_trip_speed);
        if (item != null) {
            mTripSpeedView = item.getActionView();
            if (mTripSpeedView != null) {
                mTripSpeedText = (TextView) (mTripSpeedView.findViewById(R.id.trip_speed));
            }
        }

        item = menu.findItem(R.id.action_trip_duration);
        if (item !=null) {
            mTripDurationView = item.getActionView();
            if (mTripDurationView != null) {
                mTripDurationText = (TextView) (mTripDurationView.findViewById(R.id.trip_duration));
            }
        }

        return true;
    }

    @Override
    public void onClick(View v) {

    }


    /**
     * Handles a click on the menu option to get a place.
     * @param item The menu item to handle.
     * @return Boolean.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.option_switch_mode) {
            mMap.clear();
            mTripDistanceText.setText("-");
            if (tripMan != null) {
                tripMan.switchView();
                Toast.makeText(getApplicationContext(), "Now displaying " + tripMan.getViewModeString(), Toast.LENGTH_SHORT).show();
                tripMan.redrawTrip();

                if (tripMan.getViewMode() == tripMan.MODE_CURR) {
                    item.setTitle("Show past trip");
                    mTripDistanceTitle.setText("Current Trip");
                } else {
                    item.setTitle("Show current trip");
                    mTripDistanceTitle.setText("Past Trip");
                }
            }
        } else if (item.getItemId() == R.id.option_startstop) {
            if(tripMan != null) {
                if (tripMan.isTracking()) {
                    tripMan.stopTrip();
                    Toast.makeText(getApplicationContext(), "Now stopping trip", Toast.LENGTH_SHORT).show();
                    item.setTitle("Start tracking");
                } else {
                    tripMan.startTrip();
                    Toast.makeText(getApplicationContext(), "Now starting trip", Toast.LENGTH_SHORT).show();
                    item.setTitle("Stop tracking");
                }
            }
        } else if (item.getItemId() == R.id.option_reset_past_trip) {
            if (tripMan != null) {
                tripMan.resetPastTrip();
            }
        } else if (item.getItemId() == R.id.option_share_trip) {
            Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setType("*/*");
            shareIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] {"wuperry2@gmail.com"});
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                    "Test Subject");
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                    "go on read the emails");
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(getFilesDir(), "trip.txt")));
            startActivity(Intent.createChooser(shareIntent, "Send mail..."));
        } else if (item.getItemId() == R.id.option_wakelock) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
                Toast.makeText(getApplicationContext(), "Disabling wake lock", Toast.LENGTH_SHORT).show();
                item.setTitle("Enable wake lock");
            } else {
                wakeLock.acquire();
                Toast.makeText(getApplicationContext(), "Enabling wake lock", Toast.LENGTH_SHORT).show();
                item.setTitle("Disable wake lock");
            }
        }
        return true;
    }

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout)findViewById(R.id.map), false);

                TextView title = ((TextView) infoWindow.findViewById(R.id.title));
                title.setText(marker.getTitle());

                TextView snippet = ((TextView) infoWindow.findViewById(R.id.snippet));
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });



        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        if (tripMan == null) {
            tripMan = new TripManager(this, 0 /*tripMan.MODE_CURR*/);
            tripMan.startTrip();
            tripMan.setTripFile("trip.txt");
        }
        tripMan.setTripDrawer(this);
        tripMan.redrawTrip();
    }

    @Override
    public void onDrawTripLine(PolylineOptions options) {
        if (mMap != null)
            mMap.addPolyline(options);
    }

    @Override
    public void onDrawDistance(String distance) {
        if (mTripDistanceText != null)
            mTripDistanceText.setText(distance);
    }

    public void updateSpeed(float speed) {
        if (mTripSpeedText != null)
            mTripSpeedText.setText(round(speed, 1) + "km/h");
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(this.getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                   PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        if (mLocationPermissionGranted) {
            mLastKnownLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }

        // Set the map's camera position to the current location of the device.
        if (mCameraPosition != null) {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(mCameraPosition));
        } else if (mLastKnownLocation != null) {
            LatLng loc = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, DEFAULT_ZOOM));
        } else {
            Log.d(TAG, "Current location is null. Using defaults.");
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */

        if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        }/*  else if (mLocationPermissionGranted) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } */


        if (mLocationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            mMap.setMyLocationEnabled(false);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            mLastKnownLocation = null;
        }
    }

    // turns a given time (in seconds) into a string format
    private String convertDurationToString(int duration) {
        int seconds = duration % 60;
        duration /= 60;
        int minutes = duration % 60;
        int hours = duration / 60;

        String result = "";
        if (hours < 10)
            result += "0";
        result += hours + ":";
        if (minutes < 10)
            result += "0";
        result += minutes + ":";
        if (seconds < 10)
            result += "0";
        result += seconds;

        return result;
    }

    // decimals = # of decimal points to round to (must be >= 1)
    private double round(double x, int decimals) {
        return Math.round(x * Math.pow(10, decimals)) / Math.pow(10, decimals);
    }

}
