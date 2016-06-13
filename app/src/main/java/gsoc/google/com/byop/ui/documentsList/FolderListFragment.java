package gsoc.google.com.byop.ui.documentsList;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
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
import gsoc.google.com.byop.utils.BluetoothUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;
import gsoc.google.com.byop.utils.PW.BeaconConfigFragment;

/**
 * Created by lgwork on 23/05/16.
 */
public class FolderListFragment extends Fragment {


    protected FragmentStackManager fragmentStackManager;
    GoogleAccountCredential mCredential;
    private RecyclerView rv = null;
    private SwipeRefreshLayout refreshLayout;
    private FloatingActionButton fab;

    private MakeRequestTask requestTask;
    private MakeDeleteTask deleteTask;
    private CreationTask creationTask;
    private CheckFolderTask checkFolderTask;

    private String byopFolderId = "";
    private String folderName;

    private String accountEmail;

    public static FolderListFragment newInstance() {
        FolderListFragment newfolderListFragment = new FolderListFragment();
        return newfolderListFragment;
    }

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
                        populateUI(byopFolderId);
                    }
                }
        );

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CreateDocumentFragment newDocumentFragment = CreateDocumentFragment.newInstance(byopFolderId);
                fragmentStackManager.loadFragment(newDocumentFragment, R.id.main_frame);
            }
        });

        folderName = getResources().getString(R.string.folderName);


        if (byopFolderId == null || byopFolderId.equals("")) {
            checkFolderTask = new CheckFolderTask(mCredential, folderName);
            checkFolderTask.execute();
        } else if (requestTask == null) {
            requestTask = new MakeRequestTask(mCredential, byopFolderId);
            requestTask.execute();
        } else {
            requestTask = new MakeRequestTask(mCredential, byopFolderId);
            requestTask.execute();
        }

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


        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());


        SharedPreferences settings = this.getActivity().getPreferences(Context.MODE_PRIVATE);

        accountEmail = settings.getString(Constants.PREF_ACCOUNT_EMAIL, "");


        mCredential.setSelectedAccountName(accountEmail);


        return rootView;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConnectionResult.SERVICE_INVALID) {
            fragmentStackManager.popBackStatFragment();
            FolderListFragment folderListFragment = FolderListFragment.newInstance();
            fragmentStackManager.loadFragment(folderListFragment, R.id.main_frame);
        } else if (requestCode == Constants.REQUEST_AUTHORIZATION) {
            fragmentStackManager.popBackStatFragment();
            FolderListFragment folderListFragment = FolderListFragment.newInstance();
            fragmentStackManager.loadFragment(folderListFragment, R.id.main_frame);
        }
    }


    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (requestTask != null) {
            requestTask.cancel(true);
        }
        if (checkFolderTask != null) {
            checkFolderTask.cancel(true);
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


    private class DriveDocumentHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        TextView documentTitle;
        TextView documentDescription;
        TextView documentExtension;
        ImageView filePhoto;

        String fileLink;
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
                            //deleteFilesThroughApi(fileResourceId);
                            deleteTask = new MakeDeleteTask(mCredential, fileResourceId);
                            deleteTask.execute();
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

                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    BluetoothUtils.ensureBluetoothIsEnabled(getActivity(), bluetoothAdapter);

                    BeaconConfigFragment beaconConfigFragment = BeaconConfigFragment.newInstance(fileLink);
                    fragmentStackManager.loadFragment(beaconConfigFragment, R.id.main_frame);

                    return true;
                }
            });


            MenuItem editItem = menu.add(0, v.getId(), 1, R.string.context_menu_edit);
            editItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    RenameDocumentFragment renameDocumentFragment = RenameDocumentFragment.newInstance(fileResourceId, documentTitle.getText().toString(),
                            documentDescription.getText().toString());
                    fragmentStackManager.loadFragment(renameDocumentFragment, R.id.main_frame);
                    return true;
                }
            });
        }
    }

    private class CheckFolderTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String folderName = "";
        private ProgressDialog dialog;

        public CheckFolderTask(GoogleAccountCredential credential, String folderName) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            this.folderName = folderName;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(getContext());
                dialog.setMessage("Checking folder...");
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        checkFolderTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }


        @Override
        protected Void doInBackground(Void... params) {
            try {
                checkFolderFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private void checkFolderFromApi() throws IOException {
            FileList result = mService.files().list().setQ("mimeType = 'application/vnd.google-apps.folder' and name = '" + folderName + "' and trashed=false").execute();

            List<File> files = result.getFiles();
            if (files != null && files.size() == 1) {
                byopFolderId = files.get(0).getId();
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (dialog != null) {
                dialog.hide();
                dialog.dismiss();
            }
            refreshLayout.setRefreshing(false);

            if (byopFolderId != null && !byopFolderId.equals("")) {
                requestTask = new MakeRequestTask(mCredential, byopFolderId);
                requestTask.execute();
            } else {
                creationTask = new CreationTask(mCredential, this.folderName);
                creationTask.execute();
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
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
                    dialog.dismiss();
                } else {
                    AndroidUtils.showMessage((getResources().getString(R.string.following_error) + "\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                // AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
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
            }
            return null;
        }


        private List<DriveDocument> getDataFromApi() throws IOException {
            List<DriveDocument> documentsList = new ArrayList<>();

            FileList result = mService.files().list().setQ("\'" + this.folderId + "\' in parents and trashed=false").setFields("files(description,id,name,webViewLink)").execute();

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
            if (output != null) {
                fillAdapter(output);
            } else {
                cancel(true);
            }
            if (dialog != null) {
                dialog.hide();
                dialog.dismiss();
            }
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
                    dialog.dismiss();

                } else {
                    AndroidUtils.showMessage((getResources().getString(R.string.following_error) + "\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                // AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
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
            requestTask = new MakeRequestTask(mCredential, byopFolderId);
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
                //  AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
        }
    }

    private class CreationTask extends AsyncTask<Void, Void, Void> {
        private Exception mLastError = null;
        private String folderName = "";
        private com.google.api.services.drive.Drive mService = null;
        private ProgressDialog dialog;

        public CreationTask(GoogleAccountCredential credential, String folderName) {

            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

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
        protected Void doInBackground(Void... params) {
            try {
                createFolder();
            } catch (Exception e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private void createFolder() throws IOException {

            File fileMetadata = new File();
            fileMetadata.setName(this.folderName);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");

            File file = mService.files().create(fileMetadata).execute();

            if (file != null) {
                byopFolderId = file.getId();
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {

            if (dialog != null && dialog.isShowing())
                dialog.hide();
            refreshLayout.setRefreshing(false);

            requestTask = new MakeRequestTask(mCredential, byopFolderId);
            requestTask.execute();
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
                //   AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
        }
    }


}
