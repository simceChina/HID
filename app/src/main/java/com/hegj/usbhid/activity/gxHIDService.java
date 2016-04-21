package com.hegj.usbhid.activity;

/**
 * Created by windgxhuang on 2016/4/19.
 */
import android.app.Notification;
import android.os.Binder;
import android.os.IBinder;
import android.app.Service;
import android.content.Context;
import java.nio.ByteBuffer;
import java.util.HashMap;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.os.Handler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.view.InputDevice;
import com.hegj.usbhid.activity.IgxCallBack;
public class gxHIDService extends Service  {

    private static final String TAG = "gxHIDService";
    public static final String NotificationIntent = "com.gxHIDService.notification";
    private  UsbManager mUsbManager;
    private  UsbDevice  mUsbDevice;
    private  UsbDeviceConnection mUsbConnection;
    private  InputManager inputManager;
    private  UsbEndpoint  epOut,epIn;
    private  UsbInterface mUsbInterface;

    private  int VendorID = 0;
    private  int ProductID = 0;
    private  boolean device_null = true;
    private  final String ACTION_USB_PERMISSION="com.gxHIDService.USB_PERMISSION";
    private PendingIntent mPermissionIntent;


    private static String tempLog = "";

    private Intent intent = new Intent(NotificationIntent);

    public final static int OnPlugged = 1;
    public final static int OnUnplugged = 2;
    public final static int ScanedDevice = 3;
    public final static int OnRecieveData = 4;
    /**
     * 回调接口
     */
    private IgxCallBack callBack;

    //设备连接或移除的广播
    BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))/*连接*/{
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE); //拿到连接的设备
                inputManager = (InputManager)getSystemService(INPUT_SERVICE);
                int[] id_device = inputManager.getInputDeviceIds();//获取所有的设备id
                InputDevice inputDevice = inputManager.getInputDevice(id_device[id_device.length - 1]);
                outputLog("挂载的设备是什么：" + inputDevice + "\t设备：" + mUsbDevice);
                if (device.getVendorId() == VendorID && device.getProductId() == ProductID) {
                    close();
                    setDevice(device);
                    device_null = false;
                }
            }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))/*移除*/{
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                close(); /*关闭连接*/
            }
        }
    };

    @Override
    public void onCreate() {
        outputLog("gxHIDService onCreate");
        super.onCreate();

        mUiThread = Thread.currentThread();
        IntentFilter filter=new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);


        mUsbManager=(UsbManager)getSystemService(USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        readThread=new ReadThread();
        readThread.start();
    }

    public void scanDevice() {
        outputLog("scanDevice");

        mUsbManager=(UsbManager)getSystemService(USB_SERVICE);
        HashMap<String, UsbDevice> map=   mUsbManager.getDeviceList();
        for (UsbDevice device : map.values()){
            outputLog("找到设备,VendorID:" + device.getVendorId() + "  ProductID: " + device.getProductId());




            if (mUsbManager.hasPermission(device)) {
            }else{
                outputLog("no permission");
                mUsbManager.requestPermission(device, mPermissionIntent);
            }

            //发送广播
            intent.putExtra("type", ScanedDevice);
            intent.putExtra("VendorID", device.getVendorId());
            intent.putExtra("ProductID", device.getProductId());
            sendBroadcast(intent);
        }
    }
    protected void connectTo(int theVendorID, int theProductID ){
        outputLog("connect To VendorID:" + theVendorID + "  ProdutID:" + theProductID);
        VendorID = theVendorID;
        ProductID = theProductID;
        try {
            mUsbManager = (UsbManager) getSystemService(USB_SERVICE);
            HashMap<String, UsbDevice> map = mUsbManager.getDeviceList();
            for (UsbDevice device : map.values()) {
                if (device.getVendorId() == VendorID && device.getProductId() == ProductID) {
                    setDevice(device);
                    device_null = false;
                }
            }
            openDevice();

        } catch (Exception e) {
            StackTraceElement[] st = e.getStackTrace();
            for (StackTraceElement stackTraceElement : st) {
                String exclass = stackTraceElement.getClassName();
                String method = stackTraceElement.getMethodName();
                outputLog("#CLASS#" + exclass + "#METHOD#" + method + "#LINENUM#" + stackTraceElement.getLineNumber()
                        + "------" + e.getClass().getName());
            }
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        outputLog("onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }



    /**
     * 注册回调接口的方法，供外部调用
     * @param onProgressListener
     */
    public void setCallBack(IgxCallBack theCallBack) {
        outputLog("setCallBack: " + theCallBack);
        this.callBack = theCallBack;
    }


    /**
     * 打开连接
     * @param device
     */
    private  boolean openDevice(){
        outputLog("openDevice");
        if(mUsbDevice==null) {
            outputLog("mUsbDevice==null");
            return false;
        }
//        closeDevice();
        // 在open前判断是否有连接权限；对于连接权限可以静态分配，也可以动态分配权限，可以查阅相关资料
        if (mUsbManager.hasPermission(mUsbDevice)) {
            mUsbConnection=mUsbManager.openDevice(mUsbDevice);
        }else{
            outputLog("no permission");
            mUsbManager.requestPermission(mUsbDevice, mPermissionIntent);
        }
        outputLog("打开连接：" + mUsbConnection);
        if(mUsbConnection!=null)
            return mUsbConnection.claimInterface(mUsbInterface, true);
        return false;
    }

    /**
     * UsbInterface
     * 进行端点设置和通讯
     * @param intf
     */
    private void setEndpoint( UsbInterface intf) {
        outputLog("setEndpoint");
        if(intf==null) {
            outputLog("intf==null");
            return;
        }
        //设置接收数据的端点
        if (intf.getEndpoint(0) != null){
            outputLog("get in endPoit successful" );
            epIn = intf.getEndpoint(0);
        }
        //当端点为2的时候
        if(intf.getEndpointCount()==2){
            //设置发送数据的断点
            if (intf.getEndpoint(1) != null)
                outputLog("get out endPoit successful" );
                epOut = intf.getEndpoint(1);
        }
    }
    /*-----------------------------------------------------------------------*/
    private ReadThread readThread;
    /**
     * 开启一个线程来处理数据，或对权限的判断
     * @author FLT-PC
     */
    private class ReadThread extends Thread{
        @Override
        public void run() {
            super.run();
            outputLog("ReadThread:" + Thread.currentThread().getId());
            while (!isInterrupted()){
                final  byte []  buffer=receiveUsbData();
                if(buffer!=null)
                    onDataReceived(buffer);
            }
        }
    }
    /**
     * 接收到的数据进行处理,并且执行发送数据
     * @param buffer  传过来的数据
     */
    private void onDataReceived(final byte [] buffer) {
        runOnUiThread1(new Runnable() {
            @Override
            public void run() {


                intent.putExtra("type", OnRecieveData);
                intent.putExtra("data", buffer);
                sendBroadcast(intent);
//                for (int i = 0; i < buffer.length; i++) {
//                    outputLog("onDataReceived-->这个数据是：" + buffer[i]);
//                }
            }
        });
    }

    //检测到设备，并赋值
    private void setDevice(UsbDevice device){
        if(device==null)
            return ;
        if(mUsbDevice!=null)
            mUsbDevice=null;
        mUsbDevice = device;
        //获取设备接口，一般都是一个接口，可以打印getInterfaceCount()查看接口个数，在这个接口上有两个端点，OUT和IN
        outputLog("接口个数:" + mUsbDevice.getInterfaceCount());
        mUsbInterface=mUsbDevice.getInterface(0);
        setEndpoint(mUsbInterface);
    }
    private boolean b=false;
    /*--------------------------发送数据-----------------------------------*/
    //该方法是为了，应对某种设备需要先发数据才可以接受
    public int sendDataBulkTransfer(byte [] data){
        final int length=data.length;
        int ret = -100;
        if(epOut!=null){
            ret = mUsbConnection.bulkTransfer(epOut, data, length, 1000);
            mUsbConnection.claimInterface(mUsbInterface, true);
        }
        outputLog("sendDataBulkTransfer:" + data + "------>ref=" + ret);
        return ret;
    }
    /**
     * 用ControlTransfer发送数据
     * @return
     */
    private int sendDataControlTransfer(){
        mUsbInterface=  mUsbDevice.getInterface(0);
        setEndpoint(mUsbInterface);
        byte [] buffer=new byte[6];
        buffer[0]=0x42;buffer[1]=0x45;
        buffer[2]=0x47;
        buffer[3]=0x49;buffer[4]=0x4f; buffer[5]=0x01;
        final int length=buffer.length;
        int  ref=-100;
        //发送  COM
        if(epOut!=null){
            ref=mUsbConnection.controlTransfer(0x01, 0x06, 0x02, 0x01, buffer, length, 1000);
            mUsbConnection.claimInterface(mUsbInterface, true);
        }
        return ref;
    }
    /**
     * 用UsbRequest发送数据
     * @return
     */
    private int sendDataUsbRequest(){
        mUsbInterface=  mUsbDevice.getInterface(0);
        setEndpoint(mUsbInterface);
        byte [] buffer=new byte[6];
        buffer[0]=0x66;buffer[1]=0x69;
        buffer[2]=0x71;
        buffer[3]=0x73;buffer[4]=0x78; buffer[5]=0x01;
        ByteBuffer buffer2=ByteBuffer.wrap(buffer);
        UsbRequest request=new UsbRequest();
        request.setClientData(this);
        request.initialize(mUsbConnection, epOut);
        boolean b=  request.queue(buffer2, buffer.length);
        if(mUsbConnection.requestWait()==request){
            request.close();
            return 1;
        }
        request.close();
        return b?1:-1;
    }
    /*---------------------------发送数据的所有擦、测试方法结束----------------------------------------*/
    //接收数据的方法
    private byte [] receiveData(){
        if(epIn==null||mUsbConnection==null)
            return null;
        byte [] by=null;
        outputLog("接收数据------》receiveData");
        int inMax = epIn.getMaxPacketSize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(inMax);
        UsbRequest usbRequest =new UsbRequest();
        usbRequest.setClientData(this);
        usbRequest.initialize(mUsbConnection, epIn);
        boolean l = usbRequest.queue(byteBuffer, inMax);
        if(mUsbConnection.requestWait() == usbRequest){
            byte[] retData = byteBuffer.array();
            by=new byte[retData.length];
            int i=0;
            for(Byte byte1 : retData){
                by[i]=byte1;
                i++;
                outputLog("这个数据是：" + byte1);
            }
        }
        usbRequest.close();
        return by;
    }
    /**
     * 用UsbConnection.bulkTransfer接收数据
     * @return
     */
    private byte [] receiveBulkTransferData(){
        mUsbInterface=  mUsbDevice.getInterface(0);
//        setEndpoint(mUsbInterface);
        byte [] buffer=new byte[epIn.getMaxPacketSize()];
        //sendDataUsbRequest();
        int n=  mUsbConnection.bulkTransfer(epIn, buffer, epIn.getMaxPacketSize(), 1000);
//        outputLog("receiveBulkTransferData:" + buffer + "------>n="+n);
        if(n>0)
            return buffer;
        return null;
    }
    /**
     * 用UsbConnection.controlTransfer接收数据
     * @return
     */
    private byte [] receiveControlTransferData(){
        byte [] buffer=new byte[epIn.getMaxPacketSize()];
        int n=  mUsbConnection.controlTransfer(0x00, 0x06, 0x00, 0x00, buffer, epIn.getMaxPacketSize(), 1000);
        if(n>0)
            return buffer;
        return null;
    }
    /**
     * 接收数据和打开连接处理的关键
     * @return
     */
    private byte[] receiveUsbData(){
        if(mUsbDevice!=null&&mUsbManager.hasPermission(mUsbDevice)){
            if(device_null){
                device_null=false;
                outputLog("该设备获取到了权限了"); }
            //当b为true时，则表示连接已经打开了,接着为接收数据
            if(b==true){
//                return  otherData();//receiveBulkTransferData();//receiveData();//receiveUsbRequestData();
                return  receiveBulkTransferData();
            }else/*此时连接还未打开，执行打开连接步骤*/{
                HashMap<String, UsbDevice> map=  mUsbManager.getDeviceList();
                for (UsbDevice device : map.values()){
                    if(device.getVendorId()==VendorID&&device.getProductId()==ProductID){
                        setDevice(device);
                        device_null=false;
                    }
                }
                b=openDevice();}
        }
        return null;
    }
    /**
     * 用UsbRequest
     * 接收数据
     * @return
     */
    private byte [] receiveUsbRequestData(){
        //当对象为空时下面步骤将不执行
        if(mUsbConnection==null||epIn==null)
            return null;
        ByteBuffer byteBuffer = ByteBuffer.allocate(6);
        final   UsbRequest usbRequest = new UsbRequest();
        usbRequest.initialize(mUsbConnection, epIn);
        usbRequest.queue(byteBuffer, 6);
        if(mUsbConnection.requestWait() == usbRequest){
            outputLog("拿到数据了");
            usbRequest.close();
            try { finalize(); }catch(Throwable e){e.printStackTrace();}
            return  byteBuffer.array();
        }
        usbRequest.close();
        try { finalize(); } catch (Throwable e) { e.printStackTrace(); }
        return null;
    }
    /**
     * 又一个接收数据的方法
     * @return
     */
    private byte [] otherData(){
        byte[] buffer = new byte[8];
        mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_IN , 0x5f, 0, 0, buffer, 8, 1000);
        mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0xa1, 0, 0, null, 0, 1000);
        //baud rate
        int  baudRate = 4800;
        long factor = 1532620800 / baudRate;
        int divisor = 3;
        while((factor > 0xfff0) && (divisor > 0)) {
            factor >>=3;
            divisor--;
        }
        factor = 0x10000-factor;
        short a = (short) ((factor & 0xff00) | divisor);
        short b = (short)(factor & 0xff);
        int m=-100;
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0x9a, 0x1312, a, null, 0, 1000);
        outputLog("m1----->m=" + m);
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0x9a, 0x0f2c, b, null, 0, 1000);
        outputLog("m2----->m=" + m);
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_IN,   0x95, 0x2518, 0, buffer, 8, 1000);
        outputLog("m3----->m=" + m);
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0x9a, 0x0518, 0x0050, null, 0, 1000);
        outputLog("m4----->m=" + m);
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0xa1, 0x501f, 0xd90a, null, 0, 1000);
        outputLog("m5----->m=" + m);
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0x9a, 0x1312, a, null, 0, 1000);
        outputLog("m6----->m=" + m);
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0x9a, 0x0f2c, b, null, 0, 1000);
        outputLog("m7----->m=" + m);
        m=  mUsbConnection.controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT , 0xa4, 0, 0,   null, 0, 1000);
        outputLog("m8----->m="+m);
        return buffer;
    }
    private Handler handler=new Handler();

    /**
     * 关闭连接，情况部分对象
     * @param device
     * @param usbInterface
     */
    private void closeDevice(){
        outputLog("closeDevice");
        if(mUsbConnection!=null){
            synchronized (mUsbConnection){
                mUsbConnection.releaseInterface(mUsbInterface);
                mUsbConnection.close();
                mUsbConnection=null;
                epOut=null;
                epIn=null;
            }
        }
    }
    /**
     * 关闭连接，情况所有对象
     * @return
     */
    private  boolean close(){
        outputLog("close");
        if(mUsbConnection!=null){
            synchronized (mUsbConnection){
                mUsbConnection.releaseInterface(mUsbInterface);
                mUsbConnection.close();
                mUsbConnection=null;
                epOut=null;
                epIn=null;
                mUsbInterface=null;
                mUsbDevice=null;
                return true;
            }
        }
        return false;
    }

    private Thread mUiThread;
    public final void runOnUiThread1(Runnable runnable){
        if(Thread.currentThread()!=mUiThread){
            handler.post(runnable);
        }else{
            runnable.run();}
    }

    private void outputLog(String log) {
        if (callBack != null) {
            final  String outputLog = tempLog + log;
            tempLog = "";
            runOnUiThread1(new Runnable() {
                @Override
                public void run() {
                    callBack.onLog(outputLog + "\r\n");
                }
            });
        } else {
            tempLog += (log + "\r\n");
        }
//        MainActivity.outputLog("gx:" + log + "\r\n");
        return;
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        unregisterReceiver(receiver);
//        IntentFilter filter=new IntentFilter();
//        filter.addAction(ACTION_USB_PERMISSION);
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
//        registerReceiver(receiver, filter);
//        HashMap<String, UsbDevice> map=    mUsbManager.getDeviceList();
//        mUiThread=Thread.currentThread();
//        for (UsbDevice device : map.values()){
//            if(device.getVendorId()==VendorID&&device.getProductId()==ProductID){
//                close();
//                setDevice(device);
//                device_null=false;
//            }
//        }
//        openDevice();
//    }
//
    protected void disconnect() {
        close();
        unregisterReceiver(receiver);
    }




    /**
     * 返回一个Binder对象
     */
    @Override
    public gxHIDBinder onBind(Intent intent) {
        return new gxHIDBinder();
    }

    public class gxHIDBinder extends Binder{
        /**
         * 获取当前Service的实例
         * @return
         */
        public gxHIDService getService(){
            return gxHIDService.this;
        }
    }
}
