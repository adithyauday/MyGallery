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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.sdsmdg.harjot.crollerTest.Croller;

/**
 *  Activity that shows the settings for timer and distance for notifying
 */
public class SettingActivity extends AppCompatActivity implements View.OnClickListener{

    private Croller crollerDistance;
    private Croller crollerTime;
    private ImageView imageViewBack;
    private TextView textViewSave;
    private RelativeLayout relativeLayout;
    private SharedPreferences sp;
    SharedPreferences.Editor editor ;
    private int distance;//meter
    private int time;//mins
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        sp = this.getSharedPreferences("SP_StoreBean_List", Activity.MODE_PRIVATE);
        editor = sp.edit();
        initView();
    }

    /**
     * initialize the settings view
     */
    private void initView() {
        relativeLayout = findViewById(R.id.rlm);
        imageViewBack = findViewById(R.id.imageview_back);
        imageViewBack.setOnClickListener(this);
        textViewSave = findViewById(R.id.textview_save);
        textViewSave.setOnClickListener(this);
        crollerDistance = findViewById(R.id.croller);
        crollerDistance.setProgress((sp.getInt("distance",50)/50));
        crollerDistance.setOnProgressChangedListener(new Croller.onProgressChangedListener() {
            @Override
            public void onProgressChanged(int progress) {
                crollerDistance.setLabel(progress*1+"meters");
                distance = progress*1;
            }
        });

        crollerTime = findViewById(R.id.croller2);
        crollerTime.setProgress((sp.getInt("time",600)/600));
        crollerTime.setOnProgressChangedListener(new Croller.onProgressChangedListener() {
            @Override
            public void onProgressChanged(int progress) {
                if(progress==1){
                    crollerTime.setLabel(1+"s");
                    time = progress*1;
                }else if(progress==2){
                    crollerTime.setLabel("1hour");
                    time = 60*60;
                }else if(progress==3){
                    crollerTime.setLabel("3hours");
                    time = 3*60*60;
                }else if(progress==4){
                    crollerTime.setLabel("6hours");
                    time = 6*60*60;
                }else if(progress==5){
                    crollerTime.setLabel("1day");
                    time = 24*60*60;
                }else if(progress==6){
                    crollerTime.setLabel("1week");
                    time = 7*24*60*60;
                }
            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.imageview_back:
                SnackbarUtils.closeTaost();
                finish();
                break;
            case R.id.textview_save:
                editor.putInt("distance",distance);
                editor.putInt("time",time);
                Log.i("aaaabbbb","timeqq"+time+"distance"+distance);
                editor.commit();
                SnackbarUtils.showTaost("save successfully",relativeLayout);
                BaseApplication.getApplication().DaoJiShi(time,distance);
                break;
        }
    }
}
