package gsoc.google.com.byop.ui.documentsList;

import android.Manifest;
import android.accounts.AccountManager;
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
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filter;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.poliveira.parallaxrecyclerview.ParallaxRecyclerAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.ui.poisList.POISListFragment;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;
import gsoc.google.com.byop.utils.PW.BeaconConfigFragment;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by lgwork on 23/05/16.
 */
public class FolderListFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, EasyPermissions.PermissionCallbacks {

    protected FragmentStackManager fragmentStackManager;
    GoogleAccountCredential mCredential;
    DriveId rootDriveIdFolder;
    private RecyclerView rv = null;
    private SwipeRefreshLayout refreshLayout;
    private FloatingActionButton fab;
    private GoogleApiClient mGoogleApiClient;
    private String folderId = "";
    private MakeRequestTask requestTask;
    private MakeDeleteTask deleteTask;
    private CreationTask creationTask;


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        getActivity().setTitle(getActivity().getResources().getString(R.string.app_name) + " " + getResources().getString(R.string.folderList));

        fragmentStackManager = FragmentStackManager.getInstance(getActivity());
        refreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        populateUI(folderId);
                    }
                }
        );

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CreateDocumentFragment newDocumentFragment = CreateDocumentFragment.newInstance(folderId);
                fragmentStackManager.loadFragment(newDocumentFragment, R.id.main_frame);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.folder_list, container, false);

        rv = (RecyclerView) rootView.findViewById(R.id.rv);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(true);
        refreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefresh);
        fab = (FloatingActionButton) rootView.findViewById(R.id.add_document);


        mGoogleApiClient = new GoogleApiClient.Builder(this.getActivity())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());

        return rootView;
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


    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    @Override
    public void onConnected(Bundle bundle) {

        final String folderName = getResources().getString(R.string.folderName);

        rootDriveIdFolder = Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId();


        ArrayList<Filter> fltrs = new ArrayList<>();
        fltrs.add(Filters.eq(SearchableField.TITLE, folderName));
        fltrs.add(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"));
        Query qry = new Query.Builder().addFilter(Filters.and(fltrs)).build();


        Drive.DriveApi.query(mGoogleApiClient, qry).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
            DriveId byopFolderId = null;

            @Override
            public void onResult(DriveApi.MetadataBufferResult result) {

                if (result.getStatus().isSuccess()) {
                    boolean isFound = false;
                    for (Metadata m : result.getMetadataBuffer()) {
                        if (!isFound) {
                            if (!m.isTrashed() && m.getTitle().equals(folderName)) {
                                //Folder exists
                                isFound = true;
                                byopFolderId = m.getDriveId();
                            }
                        }
                    }
                    if (isFound) {
                        //Existing Folder, fetch its contents
                        DriveFolder folder = Drive.DriveApi.getFolder(getGoogleApiClient(), byopFolderId);
                        folderId = byopFolderId.getResourceId();
                        getFilesFromApi();
                    } else {
                        createFolderThroughApi(folderName);
                    }
                }
            }
        });
    }

    private void createFolderThroughApi(String folderName) {
        if (!GooglePlayUtils.isGooglePlayServicesAvailable(this.getActivity())) {
            GooglePlayUtils.acquireGooglePlayServices(this.getActivity());
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccountForCreation(folderName);
        } else if (!GooglePlayUtils.isDeviceOnline(this.getActivity())) {
            AndroidUtils.showMessage(getResources().getString(R.string.no_network_connection), getActivity());
        } else {
            creationTask = new CreationTask(folderName);
            creationTask.execute();
        }
    }

    @AfterPermissionGranted(Constants.REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccountForCreation(String folderName) {
        if (EasyPermissions.hasPermissions(
                this.getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = this.getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                createFolderThroughApi(folderName);
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        Constants.REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this.getActivity(), getResources().getString(R.string.google_account_needed),
                    Constants.REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    private void deleteFilesThroughApi(String fileResourceId) {
        if (!GooglePlayUtils.isGooglePlayServicesAvailable(this.getActivity())) {
            GooglePlayUtils.acquireGooglePlayServices(this.getActivity());
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccountForDeletion(fileResourceId);
        } else if (!GooglePlayUtils.isDeviceOnline(this.getActivity())) {
            AndroidUtils.showMessage(getResources().getString(R.string.no_network_connection), getActivity());
        } else {
            deleteTask = new MakeDeleteTask(mCredential, fileResourceId);
            deleteTask.execute();
        }
    }

    @AfterPermissionGranted(Constants.REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccountForDeletion(String fileResourceId) {
        if (EasyPermissions.hasPermissions(
                this.getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = this.getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                deleteFilesThroughApi(fileResourceId);
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        Constants.REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this.getActivity(),
                    getResources().getString(R.string.google_account_needed),
                    Constants.REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }


    private void getFilesFromApi() {
        if (!GooglePlayUtils.isGooglePlayServicesAvailable(this.getActivity())) {
            GooglePlayUtils.acquireGooglePlayServices(this.getActivity());
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!GooglePlayUtils.isDeviceOnline(this.getActivity())) {
            AndroidUtils.showMessage(getResources().getString(R.string.no_network_connection), getActivity());
        } else {
            new MakeRequestTask(mCredential, folderId).execute();
        }
    }


    @AfterPermissionGranted(Constants.REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this.getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = this.getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getFilesFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        Constants.REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this.getActivity(),
                    getResources().getString(R.string.google_account_needed),
                    Constants.REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {/* Do nothing.*/}

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this.getActivity(), ConnectionResult.SERVICE_INVALID);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this.getActivity(), 0).show();
        }

    }

    private void populateUI(String folderId) {
        requestTask = new MakeRequestTask(mCredential, folderId);
        requestTask.execute();
    }

    private void fillAdapter(final List<DriveDocument> documents) {
        ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter = new ParallaxRecyclerAdapter<DriveDocument>(documents) {
            @Override
            public void onBindViewHolderImpl(RecyclerView.ViewHolder viewHolder, ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter, int i) {
                DriveDocument driveDoc = parallaxRecyclerAdapter.getData().get(i);
                DriveDocumentHolder documentHolder = (DriveDocumentHolder) viewHolder;
                documentHolder.fileResourceId = driveDoc.getResourceId();
                documentHolder.documentTitle.setText(driveDoc.getTitle());
                documentHolder.documentDescription.setText(driveDoc.getDescription());
                documentHolder.documentExtension.setText(driveDoc.getExtension());
                documentHolder.filePhoto.setImageDrawable(ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.xml_file));
                documentHolder.fileLink = driveDoc.getLink();
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolderImpl(ViewGroup viewGroup, final ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter, int i) {
                return new DriveDocumentHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.drivedocument_list_item_card, viewGroup, false));
            }

            @Override
            public int getItemCountImpl(ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter) {
                return documents.size();
            }
        };

        parallaxRecyclerAdapter.setParallaxHeader(getActivity().getLayoutInflater().inflate(R.layout.drivedocument_list_header_layout, rv, false), rv);

        rv.setAdapter(parallaxRecyclerAdapter);

        //On click on recycler view item
        parallaxRecyclerAdapter.setOnClickEvent(new ParallaxRecyclerAdapter.OnClickEvent() {
            @Override
            public void onClick(View view, int i) {
                DriveDocument document = documents.get(i);
                POISListFragment poisListFragment = POISListFragment.newInstance(document);
                fragmentStackManager.loadFragment(poisListFragment, R.id.main_frame);
            }
        });
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {/* Do nothing.*/}

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {/* Do nothing.*/}

    @Override
    public void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Constants.REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != Constants.RESULT_OK) {
                    AndroidUtils.showMessage(getResources().getString(R.string.play_services_needed)
                            , getActivity());
                } else {
                    getFilesFromApi();
                }
                break;
            case Constants.REQUEST_ACCOUNT_PICKER:
                if (resultCode == Constants.RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                this.getActivity().getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(Constants.PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getFilesFromApi();
                    }
                }
                break;
            case Constants.REQUEST_AUTHORIZATION:
                if (resultCode == Constants.RESULT_OK) {
                    getFilesFromApi();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    private class DriveDocumentHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        TextView documentTitle;
        TextView documentDescription;
        TextView documentExtension;
        String fileLink;
        ImageView filePhoto;

        String fileResourceId = "";

        public DriveDocumentHolder(View itemView) {
            super(itemView);
            documentTitle = (TextView) itemView.findViewById(R.id.document_title);
            documentDescription = (TextView) itemView.findViewById(R.id.document_description);
            documentExtension = (TextView) itemView.findViewById(R.id.document_extension);
            filePhoto = (ImageView) itemView.findViewById(R.id.file_photo);
            itemView.setOnCreateContextMenuListener(this);
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
                            deleteFilesThroughApi(fileResourceId);
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


            MenuItem shareitem = menu.add(0, v.getId(), 0, R.string.context_menu_share);
            shareitem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    BeaconConfigFragment beaconConfigFragment = BeaconConfigFragment.newInstance(fileLink);
                    fragmentStackManager.loadFragment(beaconConfigFragment, R.id.main_frame);

                    return true;
                }
            });


            MenuItem editItem = menu.add(0, v.getId(), 1, R.string.context_menu_edit);
            editItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    RenameDocumentFragment renameDocumentFragment = RenameDocumentFragment.newInstance(fileResourceId, documentTitle.getText().toString(), documentDescription.getText().toString());
                    fragmentStackManager.loadFragment(renameDocumentFragment, R.id.main_frame);
                    return true;
                }
            });
        }
    }


    private class MakeRequestTask extends AsyncTask<Void, Void, List<DriveDocument>> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String folderId = "";
        private ProgressDialog dialog;

        public MakeRequestTask(GoogleAccountCredential credential, String folderId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            this.folderId = folderId;
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
                        requestTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }


        @Override
        protected List<DriveDocument> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        private List<DriveDocument> getDataFromApi() throws IOException {
            List<DriveDocument> documentsList = new ArrayList<>();

            FileList result = mService.files().list().setQ("\'" + this.folderId + "\' in parents").setFields("files(description,id,name,webViewLink)").execute();

            List<File> files = result.getFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getTrashed() == null || !file.getTrashed()) {
                        DriveDocument document = new DriveDocument();
                        document.setTitle(file.getName());
                        document.setExtension(file.getFileExtension());
                        document.setResourceId(file.getId());
                        document.setDescription(file.getDescription());
                        document.setLink(file.getWebViewLink());
                        documentsList.add(document);
                    }
                }
            }
            return documentsList;
        }

        @Override
        protected void onPostExecute(List<DriveDocument> output) {
            super.onPostExecute(output);
            if (output != null)
                fillAdapter(output);
            if (dialog != null && dialog.isShowing())
                dialog.hide();
            refreshLayout.setRefreshing(false);
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(),
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
                } else {
                    AndroidUtils.showMessage((getResources().getString(R.string.following_error) + "\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
        }
    }

    private class MakeDeleteTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String resourceId = "";

        private ProgressDialog dialog;

        public MakeDeleteTask(GoogleAccountCredential credential, String resourceId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            this.resourceId = resourceId;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(getContext());
                dialog.setMessage(getActivity().getResources().getString(R.string.deleting));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        deleteTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                deleteFile();
            } catch (Exception e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private void deleteFile() throws IOException {

            mService.files().delete(this.resourceId).execute();

        }

        @Override
        protected void onPostExecute(Void aVoid) {
            requestTask = new MakeRequestTask(mCredential, folderId);
            requestTask.execute();

            if (dialog != null && dialog.isShowing())
                dialog.hide();
            refreshLayout.setRefreshing(false);
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(), ((GooglePlayServicesAvailabilityIOException) mLastError)
                            .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
                } else {
                    AndroidUtils.showMessage((getResources().getString(R.string.following_error) + "\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
        }
    }


    private class CreationTask extends AsyncTask<Void, Void, DriveId> {
        private Exception mLastError = null;
        private String folderName = "";
        private DriveId newDriveFolderId;
        private boolean isCompleted = false;

        private ProgressDialog dialog;

        public CreationTask(String folderName) {

            this.folderName = folderName;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(getContext());
                dialog.setMessage(getActivity().getResources().getString(R.string.creating_folder));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        creationTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }

        @Override
        protected DriveId doInBackground(Void... params) {
            try {
                return createFolder();
            } catch (Exception e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private DriveId createFolder() throws IOException {

            //Folder not found; creating it.
            MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle(folderName).build();

            Drive.DriveApi.getRootFolder(mGoogleApiClient)
                    .createFolder(mGoogleApiClient, changeSet)
                    .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                        @Override
                        public void onResult(DriveFolder.DriveFolderResult result) {
                            if (result.getStatus().isSuccess()) {

                                newDriveFolderId = result.getDriveFolder().getDriveId();
                                isCompleted = true;
                            }
                        }
                    });
            while (!isCompleted) {/*wait for complete*/}

            isCompleted = false;
            Drive.DriveApi.requestSync(mGoogleApiClient).setResultCallback(new ResultCallback<com.google.android.gms.common.api.Status>() {
                @Override
                public void onResult(@NonNull com.google.android.gms.common.api.Status status) {
                    if (status.isSuccess()) {
                        isCompleted = true;
                    }
                }
            });

            while (!isCompleted) {/*wait for complete*/}
            mGoogleApiClient.reconnect();
            return newDriveFolderId;
        }

        @Override
        protected void onPostExecute(DriveId newFolder) {

            if (dialog != null && dialog.isShowing())
                dialog.hide();
            refreshLayout.setRefreshing(false);
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(), ((GooglePlayServicesAvailabilityIOException) mLastError)
                            .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
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
