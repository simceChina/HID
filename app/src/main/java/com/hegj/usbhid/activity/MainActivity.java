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

import com.hegj.usbhid.R;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends Activity {

    private static final String TAG = "MissileLauncherActivity";

    private Button btsend; // 发送按钮
    private EditText logtext;
    private UsbManager manager; // USB管理器
    private UsbDevice device; // 找到的USB设备
    private ListView lsv1; // 显示USB信息的
    private UsbInterface mInterface;
    private UsbDeviceConnection mDeviceConnection;

    private StringBuilder logstr = new StringBuilder("");
    private static final String NEWLINE = "\r\n";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

//        btsend = (Button) findViewById(R.id.btnSend);
        logtext = (EditText) findViewById(R.id.edtlogText);
//        btsend.setOnClickListener(btsendListener);
//
//        lsv1 = (ListView) findViewById(R.id.lsv1);

        // 获取USB设备
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            logstr.append("usbManager is null \r\n");
            return;
        } else {
            logstr.append("usb device : " + manager.toString()+NEWLINE);
            Log.i(TAG, "usb设备：" + String.valueOf(manager.toString()));
        }
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Log.i(TAG, "usb设备：" + String.valueOf(deviceList.size()));
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            break;
//            USBDeviceList.add(String.valueOf(device.getVendorId()));
//            USBDeviceList.add(String.valueOf(device.getProductId()));

            // 在这里添加处理设备的代码
//            if (device.getVendorId() == 6790 && device.getProductId() == 57360) {
//                mUsbDevice = device;
//                Log.i(TAG, "找到设备");
//            }
        }

        if(device != null){
            logstr.append("find device :"+NEWLINE);
            logstr.append("vendorId:"+device.getVendorId()+NEWLINE);
            logstr.append("productId:"+device.getProductId()+NEWLINE);
        }
//        // 创建一个ArrayAdapter
//        lsv1.setAdapter(new ArrayAdapter<String>(this,
//                android.R.layout.simple_list_item_1, USBDeviceList));
        findIntfAndEpt();

    }





    // 寻找接口和分配结点
    private void findIntfAndEpt() {
        if (device == null) {
            logstr.append("device is null "+NEWLINE);
            Log.i(TAG, "没有找到设备");
            return;
        }
        for (int i = 0; i < device.getInterfaceCount();) {
            // 获取设备接口，一般都是一个接口，你可以打印getInterfaceCount()方法查看接
            // 口的个数，在这个接口上有两个端点，OUT 和 IN
            UsbInterface intf = device.getInterface(i);
            logstr.append(i+":"+intf+NEWLINE);
            Log.d(TAG, i + " " + intf);
            mInterface = intf;
            break;
        }

        if (mInterface != null) {
            UsbDeviceConnection connection = null;
            // 判断是否有权限
            if (manager.hasPermission(device)) {
                // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
                connection = manager.openDevice(device);
                if (connection == null) {
                    logstr.append("connection is null "+NEWLINE);
                    return;
                }
                if (connection.claimInterface(mInterface, true)) {
                    logstr.append("interface has been found 1" +NEWLINE);
                    Log.i(TAG, "找到接口");
                    mDeviceConnection = connection;
                    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
                    getEndpoint(mDeviceConnection, mInterface);
                } else {
                    logstr.append("interface not found 1" +NEWLINE);
                    connection.close();
                }
            } else {
                logstr.append("no permission "+ NEWLINE);
                Log.i(TAG, "没有权限");
            }
        } else {
            logstr.append("interface not found 2 "+NEWLINE);
            Log.i(TAG, "没有找到接口");
        }
    }

    private UsbEndpoint epOut;
    private UsbEndpoint epIn;

    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
    private void getEndpoint(UsbDeviceConnection connection, UsbInterface intf) {
        if (intf.getEndpoint(1) != null) {
            epOut = intf.getEndpoint(1);
            logstr.append("out point has been found"+NEWLINE);
        }else {
            logstr.append("out point not found " + NEWLINE);
        }
        if (intf.getEndpoint(0) != null) {
            epIn = intf.getEndpoint(0);
            logstr.append("in point has been found"+NEWLINE);
        }else {
            logstr.append("int point not found"+NEWLINE);
        }
    }

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
            Sendbytes = Arrays.copyOf(bt, bt.length);

            // 1,发送准备命令
            ret = mDeviceConnection.bulkTransfer(epOut, Sendbytes, Sendbytes.length, 5000);
            logstr.append("command has been sent : " + ret +NEWLINE);
            Log.i(TAG, "已经发送!");

            // 2,接收发送成功信息
            Receiveytes = new byte[32];
            ret = mDeviceConnection.bulkTransfer(epIn, Receiveytes, Receiveytes.length, 10000);
            Log.i(TAG, "接收返回值:" + String.valueOf(ret));
            logstr.append("get it :"+ret+NEWLINE);
            if (ret != 32) {
//            DisplayToast("接收返回值" + String.valueOf(ret));
                return;
            } else {
                // 查看返回值
                logstr.append("end :"+String.valueOf(Receiveytes) +NEWLINE);
//            DisplayToast(clsPublic.Bytes2HexString(Receiveytes));
//            Log.i(TAG, clsPublic.Bytes2HexString(Receiveytes));
            }
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
}
