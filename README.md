### Android低功耗蓝牙搜索、连接、通信流程

#### 1.添加权限

```java
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<!-- 6.0以上要额外添加定位权限 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
```

除了蓝牙权限外，如果需要BLE feature则还需要声明uses-feature：

```java
<!-- required="true"应用只能在支持BLE的Android设备上运行 -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
```

#### 2.获取BluetoothAdapter

```jav
mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
```

#### 3.判断设备是否支持蓝牙并开启蓝牙

```java
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
```

#### 4.扫描蓝牙设备

```java
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
```



#### 5.连接设备

```java
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
```



#### 6.通信

```java
private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
    // 这里有9个要实现的方法，看情况要实现那些，用到那些就实现那些
    //当连接状态发生改变的时候
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState){
        
    };
    //回调响应特征写操作的结果。
    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
        
    };
    //回调响应特征读操作的结果。
    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
    }
    //当服务被发现的时候回调的结果
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
    }
    当连接能被被读的操作
    @Override
   public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            
            super.onDescriptorRead(gatt, descriptor, status);
      }  
    //如果对一个特性启用通知,当远程蓝牙设备特性发送变化，回调函数onCharacteristicChanged( ))被触发。
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {

    }
};
```

启用蓝牙特性通知

要在Android上启用远程通知，除了调用BluetoothGatt的setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable)之外，需要为特征写出描述符，即外围设备必须在创建特征时启用特征通知。

```java
/**
 * 设置蓝牙notification功能，如果有接受到数据
 */
private boolean setmBluetoothGattNotification(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic,boolean enable){
    //Logger.d("setCharacteristicNotification");
    bluetoothGatt.setCharacteristicNotification(characteristic, enable);
    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(mConfigUUID);
    descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
    return bluetoothGatt.writeDescriptor(descriptor); 
}
```

#### 7.参考资料

Demo源码：[BleTestDemo](https://github.com/Shanna-Song/BleTestDemo)

后期可能有些方法被弃用可以查看官方文档对方法进行替换：[Android 开发者  | Android Developers](https://developer.android.google.cn/)

Android移植他人项目：[成功运行和导入别人的android studio项目](https://blog.csdn.net/lance666/article/details/105469146)
