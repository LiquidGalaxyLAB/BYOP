package gsoc.google.com.byop.ui.poisList;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.model.File;
import com.poliveira.parallaxrecyclerview.ParallaxRecyclerAdapter;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.model.POI;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.BYOPXmlPullParser;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;
import gsoc.google.com.byop.utils.POIUtils;

/**
 * Created by lgwork on 26/05/16.
 */
public class POISListFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    protected FragmentStackManager fragmentStackManager;

    private RecyclerView rv = null;
    private ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter;
    private SwipeRefreshLayout refreshLayout;
    private RequestContentsTask requestContentsTask;
    private FloatingActionButton fab;

    public static final String ARG_DOCUMENT = "document";
    private DriveDocument document;
    private String accountEmail;

    private POIUtils poiUtils;

    private static POISListFragment poisFragment;

    private GoogleApiClient mGoogleApiClient;

    GoogleAccountCredential mCredential;

    ImageButton poisListHelpBtn;

    public static POISListFragment getInstance() {
        if (poisFragment != null) {
            poisFragment = POISListFragment.newInstance(poisFragment.document);
            return poisFragment;
        } else {
            poisFragment = new POISListFragment();
            return poisFragment;
        }
    }

    public static POISListFragment newInstance(DriveDocument document) {
        poisFragment = new POISListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_DOCUMENT, document);

        poisFragment.setArguments(bundle);
        return poisFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.getParcelable("document") != null) {
            document = savedInstanceState.getParcelable(ARG_DOCUMENT);
        } else if (document == null) {
            document = getArguments().getParcelable(ARG_DOCUMENT);
        } else {
            requestContentsTask = new RequestContentsTask(mCredential);
        }

        getActivity().setTitle(getResources().getString(R.string.poisList) + " " + document.getTitle());

        setHasOptionsMenu(true);

        fragmentStackManager = FragmentStackManager.getInstance(getContext());

        refreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        populateUI();
                    }
                }
        );

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CreatePOIMapFragment createPOIMapFragment = CreatePOIMapFragment.newInstance(document);
                fragmentStackManager.loadFragment(createPOIMapFragment, R.id.main_frame);
            }
        });

        if (requestContentsTask != null) {
            requestContentsTask.execute();
        }
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

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.pois_list, container, false);

        rv = (RecyclerView) rootView.findViewById(R.id.rvPOIS);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(true);
        refreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefreshPois);
        fab = (FloatingActionButton) rootView.findViewById(R.id.add_POI);

        mGoogleApiClient = new GoogleApiClient.Builder(this.getActivity())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(this.getActivity(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());

        SharedPreferences settings = this.getActivity().getPreferences(Context.MODE_PRIVATE);

        accountEmail = settings.getString(Constants.PREF_ACCOUNT_EMAIL, "");

        mCredential.setSelectedAccountName(accountEmail);

        poisListHelpBtn = (ImageButton) rootView.findViewById(R.id.poisListHelp);

        poisListHelpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // custom dialog
                final Dialog dialog = new Dialog(getContext());
                dialog.setContentView(R.layout.help_pois_list_dialog);
                dialog.setTitle(getResources().getString(R.string.poisListHelpTitle));

                Button dialogButton = (Button) dialog.findViewById(R.id.dialogPoisButtonOK);
                dialogButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
                dialog.show();
            }
        });

        return rootView;
    }

    private void populateUI() {
        requestContentsTask = new RequestContentsTask(mCredential);
        requestContentsTask.execute();
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable("document", document);
        super.onSaveInstanceState(outState);
    }


    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    @Override
    public void onConnected(Bundle bundle) {
        populateUI();
    }

    @Override
    public void onConnectionSuspended(int i) {/*Do Nothing*/}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this.getActivity(), Constants.SERVICE_INVALID_POIS_LIST);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), getActivity(), 0).show();
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_AUTHORIZATION_POIS_LIST) {
            fragmentStackManager.popBackStatFragment();
            POISListFragment poisListFragment = POISListFragment.newInstance(document);
            fragmentStackManager.loadFragment(poisListFragment, R.id.main_frame);
        } else if (requestCode == Constants.SERVICE_INVALID_POIS_LIST) {
            requestContentsTask = new RequestContentsTask(mCredential);
            requestContentsTask.execute();
        }
    }

    private class POIHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        TextView poiName;
        TextView poiDescription;
        TextView poiLatitude;
        TextView poiLongitude;
        ImageView filePhoto;

        String latitude;
        String longitude;

        public POIHolder(View itemView) {
            super(itemView);
            poiName = (TextView) itemView.findViewById(R.id.poi_name);
            poiDescription = (TextView) itemView.findViewById(R.id.poi_description);
            filePhoto = (ImageView) itemView.findViewById(R.id.file_photo_poi);
            poiLatitude = (TextView) itemView.findViewById(R.id.poi_latitude);
            poiLongitude = (TextView) itemView.findViewById(R.id.poi_longitude);
            itemView.setOnCreateContextMenuListener(this);

            Toolbar toolbarCard = (Toolbar) itemView.findViewById(R.id.poisToolbar);
            if (toolbarCard != null) {
                toolbarCard.inflateMenu(R.menu.menu_pois_cardview);
                toolbarCard.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.viewPOIMenuItem:
                                ViewPOIMapFragment poiMapFragment = ViewPOIMapFragment.newInstance(latitude, longitude, poiName.getText().toString(), poiDescription.getText().toString());
                                fragmentStackManager.loadFragment(poiMapFragment, R.id.main_frame);
                                break;
                            case R.id.editPoiMenuItem:
                                EditPOIMapFragment editPOIMapFragment = EditPOIMapFragment.newInstance(latitude, longitude, poiName.getText().toString(), poiDescription.getText().toString());

                                editPOIMapFragment.setDriveDocument(document);

                                fragmentStackManager.loadFragment(editPOIMapFragment, R.id.main_frame);
                                break;
                            case R.id.deletePoiMenuItem:
                                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                                alert.setTitle(getResources().getString(R.string.are_you_sure));

                                alert.setPositiveButton(getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        //Delete POI
                                        poiUtils = new POIUtils(poiName.getText().toString(), poiDescription.getText().toString(),
                                                latitude, longitude, document, mGoogleApiClient, mCredential, getActivity(), poisFragment);

                                        poiUtils.deletePOI();

                                        populateUI();
                                    }
                                });

                                alert.setNegativeButton(getResources().getString(R.string.no),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                            }
                                        });

                                alert.show();
                                break;
                        }
                        return true;
                    }
                });
            }
        }


        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(getResources().getString(R.string.context_menu_title));

            MenuItem deleteItem = menu.add(0, v.getId(), 2, R.string.context_menu_delete);
            deleteItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {

                    AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                    alert.setTitle(getResources().getString(R.string.are_you_sure));

                    alert.setPositiveButton(getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            //Delete POI
                            poiUtils = new POIUtils(poiName.getText().toString(), poiDescription.getText().toString(),
                                    latitude, longitude, document, mGoogleApiClient, mCredential, getActivity(), poisFragment);

                            poiUtils.deletePOI();

                            populateUI();
                        }
                    });

                    alert.setNegativeButton(getResources().getString(R.string.no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                }
                            });

                    alert.show();
                    return true;
                }
            });

            MenuItem editItem = menu.add(0, v.getId(), 1, R.string.context_menu_edit);
            editItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    //Edit POI

                    EditPOIMapFragment editPOIMapFragment = EditPOIMapFragment.newInstance(latitude, longitude, poiName.getText().toString(), poiDescription.getText().toString());

                    editPOIMapFragment.setDriveDocument(document);

                    fragmentStackManager.loadFragment(editPOIMapFragment, R.id.main_frame);
                    return true;
                }
            });

            MenuItem viewItem = menu.add(0, v.getId(), 0, R.string.context_menu_view);
            viewItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    //View POI
                    ViewPOIMapFragment poiMapFragment = ViewPOIMapFragment.newInstance(latitude, longitude, poiName.getText().toString(), poiDescription.getText().toString());
                    fragmentStackManager.loadFragment(poiMapFragment, R.id.main_frame);
                    return true;
                }
            });

        }
    }


    public void fillAdapter(final List<POI> poisList) {
        parallaxRecyclerAdapter = new ParallaxRecyclerAdapter<POI>(poisList) {
            @Override
            public void onBindViewHolderImpl(RecyclerView.ViewHolder viewHolder, ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter, int i) {
                POI poi = parallaxRecyclerAdapter.getData().get(i);

                POIHolder poiHolder = (POIHolder) viewHolder;
                poiHolder.poiName.setText(poi.getName());
                poiHolder.poiDescription.setText(poi.getDescription());

                poiHolder.poiLatitude.setText("Lat:" + poi.getPoint().getLatitude());
                poiHolder.poiLongitude.setText("Lon:" + poi.getPoint().getLongitude());

                poiHolder.latitude = poi.getPoint().getLatitude();
                poiHolder.longitude = poi.getPoint().getLongitude();

                poiHolder.filePhoto.setImageDrawable(ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.poi_icon));
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
                super.onBindViewHolder(viewHolder, i);
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolderImpl(ViewGroup viewGroup, ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter, int i) {
                return new POIHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.poi_list_item_card, viewGroup, false));
            }

            @Override
            public int getItemCountImpl(ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter) {
                return poisList.size();
            }
        };

        parallaxRecyclerAdapter.setParallaxHeader(getActivity().getLayoutInflater().inflate(R.layout.poi_list_header_layout, rv, false), rv);

        rv.setAdapter(parallaxRecyclerAdapter);

        //On click on recycler view item
        parallaxRecyclerAdapter.setOnClickEvent(new ParallaxRecyclerAdapter.OnClickEvent() {
            @Override
            public void onClick(View view, int i) {
                POI poi = poisList.get(i);
                ViewPOIMapFragment poiMapFragment = ViewPOIMapFragment.newInstance(poi.getPoint().getLatitude(), poi.getPoint().getLongitude(),
                        poi.getName(), poi.getDescription());
                fragmentStackManager.loadFragment(poiMapFragment, R.id.main_frame);
            }
        });
    }


    private class RequestContentsTask extends AsyncTask<Void, Void, List<POI>> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private ProgressDialog dialog;

        private List<POI> innerPOIList = null;

        public RequestContentsTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(getContext());
                dialog.setMessage(getActivity().getResources().getString(R.string.loading));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        requestContentsTask.cancel(true);
                        dialog.dismiss();
                    }
                });
                dialog.show();
            }
        }


        @Override
        protected List<POI> doInBackground(Void... params) {
            try {
                return getFileContentsFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                if(dialog!=null){
                    dialog.dismiss();
                }
            }
            return null;
        }

        private List<POI> getFileContentsFromApi() throws IOException {

            File driveFile = mService.files().get(document.getResourceId()).execute();

            //DRIVE API
            Drive.DriveApi.fetchDriveId(mGoogleApiClient, driveFile.getId()).setResultCallback(new ResultCallback<DriveApi.DriveIdResult>() {
                @Override
                public void onResult(@NonNull DriveApi.DriveIdResult driveIdResult) {
                    if (driveIdResult.getStatus().isSuccess()) {
                        final DriveFile[] file = {Drive.DriveApi.getFile(getGoogleApiClient(), driveIdResult.getDriveId())};
                        file[0].open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null)
                                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                                    @Override
                                    public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                                        if (!driveContentsResult.getStatus().isSuccess()) {
                                            // display an error saying file can't be opened
                                            return;
                                        }
                                        // DriveContents object contains pointers
                                        // to the actual byte stream
                                        DriveContents contents = driveContentsResult.getDriveContents();
                                        try {
                                            innerPOIList = new ArrayList<>();
                                            innerPOIList = checkContents(contents.getInputStream());

                                            if (innerPOIList != null) {
                                                fillAdapter(innerPOIList);
                                                if (dialog != null) {
                                                    dialog.hide();
                                                    dialog.dismiss();
                                                }
                                                refreshLayout.setRefreshing(false);
                                            }

                                        } catch (IOException e) {
                                            e.printStackTrace();
                                            return;
                                        } catch (XmlPullParserException e) {
                                            e.printStackTrace();
                                            return;
                                        }
                                    }

                                    private List<POI> checkContents(InputStream inputStream) throws IOException, XmlPullParserException {
                                        BYOPXmlPullParser parser = new BYOPXmlPullParser();

                                        List<POI> poiList = parser.parse(inputStream, getActivity());

                                        return poiList;
                                    }
                                });
                    } else {
                        AndroidUtils.showMessage(getResources().getString(R.string.something_wrong) + driveIdResult.getStatus(), getActivity());
                        dialog.dismiss();
                        return;
                    }
                }
            });
            //while (!isCompleted) {/*wait for complete*/}
            return innerPOIList;
        }


        @Override
        protected void onPostExecute(List<POI> output) {
            super.onPostExecute(output);
            if (output != null) {
                fillAdapter(output);
                if (dialog != null) {
                    dialog.hide();
                    dialog.dismiss();
                }
                refreshLayout.setRefreshing(false);
            }
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(),
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    dialog.dismiss();
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION_POIS_LIST);
                } else {
                    AndroidUtils.showMessage((getResources().getString(R.string.following_error) + "\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
        }
    }
}
