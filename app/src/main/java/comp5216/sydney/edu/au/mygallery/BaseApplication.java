package comp5216.sydney.edu.au.mygallery;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.BDNotifyListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.DistanceUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class BaseApplication extends Application {
    private static BaseApplication myApplication = null;
    public LocationClient mLocationClient = null;
    private MyLocationListener myListeners = new MyLocationListener();
    public BDNotifyListener myListener = new MyNotifyLister();
    private List<StoreBean> storeBeans;
    private SharedPreferences sp;
    @Override
    public void onCreate() {
        super.onCreate();
        SDKInitializer.initialize(getApplicationContext());
        mLocationClient = new LocationClient(this);
        storeBeans = new ArrayList<>();
        //claim the class LocationClient
        mLocationClient.registerLocationListener(myListeners);

        initData();
        mLocationClient.registerNotify(myListener);
        sp = this.getSharedPreferences("SP_StoreBean_List", Activity.MODE_PRIVATE);//create the SP object

        mLocationClient.start();
        DaoJiShi(1,1);
    }
    public static BaseApplication getApplication(){
        if (myApplication == null){
            myApplication = new BaseApplication();
        }
        return myApplication;
    }

    private void initData() {
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setCoorType("bd09ll");
        option.setScanSpan(20000);
        option.setOpenGps(true);
        option.setLocationNotify(false);
        option.setIgnoreKillProcess(false);
        option.SetIgnoreCacheException(false);
        option.setWifiCacheTimeOut(5*60*1000);
        option.setEnableSimulateGps(false);
        mLocationClient.setLocOption(option);
    }

    int times;
    int distances;
    private CountDownTimer countDownTimer;
    private boolean flag = false;//flag of item, false=no notification
    //countdown
    public void DaoJiShi(int time,int distance){
        //clear the countdown
        if(countDownTimer!=null){
            countDownTimer.onFinish();
        }
        if(sp!=null){
            time = sp.getInt("time",1);
            distance = sp.getInt("distance",1);
            Log.i("aaaabbbb","times:"+time+"distances:"+distance);
        }

        distances = distance;
        Log.i("aaaabbbb","time:"+time+"distance:"+distance);
        if(times == time){
            countDownTimer = new CountDownTimer(time*1000,1000) {
                @Override
                public void onTick(long l) {
                }
                //run after the end of count down
                @Override
                public void onFinish() {
                    flag = true;
                }
            };
            countDownTimer.start();
        }

    }

    public class MyNotifyLister extends BDNotifyListener {
        public void onNotify(BDLocation mlocation, float distance){
            Log.i("aaaa","jinlaila");
            //get near to the listening point this
            Toast.makeText(getApplicationContext(), "aaaaaa", Toast.LENGTH_SHORT).show();

        }
    }

    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            //BDLocation
            double latitude = location.getLatitude();    //Get the latitude
            double longitude = location.getLongitude();    //Get the longitude
            float radius = location.getRadius();    //Get the accuracy of location，default0.0f
            String coorType = location.getCoorType();
            //Get the longitude type，LocationClientOption
            int errorCode = location.getLocType();
            //Get the loctype
            Log.i("aaaa","jinlaila+latitude"+latitude);
            String peopleListJson = sp.getString("KEY_StoreBean_LIST_DATA","");  //取出key为"KEY_PEOPLE_DATA"的值，如果值为空，则将第二个参数作为默认值赋值
            if(peopleListJson!=""){  //Avoid void
                Log.i("aaaa","jinlaila"+peopleListJson);
                Gson gson = new Gson();
                storeBeans = gson.fromJson(peopleListJson, new TypeToken<List<StoreBean>>() {}.getType()); //transfer Json string to List
            }
            for(int i = 0;i<storeBeans.size();i++){
                if(DistanceUtil.getDistance(new LatLng(storeBeans.get(i).getLatitude(),storeBeans.get(i).getLongitude()),new LatLng(latitude,longitude))>=distances){
                    Log.i("aaaa","jinlaila" +"beyond the boundary+time"+times);
                    if(flag||times==0){
                        Log.i("aaaabbbb","time"+times);
                        initNotification(storeBeans.get(i).getStoreName());
                    }
                }else{
                    Log.i("aaaa","jinlaila" +"Inside the boundary");
                }
            }

        }
    }

    /**
     *  initNotification
     */

    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;
    private Notification notification;
    private int downloadApkNotifyId;
    private void initNotification(String channelName) {
        notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        downloadApkNotifyId = channelName.hashCode();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(channelName, channelName, NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(mChannel);
        }
        builder = new NotificationCompat.Builder(this, channelName+"asd");
        builder.setContentTitle(this.getResources().getString(R.string.title)) //Set the title of notification
                .setSmallIcon(R.mipmap.ic_launcher)
                .setDefaults(Notification.DEFAULT_LIGHTS) //set the method of notification

                .setPriority(NotificationCompat.PRIORITY_MAX) //Max priority
                .setAutoCancel(true)  //
                //.setOngoing(true)     // can not be deleted
                .setContentText(this.getResources().getString(R.string.content))
                .setChannelId(channelName);
        notification = builder.build();//build the object of notification
        notificationManager.notify(downloadApkNotifyId, notification);
        if(!NotificationUtil.isPermissionOpen(this)){
            NotificationUtil.openPermissionSetting(this);
        }
    }
}
