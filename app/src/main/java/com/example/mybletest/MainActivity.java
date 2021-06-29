package com.example.mybletest;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    /**
     * 整个流程
     * 1.界面初始化
     * 2.BluetoothAdapter
     * 3.判断蓝牙是否支持，打开蓝牙
     * 4.扫描指定蓝牙设备
     * 5.连接蓝牙设备
     * 6.获取信息的方法等
     */

    String TAG = "MainActivity";

    boolean USE_SPECIFIC_UUID = true;//使用特定的UUID
    // 定义需要进行通信的ServiceUUID
    private UUID mServiceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    // 定义需要进行通信的CharacteristicUUID
    private UUID mCharacteristicUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    // 定义需要进行通信的ConfigID
    private UUID mConfigUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // 数组permissions，存储蓝牙连接需要的权限
    private String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION};
    // 未授予的权限存储到mPerrrmissionList
    private List<String> mPermissionList = new ArrayList<>();
    // 权限请求码
    private final int mRequestCode = 200;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private boolean mScanning;//是否正在搜索
    private Handler mHandler;
    private final int SCAN_TIME = 3000;
    private ArrayList<BluetoothDevice> bluetoothDeviceArrayList = new ArrayList<>();
    int selIndex = 0;

    // 是否正在连接
    private boolean mConnectionState = false;
    private BluetoothGatt mBluetoothGatt = null;

    private ArrayList<BluetoothGattCharacteristic> writeCharacteristicArrayList ;
    private ArrayList<BluetoothGattCharacteristic> readCharacteristicArrayList ;
    private ArrayList<BluetoothGattCharacteristic> notifyCharacteristicArrayList ;

    // 按钮
    private Button btn_ble;
    private TextView tv_device_name;
    // 接收数据框
    private EditText edit_receive_data;

    private int show_ble = 0;

    private  String device_name = "";

    ProgressDialog waitDialog;
    ProgressDialog cancelDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initPermission();
        initView();

       /* //首先获取BluetoothManager
        bluetoothManager=(BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        //获取BluetoothAdapter
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }*/
       // 获取BluetoothAdapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler();
        writeCharacteristicArrayList = new ArrayList<>();
        readCharacteristicArrayList = new ArrayList<>();
        notifyCharacteristicArrayList = new ArrayList<>();
    }

    /**
     * 权限判断和申请
     */
    private void initPermission() {
        mPermissionList.clear();//清空没有通过的权限
        //逐个判断你要的权限是否已经通过
        for (int i = 0; i < permissions.length; i++) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(permissions[i])!=PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[i]);//添加还未授予的权限
            }
        }
        //申请权限
        if (mPermissionList.size() > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//有权限没有通过，需要申请
            requestPermissions(permissions, mRequestCode);
        }
    }

    /**
     * 界面初始化
     *    连接蓝牙按钮：R.id.btn_ble
     *    设备名称：R.id.tv_device_name
     *    接受消息框：R.id.edit_receive_data
     *    不用弹出选择框，只展示正在连接
     *    如果找不到设备要提示，设备未开启！！！
     */
    private void initView(){

        show_ble = 0;

        btn_ble = findViewById(R.id.btn_ble);
        btn_ble.setOnClickListener(this);

        tv_device_name = findViewById(R.id.tv_device_name);

        edit_receive_data = findViewById(R.id.edit_receive_data);
        // 内容超过editText宽度设置滑动效果
        edit_receive_data.setMovementMethod(ScrollingMovementMethod.getInstance());

        waitDialog = new ProgressDialog(this);
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
        waitDialog.setCancelable(false);// 设置是否可以通过点击Back键取消
        waitDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的
        waitDialog.setTitle(getString(R.string.list_please_wait));
        waitDialog.setMessage(getString(R.string.list_searching_ble));

        cancelDialog = new ProgressDialog(this);
        cancelDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
        cancelDialog.setCancelable(false);// 设置是否可以通过点击Back键取消
        cancelDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的
        cancelDialog.setTitle(getString(R.string.list_please_wait));
        cancelDialog.setMessage(getString(R.string.list_disconnecting_ble));
    }

    /**
     * 点击事件
     * @param v
     */
    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.btn_ble){
            // 已有连接蓝牙，执行断开操作，反之进行搜索连接操作
            if (mConnectionState){
                cancelDialog.show();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cancelDialog.dismiss();
                    }
                },1000);
                mConnectionState = false;
                btn_ble.setText(R.string.list_connect_ble);
                if(mBluetoothGatt!=null){
                    mBluetoothGatt.disconnect();
                }
            }else {
                if (!checkBleDevice(this)){
                    return;
                }
                // 清空蓝牙设备列表
                bluetoothDeviceArrayList.clear();
                //TODO 搜索连接蓝牙
                scanLeDevice(true);
            }
        }
    }

    /**
     * 搜索蓝牙设备
     * mBluetoothAdapter.startLeScan(mLeScanCallback); 在API21以后被禁用，改为mBLEScanner.startScan(mScanCallback);
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {//true
            waitDialog.setMessage(getString(R.string.list_searching_ble));
            waitDialog.show();
            //标记当前的为扫描状态
            mScanning = true;
            //获取5.0新添的扫描类
            if (mBLEScanner == null){
                //mBLEScanner是5.0新添加的扫描类，通过BluetoothAdapter实例获取。
                mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            }
            //开始扫描
            //mScanCallback是ScanCallback实例。
            mBLEScanner.startScan(mScanCallback);
            //超过SCAN_TIME时间后停止搜索
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //停止扫描设备
                    //标记当前的为未扫描状态
                    mScanning = false;
                    mBLEScanner.stopScan(mScanCallback);
                    waitDialog.dismiss();
                    if (bluetoothDeviceArrayList.size()>0){
                        showScanDeviceList();
                    }
                }
            }, SCAN_TIME);
        } else {//false
            //标记当前的为未扫描状态
            mScanning = false;
            mBLEScanner.stopScan(mScanCallback);
            // TODO 弹出查找失败
            waitDialog.dismiss();
        }
    }
    /**
     * 搜索蓝牙的结果
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        //当一个蓝牙ble广播被发现时回调
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            //扫描类型有开始扫描时传入的ScanSettings相关
            //对扫描到的设备进行操作。如：获取设备信息。
            BluetoothDevice device = result.getDevice();
            if (device.getName() == null) {
                return;
            }
            // TODO
            for (int i = 0; i < bluetoothDeviceArrayList.size(); i++) {
                if (device.getAddress().equals(bluetoothDeviceArrayList.get(i).getAddress())) {
                    return;
                }
            }
            bluetoothDeviceArrayList.add(device);
        }

        //批量返回扫描结果
        //@param results 以前扫描到的扫描结果列表。
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

        }

        //当扫描不能开启时回调
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            //扫描太频繁会返回ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED，表示app无法注册，无法开始扫描。
            //android 7.0后不能在30秒内扫描次数超过5次

        }
    };

    /**
     *
     */
    private void showScanDeviceList(){
        final String[] deviceNames = new String[bluetoothDeviceArrayList.size()];
        for (int i = 0; i < bluetoothDeviceArrayList.size(); i++) {
            if (bluetoothDeviceArrayList.get(i).getName() == null) {
                deviceNames[i] = "Unknow";
            } else {
                deviceNames[i] = bluetoothDeviceArrayList.get(i).getName();
            }
        }
        // TODO 弹出框问你要连接谁 后期应该只留下 SimpleBLEPeripheral Mac：54:6C:0E:83:42:82
        new AlertDialog.Builder(this).setTitle(getString(R.string.list_select_ble))
                .setSingleChoiceItems(deviceNames, -1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        selIndex = item;
                    }
                }).setPositiveButton(getString(R.string.list_ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                waitDialog.setMessage(getString(R.string.list_connecting_ble));
                waitDialog.show();
                connectLeDevice(bluetoothDeviceArrayList.get(selIndex).getAddress());
            }
        }).setNegativeButton(getString(R.string.list_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        }).show();
    }
    /**
     *连接的时候，我们可以通过搜索到的mac地址来进行连接
     * @param address
     * @return
     */

    public boolean connectLeDevice(final String address) {
        Log.d(TAG, "连接" + address);
        if (mBluetoothAdapter == null || address == null) {
            Log.d(TAG,"BluetoothAdapter不能初始化 or 未知 address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.d(TAG, "设备没找到，不能连接");
            return false;
        }

        if(mBluetoothGatt!=null){
            mBluetoothGatt.close();
        }
        readCharacteristicArrayList.clear();
        writeCharacteristicArrayList.clear();
        notifyCharacteristicArrayList.clear();
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);//真正的连接
        //这个方法需要三个参数：一个Context对象，自动连接（boolean值,表示只要BLE设备可用是否自动连接到它），和BluetoothGattCallback调用。
        return true;
    }

    /**
     * BluetoothGattCallback用于传递一些连接状态及结果，在这处理各种连接状态
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            Log.d(TAG, "status" + status+",newSatate"+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {//当连接状态发生改变
                if (mBluetoothGatt == gatt){
                    mConnectionState = true;
                    // 连接成功后尝试发现服务
                    gatt.discoverServices();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            //做连接后的变化
                            btn_ble.setText(getString(R.string.list_disconnect_ble));
                            device_name = mBluetoothGatt.getDevice().getName();
                            tv_device_name.setText("" + device_name);
                            waitDialog.dismiss();
                        }
                    });
                }else {
                    if (mBluetoothGatt == gatt){
                        mConnectionState = true;
                        gatt.discoverServices();
                        device_name = mBluetoothGatt.getDevice().getName();
                    }
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {//当设备无法连接
                if (mBluetoothGatt == gatt){
                    mConnectionState = false;
                    if(mBluetoothGatt!=null){
                        mBluetoothGatt.close();
                    }
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            //未连接
                            waitDialog.dismiss();
                            btn_ble.setText(getString(R.string.list_connect_ble));
                            device_name = "";
                            tv_device_name.setText("");
                        }
                    });
                }else {
                    if (mBluetoothGatt == gatt){
                        mConnectionState = false;
                        if(mBluetoothGatt!=null){
                            mBluetoothGatt.close();
                        }
                        device_name = mBluetoothGatt.getDevice().getName();
                    }

                }

            }
        }


        @Override
        // 发现新服务，即调用了mBluetoothGatt.discoverServices()后，返回的数据
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (mBluetoothGatt == gatt) {
                    if (USE_SPECIFIC_UUID) {
                        //获取指定uuid的service
                        BluetoothGattService gattService = mBluetoothGatt.getService(mServiceUUID);
                        //获取到特定的服务不为空
                        if (gattService != null) {
                            //获取指定uuid的Characteristic
                            BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(mCharacteristicUUID);
                            // TODO
                            setmBluetoothGattNotification(mBluetoothGatt, gattCharacteristic, true);
                            //获取特定特征成功
                            if (gattCharacteristic != null) {
                                readCharacteristicArrayList.add(gattCharacteristic);
                                writeCharacteristicArrayList.add(gattCharacteristic);
                                notifyCharacteristicArrayList.add(gattCharacteristic);
                            }
                        }
                    }

                }

            } else {
                Log.i(TAG, "onServicesDiscovered received: " + status);
            }
        }

        // 读写特性
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                final byte[] desData = characteristic.getValue();
                Log.i(TAG,"onCharacteristicRead:"+desData.toString());
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG,"onDescriptorWrite");
        }

        //如果对一个特性启用通知,当远程蓝牙设备特性发送变化，回调函数onCharacteristicChanged( ))被触发。
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            final byte[] desData = characteristic.getValue();
            //Log.i(TAG,"onCharacteristicChanged:"+desData.toString());
            if (mBluetoothGatt == gatt){
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        if (edit_receive_data.getText().length()>100){
                            edit_receive_data.setText("");
                        }
                        edit_receive_data.setText(edit_receive_data.getText()+" "+bytesToHex(desData));
                    }
                });
            }

        }



        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            //mBluetoothGatt.readRemoteRssi()调用得到，rssi即信号强度，做防丢器时可以不断使用此方法得到最新的信号强度，从而得到距离。
        }

        public void onCharacteristicWrite(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, int status) {

            System.out.println("--------write success----- status:" + status);
        }
    };
    /**
     * 设置蓝牙notification功能，如果有接受到数据
     */
    private boolean setmBluetoothGattNotification(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic,boolean enable){
        //Logger.d("setCharacteristicNotification");
        System.out.println("set---------test");
        bluetoothGatt.setCharacteristicNotification(characteristic, enable);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(mConfigUUID);
        descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
        return bluetoothGatt.writeDescriptor(descriptor); //descriptor write operation successfully started?
    }

        /**
         * 判断是否支持蓝牙，并打开蓝牙
         * 获取到BluetoothAdapter之后，还需要判断是否支持蓝牙，以及蓝牙是否打开。
         * 如果没打开，需要打开蓝牙：
         */
        public boolean checkBleDevice(Context context) {
            if (mBluetoothAdapter != null) {
                if (!mBluetoothAdapter.isEnabled()) {
                    boolean enable = mBluetoothAdapter.enable(); //返回值表示 是否成功打开了蓝牙功能
                    if (enable) {
                        Toast.makeText(context, getString(R.string.list_open_ble_success), Toast.LENGTH_SHORT).show();
                        return true;
                    } else {
                        Toast.makeText(context, getString(R.string.list_open_ble_error_goto_setting), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                } else {
                    return true;
                }
            } else {
                //Log.i("blueTooth", "该手机不支持蓝牙");
                Toast.makeText(context, getString(R.string.list_not_support_ble), Toast.LENGTH_SHORT).show();
                return false;

            }
        }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}

