package com.eathemeat.xconsole.dump;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import android.os.Bundle;

import com.eathemeat.xconsole.Console;


public class JsonDumpper extends Dumpper {

    private List<Character> mTypes = new ArrayList<Character>();
    private boolean mFirst = true;

    public JsonDumpper(FileDescriptor fd, PrintWriter pw, Getopt opts) {
        super(fd, pw, opts);
    }

    public JsonDumpper(FileDescriptor fd, PrintWriter pw, String[] args) {
        super(fd, pw, args);
    }

    public JsonDumpper(PrintWriter pw, int detail) {
        super(pw, detail);
    }

    @Override
    protected void childBegin(Object value) {
        super.childBegin(value);
        if ((value instanceof Object[]) ||
                (value instanceof Collection)) {
            mWriter.print("[");
            mTypes.add(0, 'A');
        } else if (value instanceof Dumpable
                || value instanceof Bundle
                || value instanceof Map) {
            mWriter.print("{");
            mTypes.add(0, 'M');
        } else {
            mTypes.add(0, 'U');
        }
        mFirst = true;
    }

    @Override
    protected void childEnd(Object value) {
        switch (mTypes.get(0)) {
            case 'A':
                mWriter.print("]");
                break;
            case 'M':
                mWriter.print("}");
                break;
            case 'U':
                if (value == null || value.getClass().isPrimitive()) {
                    mWriter.print(String.valueOf(value));
                } else {
                    mWriter.print("\"");
                    mWriter.print(String.valueOf(value));
                    mWriter.print("\"");
                }
        }
        mFirst = false;
        mTypes.remove(0);
        super.childEnd(value);
    }

    @Override
    public void dumpTitle(String title, Object value) {
        if (mFirst)
            mFirst = false;
        else
            mWriter.print(",");
        if (title == null)
            title = String.valueOf(value);
        if (mTypes.isEmpty())
            return;
        if (!title.startsWith("#")) {
            mWriter.print("\"");
            mWriter.print(title);
            mWriter.print("\":");
        }
    }

    public static class Command extends Dumpper.Command {

        public Command(Console console) {
            super(console);
        }

        @Override
        public void run(Getopt opts, BufferedReader reader, PrintWriter writer) {
            Dumpper dumpper = new JsonDumpper(mFd, writer, opts);
            mConsole.dumpModules(dumpper);
        }
    }
}

