package gsoc.google.com.byop.ui.poisList;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import gsoc.google.com.byop.R;

/**
 * Created by lgwork on 27/05/16.
 */
public class ViewPOIMapFragment extends Fragment implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static final String POI_LOCATION_LON = "LONGITUDE";
    public static final String POI_LOCATION_LAT = "LATITUDE";
    public static final String POI_NAME = "POI_NAME";
    public static final String POI_DESC = "POI_DESC";

    public String poiLatitude;
    public String poiLongitude;
    public String poiName;
    public String poiDescription;

    LatLng latLon;

    private GoogleMap googleMap;
    private GoogleApiClient googleApiClient;

    public static ViewPOIMapFragment newInstance(String poiLatitude, String poiLongitude, String poiName, String poiDescription) {
        ViewPOIMapFragment poiMapFragment = new ViewPOIMapFragment();
        Bundle bundle = new Bundle();
        bundle.putString(POI_LOCATION_LON, poiLongitude);
        bundle.putString(POI_LOCATION_LAT, poiLatitude);
        bundle.putString(POI_NAME, poiName);
        bundle.putString(POI_DESC, poiDescription);
        poiMapFragment.setArguments(bundle);
        return poiMapFragment;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.poi_map, container, false);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        poiLatitude = getArguments().getString(POI_LOCATION_LAT);
        poiLongitude = getArguments().getString(POI_LOCATION_LON);
        poiName = getArguments().getString(POI_NAME);
        poiDescription = getArguments().getString(POI_DESC);


        latLon = new LatLng(Double.parseDouble(poiLatitude), Double.parseDouble(poiLongitude));


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
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    public void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }


    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        // googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLon, 17));
        MarkerOptions marker = new MarkerOptions().position(latLon).title(poiName).snippet(poiDescription);
        marker.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE));
        googleMap.addMarker(marker);
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onConnected(Bundle bundle) {/*Do Nothing*/}

    @Override
    public void onConnectionSuspended(int i) {/*Do Nothing*/}

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {/*Do Nothing*/}
}
