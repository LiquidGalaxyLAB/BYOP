package gsoc.google.com.byop.ui.poisList;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.model.POI;
import gsoc.google.com.byop.model.Point;
import gsoc.google.com.byop.utils.FragmentStackManager;

/**
 * Created by lgwork on 30/05/16.
 */
public class EditPOIMapFragment extends Fragment implements GoogleMap.OnMarkerDragListener, OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    protected FragmentStackManager fragmentStackManager;

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

    private DriveDocument driveDoc;

    POI managedPoi;

    public static EditPOIMapFragment newInstance(String poiLatitude, String poiLongitude, String poiName, String poiDescription) {
        EditPOIMapFragment editPoiMapFragment = new EditPOIMapFragment();
        Bundle bundle = new Bundle();
        bundle.putString(POI_LOCATION_LON, poiLongitude);
        bundle.putString(POI_LOCATION_LAT, poiLatitude);
        bundle.putString(POI_NAME, poiName);
        bundle.putString(POI_DESC, poiDescription);
        editPoiMapFragment.setArguments(bundle);
        return editPoiMapFragment;
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
        return inflater.inflate(R.layout.poi_map, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        poiName = getArguments().getString(POI_NAME);

        getActivity().setTitle(getResources().getString(R.string.editing_poi) + " " + poiName);

        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        poiLatitude = getArguments().getString(POI_LOCATION_LAT);
        poiLongitude = getArguments().getString(POI_LOCATION_LON);

        poiDescription = getArguments().getString(POI_DESC);

        latLon = new LatLng(Double.parseDouble(poiLatitude), Double.parseDouble(poiLongitude));

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        googleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        managedPoi = new POI();
        managedPoi.setName(poiName);
        managedPoi.setDescription(poiDescription);
        Point point = new Point();
        point.setLatitude(poiLatitude);
        point.setLongitude(poiLongitude);
        managedPoi.setPoint(point);
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
        MarkerOptions marker = new MarkerOptions().draggable(true).position(latLon).title(poiName).snippet(poiDescription).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE));

        googleMap.addMarker(marker);
        googleMap.setOnMarkerDragListener(this);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {/*Do Nothing*/}

    @Override
    public void onConnectionSuspended(int i) {/*Do Nothing*/}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {/*Do Nothing*/}

    @Override
    public void onMarkerDragStart(Marker marker) {/*Do Nothing*/}

    @Override
    public void onMarkerDrag(Marker marker) {/*Do Nothing*/}

    @Override
    public void onMarkerDragEnd(Marker marker) {

        EditPOIDataFragment fragment = EditPOIDataFragment.newInstance(marker.getPosition().latitude, marker.getPosition().longitude, poiName, poiDescription);

        fragment.setDriveDocument(this.driveDoc);
        fragment.setManagedPoi(managedPoi);

        fragmentStackManager.loadFragment(fragment, R.id.map);
    }

    public void setDriveDocument(DriveDocument driveDocument) {
        this.driveDoc = driveDocument;
    }
}
