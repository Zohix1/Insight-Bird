package com.mnn.llm;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.service.notification.StatusBarNotification;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mnn.llm.notify.NotifyHelper;
import com.mnn.llm.notify.NotifyListener;
import com.mnn.llm.notify.NotifyService;
import com.mnn.llm.recylcerchat.ChatData;
import com.mnn.llm.recylcerchat.ConversationRecyclerView;



import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Conversation extends BaseActivity implements NotifyListener {

    private static final int REQUEST_CODE = 9527;
    private RecyclerView mRecyclerView;
    private ConversationRecyclerView mAdapter;
    private EditText text;
    private Button send;
    private DateFormat mDateFormat;
    private Chat mChat;
    private boolean mHistory = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //打开对话界面，初始化日期和对话
        setContentView(R.layout.activity_conversation);
        mChat = (Chat) getIntent().getSerializableExtra("chat");
        mDateFormat = new SimpleDateFormat("hh:mm aa");
        //初始化导航栏
        setupToolbarWithUpNav(R.id.toolbar, "mnn-llm", R.drawable.ic_action_back);

        //利用适配器将recycleview中的数据和视图（initData）进行绑定，并将recyclerview滚动到最后一个位置
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ConversationRecyclerView(this, initData());
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mRecyclerView.smoothScrollToPosition(mRecyclerView.getAdapter().getItemCount() - 1);
            }
        }, 1000);

        text = (EditText) findViewById(R.id.et_message);
        text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRecyclerView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mRecyclerView.smoothScrollToPosition(mRecyclerView.getAdapter().getItemCount() - 1);
                    }
                }, 500);
            }
        });
        //发送信息事件，可以改为当获取到消息时触发
        send = (Button) findViewById(R.id.bt_send);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String inputString = text.getText().toString();
                if (!inputString.equals("")){
                    //创建发送信息对象
                    ChatData item = new ChatData();
                    item.setTime(mDateFormat.format(new Date()));
                    item.setType("2");
                    item.setText(inputString);
                    mAdapter.addItem(item);
                    mRecyclerView.smoothScrollToPosition(mRecyclerView.getAdapter().getItemCount() -1);
                    text.setText("");

                    if (inputString.equals("/reset")) {
                        mChat.Reset();
                    } else {
                        //创建回复信息对象
                        ChatData response = new ChatData();
                        response.setTime(mDateFormat.format(new Date()));
                        response.setType("1");
                        response.setText("");
                        mAdapter.addItem(response);
                        mRecyclerView.smoothScrollToPosition(mRecyclerView.getAdapter().getItemCount() -1);

                        //这种创建处理器和线程的方式适用于需要在后台执行任务，并且任务执行完成后需要更新 UI 界面的情况。
                        Handler responseHandler = new Handler() {
                            @Override
                            public void handleMessage(Message msg) {
                                //当返回消息时，更新聊天界面中的回答
                                super.handleMessage(msg);
                                ChatData response = new ChatData();
                                response.setTime(mDateFormat.format(new Date()));
                                response.setType("1");
                                response.setText(msg.obj.toString());
                                mAdapter.updateRecentItem(response);
                            }
                        };
                        ResponseThread responseT = new ResponseThread(mChat, inputString, responseHandler, mHistory);
                        responseT.start();
                    }
                }
            }
        });
        //
        if (!isNLServiceEnabled()) {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivityForResult(intent, REQUEST_CODE);
        } else {
            toggleNotificationListenerService();
        }
        //将当前 Activity 设置为通知的监听器。
        NotifyHelper.getInstance().setNotifyListener((NotifyListener) this);
    }

    public boolean isNLServiceEnabled() {
        //检查是否启用通知监听服务
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (packageNames.contains(getPackageName())) {
            return true;
        }
        return false;
    }

    public void toggleNotificationListenerService() {
        //切换通知监听器服务
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(getApplicationContext(), NotifyService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(new ComponentName(getApplicationContext(), NotifyService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

//    @Override
//    public void onReceiveMessage(int type) {
//        @Override
//        public void onRemovedMessage(int type) {
//            switch (type) {
//                case N_MESSAGE:
//                    textView.setText("移除短信消息");
//                    break;
//                case N_CALL:
//                    textView.setText("移除来电消息");
//                    break;
//                case N_WX:
//                    textView.setText("移除微信消息");
//                    break;
//                case N_QQ:
//                    textView.setText("移除QQ消息");
//                    break;
//                default:
//                    break;
//            }
//        }
//    }

    @Override
    public void onReceiveMessage(StatusBarNotification sbn) {
        if (sbn.getNotification() == null) return;
//        //消息内容
//        String msgContent = "";
//        if (sbn.getNotification().tickerText != null) {
//            msgContent = sbn.getNotification().tickerText.toString();
//        }
//
//        //消息时间
//        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE).format(new Date(sbn.getPostTime()));
//        textView.setText(String.format(Locale.getDefault(),
//                "应用包名：%s\n消息内容：%s\n消息时间：%s\n",
//                sbn.getPackageName(), msgContent, time));

        // 获取Notification对象
        Notification notification = sbn.getNotification();
        // 从extras中获取详细信息
        Bundle extras = notification.extras;
        String title = "";
        String maintext = "";
        String subText = "";
        if (extras != null) {
            // 获取通知标题
            title = extras.getString(Notification.EXTRA_TITLE);

            // 获取通知文本内容
            maintext = extras.getString(Notification.EXTRA_TEXT);

            // 获取通知子文本内容
            subText = extras.getString(Notification.EXTRA_SUB_TEXT);
        }

        // 消息时间
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE).format(new Date(sbn.getPostTime()));

        if (subText == null){
            text.setText(maintext);
            send.performClick();

        } else {
            text.setText(subText);
            send.performClick();
        }
    }

    public List<ChatData> initData(){
        List<ChatData> data = new ArrayList<>();
        // set head time: year-month-day
        ChatData head = new ChatData();
        DateFormat headFormat = new SimpleDateFormat("yyyy-MM-dd");
        String headDate = headFormat.format(new Date());
        head.setTime("");
        head.setText(headDate);
        head.setType("0");
        data.add(head);
        // set first item
        ChatData item = new ChatData();
        String itemDate = mDateFormat.format(new Date());
        item.setType("1");
        item.setTime(itemDate);
        item.setText("你好，我是mnn-llm，欢迎向我提问。");
        data.add(item);

        return data;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //导航栏中图标的显示，可以改为选择其他页面
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_userphoto, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //点击导航栏中菜单后的事件，可以改为切换其他页面
        /*
        if (mHistory) {
            Toast.makeText(getBaseContext(), "关闭上下文", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getBaseContext(), "打开上下文", Toast.LENGTH_SHORT).show();
        }
        mHistory = !mHistory;
        */
        Toast.makeText(getBaseContext(), "清空记忆", Toast.LENGTH_SHORT).show();
        mChat.Reset();
        return true;
    }


}

class ResponseThread extends Thread {
    private String mInput;
    private Handler mHandler;
    private Chat mChat;
    private boolean mHistory;

    ResponseThread(Chat chat, String input, Handler handler, boolean history) {
        mChat = chat;
        mInput = input;
        mHandler = handler;
        mHistory = history;
    }

    public void run() {
        super.run();
        //通过chat中的submit函数传入输入内容
        mChat.Submit(mInput);
        String last_response = "";
        System.out.println("[MNN_DEBUG] start response\n");
        while (!last_response.contains("<eop>")) {
            try {
                Thread.sleep(50);
            } catch (Exception e) {}
            //通过response函数获取回答（50毫秒刷新一次）
            String response = new String(mChat.Response());
            if (response.equals(last_response)) {
                continue;
            } else {
                last_response = response;
            }
            Message msg = new Message();
            System.out.println("[MNN_DEBUG] " + response);
            msg.obj = response.replaceFirst("<eop>", "");
            mHandler.sendMessage(msg);
        }
        System.out.println("[MNN_DEBUG] response end\n");
        mChat.Done();
        if (!mHistory) {
            mChat.Reset();
        }
    }
}