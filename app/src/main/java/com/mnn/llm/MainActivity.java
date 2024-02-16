package com.mnn.llm;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {
    private Chat mChat;
    private Intent mIntent;
    private Button mLoadButton;
    private TextView mModelInfo;
    private RelativeLayout mProcessView;
    private Handler mProcessHandler;
    private ProgressBar mProcessBar;
    private TextView mProcessName;
    private TextView mProcessPercent;
    private Spinner mSpinnerModels;
    // resource files
    private String mSearchPath = "/data/local/tmp/mnn-llm/";
    private String mModelName = "qwen-1.8b-int4";
    private String mModelDir = mSearchPath + mModelName; // default dir
    private boolean mModelReady = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //构造函数：初始化变量
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIntent = new Intent(this, Conversation.class);
        mModelInfo = (TextView)findViewById(R.id.model_info);
        mLoadButton = (Button)findViewById(R.id.load_button);
        mProcessView = (RelativeLayout)findViewById(R.id.process_view);
        mProcessBar = (ProgressBar)findViewById(R.id.process_bar);
        mProcessName = (TextView)findViewById(R.id.process_name);
        mProcessPercent = (TextView)findViewById(R.id.process_percent);
        mSpinnerModels = (Spinner) findViewById(R.id.spinner_models);
        // default using assert file
        mModelDir = this.getCacheDir() + "/" + mModelName;
        populateFoldersSpinner();
        mSpinnerModels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            //对下拉框（Spinner）设置选择监听器，用来选择模型
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    mModelName = (String) parent.getItemAtPosition(position);
                    mModelInfo.setText("选择模型：" + mModelName);
                    mModelInfo.setVisibility(View.VISIBLE);
                    mModelDir = mSearchPath + mModelName;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mProcessHandler = new Handler() {
            //显示加载进度条，当进度条满时进入下一个activity(Chat)
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                int progress = msg.arg1;
                mProcessBar.setProgress((int)progress);
                mProcessPercent.setText(" " + progress + "%");
                if (progress >= 100) {
                    mLoadButton.setClickable(true);
                    mLoadButton.setBackgroundColor(Color.parseColor("#3e3ddf"));
                    mLoadButton.setText("加载已完成");
                    //将名为 "chat" 的额外数据放入 mIntent 中，利用mIntent传递给新的活动
                    mIntent.putExtra("chat", mChat);
                    startActivity(mIntent);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void onCheckModels() {
        mModelReady = checkModelsReady();
        // 若模型未准备,从assert文件夹中复制模型到本地（本项目不含模型）
        if (!mModelReady) {
            try {
                mModelDir = Common.copyAssetResource2File(this, mModelName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            mModelReady = checkModelsReady();
        }
        // download models
        if (!mModelReady) {
            mModelInfo.setVisibility(View.VISIBLE);
            mModelInfo.setText("请下载模型文件");
            mLoadButton.setText("下载模型");
        } else {
            mModelInfo.setVisibility(View.VISIBLE);
            mModelInfo.setText(mModelName + "模型文件就绪，模型加载中");
            mLoadButton.setText("加载模型");
        }
    }
    public boolean checkModelsReady() {
        //检查模型是否准备就绪
        System.out.println("### Check Models!");
        File dir = new File(mModelDir);
        if (!dir.exists()) {
            return false;
        }
        String[] modelArray = this.getResources().getStringArray(R.array.model_list);
        for (int i = 0; i < modelArray.length; i++) {
            File model = new File(mModelDir, modelArray[i]);
            if (!model.exists()) {
                return false;
            }
        }
        return true;
    }

    private ArrayList<String> getFoldersList(String path) {
        //获取文件夹（qwen-1.8b-init4）中的文件名称列表
        File directory = new File(path);
        File[] files = directory.listFiles();
        ArrayList<String> foldersList = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    foldersList.add(file.getName());
                }
            }
        }
        return foldersList;
    }
    private void populateFoldersSpinner() {
//        设置模型路径,显示模型文件列表：
//        推送方式
//        adb shell mkdir /data/local/tmp/mnn-llm
//        adb push ./qwen-1.8b-mnn  /data/local/tmp/mnn-llm
        ArrayList<String> folders = getFoldersList("/data/local/tmp/mnn-llm");
        folders.add(0, getString(R.string.spinner_prompt));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folders);
        mSpinnerModels.setAdapter(adapter);
    }
    public void loadModel(View view) {
        //检查是否含有对应模型
        onCheckModels();
        if (!mModelReady) {
            //跳转至模型下载活动
            startActivity(new Intent(this, DownloadModel.class));
            return;
        }
        mLoadButton.setClickable(false);
        mLoadButton.setBackgroundColor(Color.parseColor("#2454e4"));
        mLoadButton.setText("模型加载中 ...");
        mProcessView.setVisibility(View.VISIBLE);
        mChat = new Chat();
        //创建任务处理对象，当接收到消息时，调用Chat活动
        Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mIntent.putExtra("chat", mChat);
                startActivity(mIntent);
            }
        };
        //创建loadthread线程并传入任务处理对象handler
        LoadThread loadT = new LoadThread(mChat, handler, mModelDir);
        loadT.start();
        //创建progressthread线程用于更新模型加载进度：传入的是进度条显示处理器
        ProgressThread progressT = new ProgressThread(mChat, mProcessHandler);
        progressT.start();
    }
}

class LoadThread extends Thread {
    private Chat mChat;
    private Handler mHandler;
    private String mModelDir;
    LoadThread(Chat chat, Handler handler, String modelDir) {
        mChat = chat;
        mHandler = handler;
        mModelDir = modelDir;
    }
    public void run() {
        super.run();
        mChat.Init(mModelDir);
        //向handler发送消息，用于启动handler中的chat活动
        mHandler.sendMessage(new Message());
    }
}

class ProgressThread extends Thread {
    private Handler mHandler;
    private Chat mChat;

    ProgressThread(Chat chat, Handler handler) {
        mChat = chat;
        mHandler = handler;
    }

    public void run() {
        super.run();
        float progress = 0;
        while (progress < 100) {
            //隔一段时间更新进度条数据，并返回消息给handler，进行进度显示
            try {
                Thread.sleep(50);
            } catch (Exception e) {}
            float new_progress = mChat.Progress();
            if (Math.abs(new_progress - progress) < 0.01) {
                continue;
            }
            progress = new_progress;
            Message msg = new Message();
            msg.arg1 = (int)progress;
            mHandler.sendMessage(msg);
        }
    }
}