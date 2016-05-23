package gsoc.google.com.byop.utils;

import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filter;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.util.ArrayList;

import gsoc.google.com.byop.ui.documentsList.FolderListFragment;

/**
 * Created by lgwork on 23/05/16.
 */
public class DriveUtils {

    public static DriveId getFolder(final FolderListFragment folderListFragment, DriveId parentId, final String titl, final GoogleApiClient googleApiClient) {
        final DriveId[] dId = {null};
        if (parentId != null && titl != null) try {
            ArrayList<Filter> fltrs = new ArrayList<>();
           // fltrs.add(Filters.in(SearchableField.PARENTS, parentId));
            fltrs.add(Filters.eq(SearchableField.TITLE, titl));
            fltrs.add(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"));
            Query qry = new Query.Builder().addFilter(Filters.and(fltrs)).build();

            MetadataBuffer mdb = null;
            Drive.DriveApi.query(googleApiClient, qry).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {

                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if(result.getStatus().isSuccess()){
                        boolean isFound = false;
                        for(Metadata m : result.getMetadataBuffer()) {
                            if(!isFound) {
                                if (!m.isTrashed() && m.getTitle().equals(titl)) {
                                     //Folder exists"
                                    isFound = true;
                                    dId[0] = m.getDriveId();
                                }
                            }
                        }

                        if(!isFound) {
                          //Folder not found; creating it.
                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle(titl).build();

                            Drive.DriveApi.getRootFolder(googleApiClient)
                                    .createFolder(googleApiClient, changeSet)
                                    .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                                        @Override
                                        public void onResult(DriveFolder.DriveFolderResult result) {
                                            if (!result.getStatus().isSuccess()) {
                                                Toast.makeText(folderListFragment.getActivity(), "FAIL", Toast.LENGTH_LONG).show();
                                            } else {
                                                dId[0] = result.getDriveFolder().getDriveId();
                                                Toast.makeText(folderListFragment.getActivity(),"Created a folder: " + result.getDriveFolder().getDriveId(),Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    });
                        }
                    }

                }

            });
        } catch (Exception e) { e.printStackTrace(); }
        return dId[0];
    }
}
