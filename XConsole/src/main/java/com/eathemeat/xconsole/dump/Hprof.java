package com.eathemeat.xconsole.dump;


import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;


import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.eathemeat.xconsole.Console;

public class Hprof {

    public static void dump(Context context) {
        File file = new File(context.getCacheDir(), Process.myPid() + ".hprof");
        Log.d("Hprof", "dump to " + file);
        try {
            System.gc();
            android.os.Debug.dumpHprofData(file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
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
            dump(mContext);
        }

    }

}


