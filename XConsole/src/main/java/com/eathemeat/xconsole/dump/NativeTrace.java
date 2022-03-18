package com.eathemeat.xconsole.dump;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;


import android.content.Context;
import android.util.Log;

import com.eathemeat.xconsole.Console;

public class NativeTrace {

    private static final String TAG = "NativeTrace";

    public static void generate(Context context) {
        int pid = android.os.Process.myPid();
        File file = new File(context.getCacheDir(), pid + ".dump");
        Process process = null;
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try {
            process = Runtime.getRuntime().exec(new String[] {
                    "debuggerd", "-b", String.valueOf(pid) });
            byte[] buffer = new byte[1024];
            in = new BufferedInputStream(process.getInputStream());
            out = new BufferedOutputStream(new FileOutputStream(file));
            int n = 0;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "generate " + e.getMessage());
        } finally {
            try{
                if (out != null){
                    out.close();
                }
            } catch (Exception e) {
            }
            try{
                if (in != null){
                    in.close();
                }
            } catch (Exception e) {
            }
            try{
                if (process != null){
                    process.waitFor();
                }
            } catch (InterruptedException e) {
                Log.d(TAG, e.getMessage());
            }
            process.destroy();
        }
    }

    public static class Command extends Console.Command {

        private Context mContext;

        public Command(Context context) {
            mContext = context;
        }

        @Override
        public void run(Getopt opts, BufferedReader reader,
                        PrintWriter writer) {
            generate(mContext);
        }

    }

}

