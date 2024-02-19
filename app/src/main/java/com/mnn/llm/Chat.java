package com.mnn.llm;

import java.io.Serializable;

public class Chat implements Serializable {
    //chat对象，包含提交信息和回答等函数
    public native boolean Init(String modelDir);
    public native boolean Ready();
    public native float Progress();
    public native String Submit(String input);
    public native byte[] Response();
    public native void Done();
    public native void Reset();

    static {
        System.loadLibrary("llm_mnn");
    }
}
