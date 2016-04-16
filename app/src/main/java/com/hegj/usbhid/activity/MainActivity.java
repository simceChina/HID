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

    private Button btsend; // ���Ͱ�ť
    private EditText logtext;
    private UsbManager manager; // USB������
    private UsbDevice device; // �ҵ���USB�豸
    private ListView lsv1; // ��ʾUSB��Ϣ��
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

        // ��ȡUSB�豸
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            logstr.append("usbManager is null \r\n");
            return;
        } else {
            logstr.append("usb device : " + manager.toString()+NEWLINE);
            Log.i(TAG, "usb�豸��" + String.valueOf(manager.toString()));
        }
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Log.i(TAG, "usb�豸��" + String.valueOf(deviceList.size()));
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            break;
//            USBDeviceList.add(String.valueOf(device.getVendorId()));
//            USBDeviceList.add(String.valueOf(device.getProductId()));

            // ��������Ӵ����豸�Ĵ���
//            if (device.getVendorId() == 6790 && device.getProductId() == 57360) {
//                mUsbDevice = device;
//                Log.i(TAG, "�ҵ��豸");
//            }
        }

        if(device != null){
            logstr.append("find device :"+NEWLINE);
            logstr.append("vendorId:"+device.getVendorId()+NEWLINE);
            logstr.append("productId:"+device.getProductId()+NEWLINE);
        }
//        // ����һ��ArrayAdapter
//        lsv1.setAdapter(new ArrayAdapter<String>(this,
//                android.R.layout.simple_list_item_1, USBDeviceList));
        findIntfAndEpt();

    }





    // Ѱ�ҽӿںͷ�����
    private void findIntfAndEpt() {
        if (device == null) {
            logstr.append("device is null "+NEWLINE);
            Log.i(TAG, "û���ҵ��豸");
            return;
        }
        for (int i = 0; i < device.getInterfaceCount();) {
            // ��ȡ�豸�ӿڣ�һ�㶼��һ���ӿڣ�����Դ�ӡgetInterfaceCount()�����鿴��
            // �ڵĸ�����������ӿ����������˵㣬OUT �� IN
            UsbInterface intf = device.getInterface(i);
            logstr.append(i+":"+intf+NEWLINE);
            Log.d(TAG, i + " " + intf);
            mInterface = intf;
            break;
        }

        if (mInterface != null) {
            UsbDeviceConnection connection = null;
            // �ж��Ƿ���Ȩ��
            if (manager.hasPermission(device)) {
                // ���豸����ȡ UsbDeviceConnection ���������豸�����ں����ͨѶ
                connection = manager.openDevice(device);
                if (connection == null) {
                    logstr.append("connection is null "+NEWLINE);
                    return;
                }
                if (connection.claimInterface(mInterface, true)) {
                    logstr.append("interface has been found 1" +NEWLINE);
                    Log.i(TAG, "�ҵ��ӿ�");
                    mDeviceConnection = connection;
                    // ��UsbDeviceConnection �� UsbInterface ���ж˵����ú�ͨѶ
                    getEndpoint(mDeviceConnection, mInterface);
                } else {
                    logstr.append("interface not found 1" +NEWLINE);
                    connection.close();
                }
            } else {
                logstr.append("no permission "+ NEWLINE);
                Log.i(TAG, "û��Ȩ��");
            }
        } else {
            logstr.append("interface not found 2 "+NEWLINE);
            Log.i(TAG, "û���ҵ��ӿ�");
        }
    }

    private UsbEndpoint epOut;
    private UsbEndpoint epIn;

    // ��UsbDeviceConnection �� UsbInterface ���ж˵����ú�ͨѶ
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

    private byte[] Sendbytes; // ������Ϣ�ֽ�
    private byte[] Receiveytes; // ������Ϣ�ֽ�


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

            // 1,����׼������
            ret = mDeviceConnection.bulkTransfer(epOut, Sendbytes, Sendbytes.length, 5000);
            logstr.append("command has been sent : " + ret +NEWLINE);
            Log.i(TAG, "�Ѿ�����!");

            // 2,���շ��ͳɹ���Ϣ
            Receiveytes = new byte[32];
            ret = mDeviceConnection.bulkTransfer(epIn, Receiveytes, Receiveytes.length, 10000);
            Log.i(TAG, "���շ���ֵ:" + String.valueOf(ret));
            logstr.append("get it :"+ret+NEWLINE);
            if (ret != 32) {
//            DisplayToast("���շ���ֵ" + String.valueOf(ret));
                return;
            } else {
                // �鿴����ֵ
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
//            // 1,����׼������
//            ret = mDeviceConnection.bulkTransfer(epOut, Sendbytes,
//                    Sendbytes.length, 5000);
//            Log.i(TAG, "�Ѿ�����!");
//
//            // 2,���շ��ͳɹ���Ϣ
//            Receiveytes = new byte[32];
//            ret = mDeviceConnection.bulkTransfer(epIn, Receiveytes,
//                    Receiveytes.length, 10000);
//            Log.i(TAG, "���շ���ֵ:" + String.valueOf(ret));
//            if (ret != 32) {
//                DisplayToast("���շ���ֵ" + String.valueOf(ret));
//                return;
//            } else {
//                // �鿴����ֵ
//                DisplayToast(clsPublic.Bytes2HexString(Receiveytes));
//                Log.i(TAG, clsPublic.Bytes2HexString(Receiveytes));
//            }
//        }
//    };

//    public void DisplayToast(CharSequence str) {
//        Toast toast = Toast.makeText(this, str, Toast.LENGTH_LONG);
//        // ����Toast��ʾ��λ��
//        toast.setGravity(Gravity.TOP, 0, 200);
//        // ��ʾToast
//        toast.show();
//    }
}
