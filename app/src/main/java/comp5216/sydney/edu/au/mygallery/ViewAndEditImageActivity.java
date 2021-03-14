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
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Activity that holds the selected or clicked ImageView to view or edit it.
 */
public class ViewAndEditImageActivity extends AppCompatActivity {

    private ImageView image;
    private String[] filesPaths;
    private Bitmap bmp;
    File imageFile;
    Integer position;
    File[] files;
    private String resultText;
    private Uri fileUri;
    FirebaseFirestore db;
    FirebaseAuth mFirebaseAuth;
    private StorageReference mStorageRef;
    private StorageTask mUploadTask;
    public LocationClient mLocationClient = null;
    private MyLocationListener myListener = new MyLocationListener();

    private List<StoreBean> storeBeans;
    private StoreBean storeBean;
    List<String> stores;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_and_edit_image);
        image = (ImageView) findViewById(R.id.imageView);
        db = FirebaseFirestore.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        //get position from calling activity
        Intent intent = getIntent();
        position = intent.getIntExtra("position",0);
        //get all the file images stored
        File mFile = this.getExternalFilesDir(null);
        files = mFile.listFiles();
        filesPaths = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            filesPaths[i] = files[i].getAbsolutePath();
        }
        try{
            fileUri = Uri.fromFile(files[files.length - 1]);
        }catch (Exception e){
            e.getMessage();
        }
        // Reference to the Firebase Storage
        mStorageRef = FirebaseStorage.getInstance().getReference("uploads");
        if(position==-1) {
            try {
                ((Button)findViewById(R.id.btnSaveStore)).setVisibility(View.GONE);
                detectLabel();
                // get the bmp of the image to be viewed
                bmp = BitmapFactory.decodeFile(filesPaths[files.length - 2]);
                imageFile = files[files.length - 2];
            }
            catch (Exception e){
                e.getMessage();
            }
        }
        else{
            ((Button)findViewById(R.id.btnSaveStore)).setVisibility(View.VISIBLE);
            getData();
            bmp = BitmapFactory.decodeFile(filesPaths[position]);
            imageFile = files[position];
        }
        // Set the image to Image view
        image.setImageBitmap(bmp);
        initFirestore();
        storeBeans = new ArrayList<>();
        storeBean = new StoreBean();
        mLocationClient = new LocationClient(getApplicationContext());
        mLocationClient.registerLocationListener(myListener);
        initLocation();
        mLocationClient.start();

        // get the stores in the database to help auto fill TextView
        stores = new ArrayList<>();
        db.collection("users").document("storeData").collection("data").get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                        stores.add(document.getId());
                                }
                            ArrayAdapter<String> adapter = new ArrayAdapter<String>(ViewAndEditImageActivity.this, android.R.layout.select_dialog_singlechoice, stores);
                            AutoCompleteTextView acTextView = (AutoCompleteTextView) findViewById(R.id.editViewStore);
                            //Set the number of characters the user must type before the drop down list is shown
                            acTextView.setThreshold(1);
                            //Set the adapter
                            acTextView.setAdapter(adapter);
                        } else {
                            Log.w(null, "Error getting documents.", task.getException());
                        }
                    }
                });
    }

    @Override
    public void onBackPressed() {
        ClosePreviewActivity(null);
    }

    /**
     * get all the product details from the store
     */
    public void getData(){
        db.collection("users").document(mFirebaseAuth.getCurrentUser().getUid()).collection("product")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(null, document.getId() + " => " + document.getData());
                                Map<String, Object> data = document.getData();
                                if(data.get("imageFilePath") != null) {
                                    if (imageFile.getAbsolutePath().equals(data.get("imageFilePath"))) {
                                        ((EditText) findViewById(R.id.editViewName)).setText(data.get("name").toString());
                                        ((EditText) findViewById(R.id.editViewPrice)).setText(data.get("price").toString());
                                        ((EditText) findViewById(R.id.editViewSize)).setText(data.get("size").toString());
                                        ((EditText) findViewById(R.id.editViewStore)).setText(data.get("store").toString());
                                    }
                                }
                            }
                        } else {
                            Log.w(null, "Error getting documents.", task.getException());
                        }
                    }
                });
    }

    /**
     * function to read the ocr from label picture
     */
    public void detectLabel(){
        FirebaseVisionImage image;
        try {
            image = FirebaseVisionImage.fromFilePath(this, fileUri);
            FirebaseVisionTextRecognizer textRecognizer = FirebaseVision.getInstance().getOnDeviceTextRecognizer();

            textRecognizer.processImage(image)
                    .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                        @Override
                        public void onSuccess(FirebaseVisionText result) {
                            Log.d("Label detected ", "success");
                            onSuccessfulDetection(result);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.getStackTraceString(e);
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * when the ocr is detected
     * @param result
     */
    public void onSuccessfulDetection(FirebaseVisionText result){
        resultText = result.getText();
        Log.d("Finished! Results: ", resultText);
        fillProductDetails(resultText);
    }

    /**
     * filter details read by ocr to categories and display them
     * @param extras
     */
    private void fillProductDetails(String extras){
        //process label text
        String [] lines = extras.split("\\n");
        String itemDescription = "";
        for (String line : lines) {
            try {
                if (line.contains("$")) {
                    EditText editItem = (EditText) findViewById(R.id.editViewPrice);
                    editItem.setText(line);
                } else if (line.matches("[[S|M|L|XL|XS|XXS|XXL|XXXL]]")) {
                    EditText editItem = (EditText) findViewById(R.id.editViewSize);
                    editItem.setText(line);
                } //words
                else if (line.matches("[^\\d\\W]{2,}")) {
                    itemDescription += line;
                }
                //integer size, according to australian sizing standards
                else if (Integer.parseInt(line) > 2 && Integer.parseInt(line) < 40) {
                    EditText editItem = (EditText) findViewById(R.id.editViewSize);
                    editItem.setText(line);
                } else if (line.toLowerCase().contains("size")) {
                    EditText editItem = (EditText) findViewById(R.id.editViewSize);
                    String[] size = line.split("' ':");
                    for (String obj : size) {
                        try {
                            if (obj.equalsIgnoreCase("size")) {
                                continue;
                            } else if (Integer.parseInt(obj) > 2 && Integer.parseInt(obj) < 40) {
                                editItem.setText(obj);
                            } else if (obj.matches("[[S|M|L|XL|XS|XXS|XXL|XXXL]]")) {
                                editItem.setText(obj);
                            }
                        } catch (NumberFormatException e) {
                            continue;
                        }
                    }
                }
                //set item description to all other strings that are words
                EditText editItem = (EditText) findViewById(R.id.editViewName);
                editItem.setText(itemDescription);
            } catch (NumberFormatException e) {
                continue;
            }
        }
    }
    /**
     * Function to Close current activity and return to Main activity
     */
    public void ClosePreviewActivity(View v){
        if(position == -1) {
            imageFile.delete();
            files[filesPaths.length-1].delete();
        }
        Intent i=new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    /**
     * Function to Save the image with its details.
     */
    public void SaveImage(View v){
        //delete label
        if(position == -1) {
            files[filesPaths.length - 1].delete();
        }
        ProductDetails item = new ProductDetails();
        item.name = ((EditText)findViewById(R.id.editViewName)).getText().toString();
        item.price = ((EditText)findViewById(R.id.editViewPrice)).getText().toString();
        item.size = ((EditText)findViewById(R.id.editViewSize)).getText().toString();
        item.storeName = ((EditText)findViewById(R.id.editViewStore)).getText().toString();
        item.imageFilePath = imageFile.getAbsolutePath();

        savetoFireStore(item);
        uploadFile();

        SharedPreferences sp = this.getSharedPreferences("SP_StoreBean_List", Activity.MODE_PRIVATE);
        String peopleListJson = sp.getString("KEY_StoreBean_LIST_DATA","");
        if(peopleListJson!="")  //防空判断
        {
            Gson gson = new Gson();
            storeBeans = gson.fromJson(peopleListJson, new TypeToken<List<StoreBean>>() {}.getType());
        }
        storeBean.setStoreName(item.name);
        storeBean.setLatitude(latitude);
        storeBean.setLongitude(longitude);
        storeBeans.add(storeBean);
        Gson gson = new Gson();
        String jsonStr=gson.toJson(storeBeans);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("KEY_StoreBean_LIST_DATA", jsonStr) ;
        editor.commit() ;

        Intent i=new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    /**
     * Function to get the link to the online store of the product
     * @param view
     */
    public void LinkOnlineStore(View view){
        String store = ((AutoCompleteTextView)findViewById(R.id.editViewStore)).getText().toString();
        if(store.equals("")){
            Toast.makeText(this,"Add Store name", Toast.LENGTH_SHORT).show();
        }
        else{
            db.collection("users").document("storeData").collection("data").document(store).get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if(task.isSuccessful()){
                                DocumentSnapshot document = task.getResult();
                                Map<String, Object> data = document.getData();
                                if(data != null) {
                                    String url = data.get("link").toString();
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    startActivity(browserIntent);
                                }
                                else{
                                    Toast.makeText(ViewAndEditImageActivity.this,"Store Link not Available", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });
        }
    }

    FirebaseFirestore mFireStore;
    private void initFirestore() {mFireStore = FirebaseFirestore.getInstance();}

    /**
     * function to save the product details to the fireStore database
     * @param item
     */
    private void savetoFireStore(ProductDetails item){
        Map<String, Object> data = new HashMap<>();
        data.put("store",item.storeName);
        data.put("imageFilePath", item.imageFilePath);
        data.put("size",item.size);
        data.put("price",item.price);
        data.put("name",item.name);

        // Push the data to Firestore
        mFireStore.collection("users").document(mFirebaseAuth.getCurrentUser().getUid()).collection("product").document(imageFile.getName()).set(data)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(null, "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(null, "Error writing document", e);
                    }
                });
    }

    /**
     * Upload file to FireBase storage
     */
    private void uploadFile() {
        Uri mImageUri = Uri.fromFile(imageFile);
        if (mImageUri != null) {
            StorageReference fileReference = mStorageRef.child(mFirebaseAuth.getCurrentUser().getUid() + "/" + imageFile.getName());
            // Store Image in FireBase Storage
            mUploadTask = fileReference.putFile(mImageUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                }
                            }, 500);

                            Toast.makeText(ViewAndEditImageActivity.this, "Upload successful", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(ViewAndEditImageActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                        }
                    });
            // Get the product details from the view
            ProductDetails item = new ProductDetails();
            item.name = ((EditText)findViewById(R.id.editViewName)).getText().toString();
            item.price = ((EditText)findViewById(R.id.editViewPrice)).getText().toString();
            item.size = ((EditText)findViewById(R.id.editViewSize)).getText().toString();
            item.storeName = ((EditText)findViewById(R.id.editViewStore)).getText().toString();
            item.imageFilePath = imageFile.getAbsolutePath();

            // Create a new product with the details
            Map<String, Object> user = new HashMap<>();
            user.put("name", item.name);
            user.put("price", item.price);
            user.put("size", item.size);
            user.put("storeName", item.storeName);
        } else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void initLocation() {
        LocationClientOption option = new LocationClientOption();

        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setCoorType("bd09ll");
        option.setScanSpan(1000);
        option.setOpenGps(true);
        option.setLocationNotify(true);
        option.setIgnoreKillProcess(false);
//optional，location is a service inside the SDK
//setIgnoreKillProcess(true)
        option.SetIgnoreCacheException(false);
//optional，set whether to collect the Crash details，default false

        option.setWifiCacheTimeOut(5*60*1000);
        option.setEnableSimulateGps(false);

        mLocationClient.setLocOption(option);
    }

    double latitude;
    double longitude;
    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location){

            //BDLocation
            latitude = location.getLatitude();    //Get the latitude
            longitude = location.getLongitude();    //Get the longitude
            float radius = location.getRadius();    //Get accuracy of location 0.0f
            String coorType = location.getCoorType();
            int errorCode = location.getLocType();
            mLocationClient.stop();
        }
    }
}
