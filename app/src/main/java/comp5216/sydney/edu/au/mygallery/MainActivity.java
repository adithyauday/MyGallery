/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comp5216.sydney.edu.au.mygallery;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main activity which stores the GridView of Images clicked
 */
public class MainActivity extends AppCompatActivity {

    private GridView grdImages;
    private Button btnCamera;
    private Button btnDelete;
    private Button btnSetting;

    private ImageAdapter imageAdapter;
    private String[] arrPath;
    private boolean[] thumbnailsselection;
    private int ids[];
    private String[] filesPaths;
    private File[] files;
    private int[] selectedPositions;
    // Firebase instance variables
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private String mUsername;
    private String mPhotoUrl;
    private GoogleApiClient mGoogleApiClient;
    public static final String ANONYMOUS = "anonymous";
    private StorageReference mStorageRef;
    FirebaseFirestore mFireStore;
    List<ViewHolder> itemHolder = new ArrayList<ViewHolder>();
    List<Map<String, Object>> dataSet = new ArrayList<>();

    /**
     * Overrides methods
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set default username is anonymous.
        mUsername = ANONYMOUS;

        // Initialize Firebase Auth
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();
        if (mFirebaseUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        } else  {
            mUsername = mFirebaseUser.getDisplayName();
            if (mFirebaseUser.getPhotoUrl() != null) {
                mPhotoUrl = mFirebaseUser.getPhotoUrl().toString();
            }
        }
        mFireStore = FirebaseFirestore.getInstance();
        mStorageRef = FirebaseStorage.getInstance().getReference("uploads/" + mFirebaseAuth.getCurrentUser().getUid());
        // Now we get the references of these images

        setContentView(R.layout.activity_main);
        grdImages= (GridView) findViewById(R.id.grdImages);
        btnCamera= (Button) findViewById(R.id.openCamera);
        btnDelete= (Button) findViewById(R.id.btnDelete);
        btnSetting = (Button) findViewById(R.id.btnSetting);

        // get all files from local storage
        final File mFile = this.getExternalFilesDir(null);
        files = mFile.listFiles();
        filesPaths = new String[files.length];
        for (int i = 0; i < files.length; i++) {
                filesPaths[i] = files[i].getAbsolutePath();
        }

        // get all the photos from Firebase Storage and save in local storage
        mStorageRef.listAll().addOnCompleteListener (new OnCompleteListener<ListResult>() {
            @Override
            public void onComplete(@NonNull Task<ListResult> task) {
                if (task.isSuccessful()) {
                    ListResult imageList = task.getResult();
                    if(imageList.getItems().size()> files.length){
                        for(StorageReference file : imageList.getItems()){
                            File tempFile = new File(getExternalFilesDir(null),file.getName());
                            file.getFile(tempFile);
                        }
                    }
                }
                else {
                    Log.w(null, "Error getting documents.", task.getException());
                }
            }
        });

        //Initialize the gridView
        imageAdapter = new ImageAdapter(filesPaths);
        grdImages.setAdapter(imageAdapter);
        grdImages.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
        grdImages.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                         @Override
                                         public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                             if(i==0){
                                                 Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                                                 intent.putExtra("currentMode","item");
                                                 startActivity(intent);
                                             }
                                             else {
                                                 Intent intent = new Intent(MainActivity.this, ViewAndEditImageActivity.class);
                                                 intent.putExtra("position", i);
                                                 startActivity(intent);
                                             }
                                         }
                                     });

        grdImages.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            // Handle long press
            @Override
            public boolean onCreateActionMode(final android.view.ActionMode actionMode, final Menu menu) {
                actionMode.setTitle("Select Items");
                actionMode.setSubtitle("One item selected");
                findViewById(R.id.openCamera).setVisibility(View.GONE);
                findViewById(R.id.btnDelete).setVisibility(View.VISIBLE);
                // Handle deletion of the item from local storage and Firebase storage
                btnDelete.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        SparseBooleanArray selectedItems = grdImages.getCheckedItemPositions();
                        if (selectedItems != null) {
                            for (int i=0; i<selectedItems.size(); i++) {
                                if (selectedItems.valueAt(i)) {
                                    File mSelectedFile = new File(filesPaths[selectedItems.keyAt(i)]);
                                    if(mSelectedFile.exists()){
                                        mSelectedFile.delete();
                                        deleteFireBase(mSelectedFile);
                                    }
                                }
                            }
                        }

                        File[] files = getExternalFilesDir(null).listFiles();
                        filesPaths = new String[files.length];
                        for (int i = 0; i < files.length; i++) {
                            filesPaths[i] = files[i].getAbsolutePath();
                        }
                        grdImages.setAdapter(new ImageAdapter(filesPaths));
                        mFireStore.collection("users").document(mFirebaseAuth.getCurrentUser().getUid()).collection("product")
                                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                if(task.isSuccessful()){
                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                        Log.d(null, document.getId() + " => " + document.getData());
                                        Map<String, Object> data = document.getData();
                                        if(data.get("imageFilePath") != null) {
                                            for( ViewHolder item: itemHolder){
                                                if(item.imageName.equals(document.getId())){
                                                    item.name.setText(data.get("name").toString());
                                                    item.store.setText(data.get("store").toString());
                                                    item.price.setText(data.get("price").toString());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        });
                        actionMode.finish();
                    }
                });

                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem menuItem) {
                int selectCount = grdImages.getCheckedItemCount();
                switch (selectCount) {
                    case 1:
                        actionMode.setSubtitle("One item selected");
                        break;
                    default:
                        actionMode.setSubtitle("" + selectCount + " items selected");
                        break;
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode actionMode) {
                findViewById(R.id.openCamera).setVisibility(View.VISIBLE);
                findViewById(R.id.btnDelete).setVisibility(View.GONE);
            }

            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode actionMode, int i, long l, boolean b) {
                int selectCount = grdImages.getCheckedItemCount();
                switch (selectCount) {
                    case 1:
                        actionMode.setSubtitle("One item selected");
                        break;
                    default:
                        actionMode.setSubtitle("" + selectCount + " items selected");
                        break;
                }
            }

        });

        // Handle the button to open camera
        btnCamera.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("currentMode","item");
                startActivity(intent);
            }
        });
        // Handle the button that opens settings
        btnSetting.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intent);
            }
        });
        // Set the holder data of the various grids
        mFireStore.collection("users").document(mFirebaseAuth.getCurrentUser().getUid()).collection("product")
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Log.d(null, document.getId() + " => " + document.getData());
                        Map<String, Object> data = document.getData();
                        if(data.get("imageFilePath") != null) {
                            for( ViewHolder item: itemHolder){
                                if(item.imageName.equals(document.getId())){
                                    item.name.setText(data.get("name").toString());
                                    item.store.setText(data.get("store").toString());
                                    item.price.setText(data.get("price").toString());
                                }
                            }
                        }
                    }
                }
            }
        });
        setViewHolder();
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();

    }

    /**
     * This method used to set bitmap.
     * @param iv represented ImageView
     * @param id represented id
     */

    private void setBitmap(final ImageView iv, final int id) {

        new AsyncTask<Void, Void, Bitmap>() {

            @Override
            protected Bitmap doInBackground(Void... params) {
                return MediaStore.Images.Thumbnails.getThumbnail(getApplicationContext().getContentResolver(), id, MediaStore.Images.Thumbnails.MICRO_KIND, null);
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                super.onPostExecute(result);
                iv.setImageBitmap(result);
            }
        }.execute();
    }


    /**
     * List adapter
     * @author tasol
     */

    public class ImageAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private String[] filesPaths;

        public ImageAdapter(String[] filesPaths) {
            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.filesPaths = filesPaths;
        }

        public int getCount() {
            return filesPaths.length;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }


        public View getView(final int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.custom_gallery_item, null);
                holder.imgThumb = (ImageView) convertView.findViewById(R.id.imgThumb);
                holder.name = (TextView)convertView.findViewById(R.id.itemNameValue);
                holder.store = (TextView)convertView.findViewById(R.id.storeNameValue);
                holder.price = (TextView)convertView.findViewById(R.id.priceValue);
                holder.imageName = files[position].getName();

                Bitmap bmp = BitmapFactory.decodeFile(filesPaths[position]);
                if(!files[position].getName().equals("BaiduMapSDKNew")){
                holder.imgThumb.setImageBitmap(bmp);}
                else {
                    holder.imgThumb.setImageResource(R.mipmap.addphoto);
                }
                itemHolder.add(holder);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            try {
                setBitmap(holder.imgThumb, ids[position]);
            } catch (Throwable e) {
            }
            holder.id = position;
            return convertView;
        }
    }

    private void setViewHolder(){
        for (int i=0; i<itemHolder.size(); i++){
            itemHolder.get(i).name.setText(dataSet.get(i).get("name").toString());
            itemHolder.get(i).store.setText(dataSet.get(i).get("store").toString());
            itemHolder.get(i).price.setText(dataSet.get(i).get("price").toString());
        }
    }
    /**
     * Inner class
     * @author tasol
     */
    class ViewHolder {
        ImageView imgThumb;
        int id;
        TextView name;
        TextView price;
        TextView store;
        String imageName;
    }

    private void deleteFireBase(File deleteFile){
        mStorageRef.child(deleteFile.getName()).delete();
        mFireStore.collection("users").document(mFirebaseAuth.getCurrentUser().getUid()).collection("product").document(deleteFile.getName()).delete();
    }

}