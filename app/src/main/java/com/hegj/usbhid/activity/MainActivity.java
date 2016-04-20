package com.hegj.usbhid.activity;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.hegj.usbhid.R;
import com.hegj.usbhid.activity.IgxCallBack;

import java.util.Arrays;
import java.util.HashMap;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;


import android.content.ServiceConnection;
import android.content.Intent;
import android.content.ComponentName;
import android.os.IBinder;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class MainActivity extends Activity implements IgxCallBack {

    private static final String TAG = "MissileLauncherActivity";

    @Bind(R.id.sendcmd)
    public Button btsend; // 发送按钮

    @Bind(R.id.edtlogText)
    public EditText logtext;
    @Bind(R.id.vendorIdText)
    public TextView vendorIdText;
    @Bind(R.id.productIdText)
    public TextView productIdText;

    private UsbManager manager; // USB管理器
    private UsbDevice device; // 找到的USB设备
    private ListView lsv1; // 显示USB信息的
    private UsbInterface mInterface;
    private UsbDeviceConnection mDeviceConnection;

    private StringBuilder logstr = new StringBuilder("");
    private static final String NEWLINE = "\r\n";
    private final int VendorID = 3608;
    private final int ProductID = 4;
    private UsbEndpoint epOut;
    private UsbEndpoint epIn;



    private gxHIDService theHIDService;
    private gxHIDServiceReceiver theHIDServiceReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        btsend.setEnabled(false);
        logtext.setText(logstr);
        // 获取USB设备
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);


        theHIDServiceReceiver = new gxHIDServiceReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(gxHIDService.NotificationIntent);
        registerReceiver(theHIDServiceReceiver, intentFilter);

        //绑定Service
        Intent intent = new Intent(this, gxHIDService.class);//new Intent("com.gxHIDService.communication");
        bindService(intent, conn, Context.BIND_AUTO_CREATE);


        startService(intent);
    }

    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {

        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //返回一个gxHIDService对象
            theHIDService = ((gxHIDService.gxHIDBinder)service).getService();
            IgxCallBack callBack = (IgxCallBack)MainActivity.this;
            theHIDService.setCallBack(callBack);
            theHIDService.scanDevice();

        }
    };

    @OnClick(R.id.btnClear)
    public void doConn(){
        try{
            enumerateDevice();

            findInterface();

            openDevice();

            assignEndpoint();
        }catch (Exception e){
            StackTraceElement[] st = e.getStackTrace();
            for (StackTraceElement stackTraceElement : st) {
                String exclass = stackTraceElement.getClassName();
                String method = stackTraceElement.getMethodName();
                logstr.append("#CLASS#"+exclass +"#METHOD#"+method + "#LINENUM#"+stackTraceElement.getLineNumber()
                        + "------" + e.getClass().getName()+NEWLINE);
            }
        }

    }

    /**
     * 枚举设备
     */
    private void enumerateDevice() throws Exception{
        if (manager == null){
            logstr.append("usbManager is null \r\n");
            return;
        }

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        if (!deviceList.isEmpty()) { // deviceList不为空
            for (UsbDevice d : deviceList.values()) {
                // 输出设备信息
                logstr.append("DeviceInfo: " + d.getVendorId() + " , " + d.getProductId() + NEWLINE);

                // 枚举到设备
                if (d.getVendorId() == VendorID  && d.getProductId() == ProductID) {
                    device = d;
                    vendorIdText.setText("vendorId : "+VendorID);
                    productIdText.setText("productId : "+ProductID);
                    logstr.append("find device success" + NEWLINE);
                }
            }
        }else {
            logstr.append("find nothing!"+NEWLINE);
        }
    }

    /**
     * 找设备接口
     */
    private void findInterface() throws Exception{
        if (device != null) {
            logstr.append("interfaceCounts : " + device.getInterfaceCount()+NEWLINE);
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                // 获取设备接口，一般都是一个接口，你可以打印getInterfaceCount()方法查看接
                // 口的个数，在这个接口上有两个端点，OUT 和 IN
                UsbInterface intf = device.getInterface(i);
                logstr.append(i+":"+intf+NEWLINE);
                mInterface = intf;
                break;
            }
        }else {
            logstr.append("device is null"+NEWLINE);
        }
    }

    /**
     * 打开设备
     */
    private void openDevice() throws Exception{
        if (mInterface != null) {
            UsbDeviceConnection conn = null;
            // 在open前判断是否有连接权限；对于连接权限可以静态分配，也可以动态分配权限，可以查阅相关资料
            if (manager.hasPermission(device)) {
                conn = manager.openDevice(device);
            }else{
                logstr.append("no permission"+NEWLINE);
            }

            if (conn == null) {
                logstr.append("connection is null "+NEWLINE);
                return;
            }

            if (conn.claimInterface(mInterface, true)) {
                mDeviceConnection = conn; // 到此你的android设备已经连上HID设备
                logstr.append("conn success!");
            } else {
                logstr.append("claimInterface is not success!");
                conn.close();
            }
        }else{
            logstr.append("interface is null!"+NEWLINE);
        }
    }

    /**
     * 分配端点，IN | OUT，即输入输出；此处我直接用1为OUT端点，0为IN，当然你也可以通过判断
     */
    private void assignEndpoint() throws Exception{
        if (mInterface.getEndpoint(1) != null) {
            epOut = mInterface.getEndpoint(1);
            logstr.append("out point has been found"+NEWLINE);
        }else {
            logstr.append("out point not found " + NEWLINE);
        }
        if (mInterface.getEndpoint(0) != null) {
            epIn = mInterface.getEndpoint(0);
            logstr.append("in point has been found"+NEWLINE);
        }else {
            logstr.append("int point not found"+NEWLINE);
        }
    }










//    // 寻找接口和分配结点
//    private void findIntfAndEpt() {
//        if (device == null) {
//            logstr.append("device is null "+NEWLINE);
//            Log.i(TAG, "没有找到设备");
//            return;
//        }
//        for (int i = 0; i < device.getInterfaceCount();) {
//            // 获取设备接口，一般都是一个接口，你可以打印getInterfaceCount()方法查看接
//            // 口的个数，在这个接口上有两个端点，OUT 和 IN
//            UsbInterface intf = device.getInterface(i);
//            logstr.append(i+":"+intf+NEWLINE);
//            Log.d(TAG, i + " " + intf);
//            mInterface = intf;
//            break;
//        }
//
//        if (mInterface != null) {
//            UsbDeviceConnection connection = null;
//            // 判断是否有权限
//            if (manager.hasPermission(device)) {
//                // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
//                connection = manager.openDevice(device);
//                if (connection == null) {
//                    logstr.append("connection is null "+NEWLINE);
//                    return;
//                }
//                if (connection.claimInterface(mInterface, true)) {
//                    logstr.append("interface has been found 1" +NEWLINE);
//                    Log.i(TAG, "找到接口");
//                    mDeviceConnection = connection;
//                    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
//                    getEndpoint(mDeviceConnection, mInterface);
//                } else {
//                    logstr.append("interface not found 1" +NEWLINE);
//                    connection.close();
//                }
//            } else {
//                logstr.append("no permission "+ NEWLINE);
//                Log.i(TAG, "没有权限");
//            }
//        } else {
//            logstr.append("interface not found 2 "+NEWLINE);
//            Log.i(TAG, "没有找到接口");
//        }
//    }
//
//
//
//    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
//    private void getEndpoint(UsbDeviceConnection connection, UsbInterface intf) {
//        if (intf.getEndpoint(1) != null) {
//            epOut = intf.getEndpoint(1);
//            logstr.append("out point has been found"+NEWLINE);
//        }else {
//            logstr.append("out point not found " + NEWLINE);
//        }
//        if (intf.getEndpoint(0) != null) {
//            epIn = intf.getEndpoint(0);
//            logstr.append("in point has been found"+NEWLINE);
//        }else {
//            logstr.append("int point not found"+NEWLINE);
//        }
//    }

    private byte[] Sendbytes; // 发送信息字节
    private byte[] Receiveytes; // 接收信息字节


    @OnClick(R.id.sendcmd)
    public void sendMsg(){
//        if(device == null){
//            logtext.setText(logstr);
//            return;
//        }
        try{
            int ret = -100;
            String testString = "CMDI";
            //String testString = "C783CC30";
//        byte[] bt = CHexConver.hexStr2Bytes(testString);
            byte[] bt = new byte[4];
            bt[0] = 67;
            bt[1] = 77;
            bt[2] = 68;
            bt[3] = 73;
//            Sendbytes = Arrays.copyOf(bt, bt.length);

            // 1,发送准备命令
//            ret = mDeviceConnection.bulkTransfer(epOut, Sendbytes, Sendbytes.length, 5000);
            ret = theHIDService.sendDataBulkTransfer(bt);
            onLog("command has been sent : " + ret + NEWLINE);
            Log.i(TAG, "已经发送!");
//
//            // 2,接收发送成功信息
//            Receiveytes = new byte[64];
//            ret = mDeviceConnection.bulkTransfer(epIn, Receiveytes, Receiveytes.length, 10000);
//            Log.i(TAG, "接收返回值:" + String.valueOf(ret));
//            logstr.append("get it :"+ret+NEWLINE);
//            if (ret < 0) {
////            DisplayToast("接收返回值" + String.valueOf(ret));
////                return;
//                logstr.append("receive nothing!"+NEWLINE);
//            } else {
//                // 查看返回值
//                logstr.append("RECEIVE :"+String.valueOf(Receiveytes) +NEWLINE);
////            DisplayToast(clsPublic.Bytes2HexString(Receiveytes));
////            Log.i(TAG, clsPublic.Bytes2HexString(Receiveytes));
//            }
        }catch (Exception e){
            e.printStackTrace();
            StackTraceElement[] st = e.getStackTrace();
            for (StackTraceElement stackTraceElement : st) {
                String exclass = stackTraceElement.getClassName();
                String method = stackTraceElement.getMethodName();
                logstr.append("#CLASS#"+exclass +"#METHOD#"+method + "#LINENUM#"+stackTraceElement.getLineNumber()
                        + "------" + e.getClass().getName()+NEWLINE);
            }
        }

        logtext.setText(logstr);
    }
//    private View.OnClickListener btsendListener = new View.OnClickListener() {
//        int ret = -100;
//
//        @Override
//        public void onClick(View v) {
//            String testString = "010A";
//            //String testString = "C783CC30";
//            byte[] bt = ClsPublic.HexString2Bytes(testString);
//
//            Sendbytes = Arrays.copyOf(bt, bt.length);
//
//            // 1,发送准备命令
//            ret = mDeviceConnection.bulkTransfer(epOut, Sendbytes,
//                    Sendbytes.length, 5000);
//            Log.i(TAG, "已经发送!");
//
//            // 2,接收发送成功信息
//            Receiveytes = new byte[32];
//            ret = mDeviceConnection.bulkTransfer(epIn, Receiveytes,
//                    Receiveytes.length, 10000);
//            Log.i(TAG, "接收返回值:" + String.valueOf(ret));
//            if (ret != 32) {
//                DisplayToast("接收返回值" + String.valueOf(ret));
//                return;
//            } else {
//                // 查看返回值
//                DisplayToast(clsPublic.Bytes2HexString(Receiveytes));
//                Log.i(TAG, clsPublic.Bytes2HexString(Receiveytes));
//            }
//        }
//    };

//    public void DisplayToast(CharSequence str) {
//        Toast toast = Toast.makeText(this, str, Toast.LENGTH_LONG);
//        // 设置Toast显示的位置
//        toast.setGravity(Gravity.TOP, 0, 200);
//        // 显示Toast
//        toast.show();
//    }
    private void onScanDevice(int theVendorID,  int theProductID){
        if (VendorID == theVendorID && ProductID == theProductID) {
            vendorIdText.setText("vendorId : " + VendorID);
            productIdText.setText("productId : " + ProductID);
            theHIDService.connectTo(theVendorID, theProductID);
            btsend.setEnabled(true);
        }
    }

    private void onReceiveData(byte data[]){

        Log.i(TAG, "onReceiveData");
        onLog("onReceiveData" + new String(data));
    }
    /**
     * 广播接收器
     * @author len
     *
     */
    public class gxHIDServiceReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            int type = intent.getIntExtra("type", 0);
            switch (type) {
                case gxHIDService.OnPlugged:
                    break;
                case gxHIDService.OnUnplugged:
                    break;
                case gxHIDService.ScanedDevice://扫描到设备
                    int theVendorID = intent.getIntExtra("VendorID", 0);
                    int theProductID = intent.getIntExtra("ProductID", 0);
                    onScanDevice(theVendorID, theProductID);
                    break;
                case gxHIDService.OnRecieveData:
                    byte data[] = intent.getByteArrayExtra("data");
                    onReceiveData(data);
                    break;
            }
        }

    }

    @Override
    public void  onLog(String log) {
        logstr.append(log);
        logtext.setText(logstr);
    }

}
