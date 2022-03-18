package com.eathemeat.xconsole.dump;


import java.io.BufferedReader;
import java.io.PrintWriter;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.eathemeat.xconsole.Console;

public class Trace {

    private static final int SIGQUIT = 3;

    public static void generate() {
        generate(Os.getpid());
    }

    public static void generate(Context context, String process) {
//        generate(Os.getpid(context, process));
    }

    public static void generate(int pid) {
        // @see ActivityManagerService.dumpStackTraces
        try {
            Os.kill(pid, SIGQUIT);
        } catch (ErrnoException e) {
            Log.e("Trace","generte err pid:"+pid,e);
        }
    }

    public static class Command extends Console.Command {

        public Command() {
        }

        @Override
        public void run(Getopt opts, BufferedReader reader,
                        PrintWriter writer) {
            generate();
        }

    }

}

