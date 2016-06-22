package gsoc.google.com.byop.ui.poisList;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.PermissionUtils;

/**
 * Created by lgwork on 30/05/16.
 */
public class CreatePOIMapFragment extends Fragment implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, GoogleMap.OnMapLongClickListener, GoogleMap.OnMyLocationButtonClickListener {

    protected FragmentStackManager fragmentStackManager;

    private static String TAG = CreatePOIMapFragment.class.toString();

    public static final String ARG_DOCUMENT = "document";

    private GoogleMap googleMap;
    private GoogleApiClient googleApiClient;
    private DriveDocument document;
    private String accountEmail;

    private LocationRequest locationRequest;

    private double latitude = 41.5583194;
    private double longitude = 0.675144;

    private static int UPDATE_INTERVAL = 10000; // 10 sec
    private static int FATEST_INTERVAL = 5000; // 5 sec

    public static CreatePOIMapFragment newInstance(DriveDocument document) {
        CreatePOIMapFragment createPOIMapFragment = new CreatePOIMapFragment();

        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_DOCUMENT, document);

        createPOIMapFragment.setArguments(bundle);

        return createPOIMapFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem itemLogout = menu.findItem(R.id.action_logout);
        itemLogout.setVisible(false);
        MenuItem itemDisconnect = menu.findItem(R.id.action_disconnect);
        itemDisconnect.setVisible(false);
        MenuItem itemSettings = menu.findItem(R.id.action_settings);
        itemSettings.setVisible(false);
        MenuItem aboutSettins = menu.findItem(R.id.action_about);
        aboutSettins.setVisible(false);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.poi_map, container, false);
        fragmentStackManager = FragmentStackManager.getInstance(getActivity());
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        document = getArguments().getParcelable(ARG_DOCUMENT);
        SharedPreferences settings = this.getActivity().getPreferences(Context.MODE_PRIVATE);

        accountEmail = settings.getString(Constants.PREF_ACCOUNT_EMAIL, "");

        getActivity().setTitle(getResources().getString(R.string.new_poi) + " in " + document.getTitle());

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        googleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.setOnMapLongClickListener(this);
        googleMap.setOnMyLocationButtonClickListener(this);
        enableMyLocation();
        LatLng latLong = new LatLng(latitude, longitude);

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLong, 10));
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(getActivity(), Constants.LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (googleMap != null) {
            // Access to the location has been granted to the app.
            googleMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(getActivity(), "MyLocation button clicked", Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FATEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //  locationRequest.setSmallestDisplacement(DISPLACEMENT);
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    private void stopLocationUpdates() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {/*Do Nothing*/}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {/*Do Nothing*/}

    @Override
    public void onMapLongClick(LatLng latLng) {
        googleMap.clear();

        MarkerOptions marker = new MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE));
        marker.position(latLng);
        googleMap.addMarker(marker);

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getCameraPosition().zoom));

        CreatePOIDialogFragment createPoiDialog = CreatePOIDialogFragment.newInstance(latLng.latitude, latLng.longitude, document);
        fragmentStackManager.loadFragment(createPoiDialog, R.id.map);

    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.e(TAG, "position: " + location.getLatitude() + ", " + location.getLongitude() + " accuracy: " + location.getAccuracy());
            // we have our desired accuracy of 10 meters so lets quit this service,
            // onDestroy will be called and stop our location uodates
            if (location.getAccuracy() <= 10) {
                stopLocationUpdates();
            }
        }
    }
}
