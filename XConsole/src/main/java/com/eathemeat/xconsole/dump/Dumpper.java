package com.eathemeat.xconsole.dump;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.text.TextUtils;
import android.util.Log;

import com.eathemeat.xconsole.Console;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Dumpper {

    private static final String TAG = "Dumpper";

    FileDescriptor mFd;
    PrintWriter mWriter;
    private int mDetail = 0;
    private String mPrefix = "";
    private String mSearch;
    private String mPath = "";
    private Matcher mMatcher;

    static final String sUsage = ""
            + "Usage: dump [-d detail] [-f prefix] [pattens...]\n";

    static final String sOptString = "d:f:p:l:s:";

    static final LongOpt[] sLongOptions = new LongOpt[] {
            new LongOpt("detail", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
            new LongOpt("prefix", LongOpt.REQUIRED_ARGUMENT, null, 'f'),
            new LongOpt("search", LongOpt.REQUIRED_ARGUMENT, null, 's'),
            new LongOpt("path", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
            new LongOpt("pathlevel", LongOpt.REQUIRED_ARGUMENT, null, 'l'),
    };

    public Dumpper(FileDescriptor fd, PrintWriter pw, String[] args) {
        this(fd, pw, new Getopt("dump", args, sOptString, sLongOptions));
    }

    public Dumpper(FileDescriptor fd, PrintWriter pw, Getopt opts) {
        mFd = fd;
        mWriter = pw;
        mDetail = 2;
        List<String[]> pattens = new ArrayList<String[]>();
        int pathlevel = -1;
        int c;
        while ((c = opts.getopt()) != -1) {
            switch (c) {
                case 'd':
                    mDetail = Integer.parseInt(opts.getOptarg());
                    break;
                case 'f':
                    mPrefix = opts.getOptarg();
                    break;
                case 's':
                    mSearch = opts.getOptarg().toLowerCase();
                    break;
                case 'p':
                    mPath = opts.getOptarg();
                    break;
                case 'l':
                    pathlevel = Integer.parseInt(opts.getOptarg());
                    break;
            }
        }
        for (String patten : opts.getArgv()) {
            patten = patten.toLowerCase();
            if (patten.startsWith("/"))
                patten = patten.substring(1);
            pattens.add(patten.split("/"));
        }
        mMatcher = new Matcher(pattens, pathlevel);
    }

    public Dumpper(PrintWriter pw, int detail) {
        mWriter = pw;
        mDetail = detail;
        mMatcher = new Matcher(new ArrayList<String[]>());
    }

    public void setDetail(int d) {
        mDetail = d;
    }

    public PrintWriter writer() {
        return mWriter;
    }

    public String prefix() {
        return mPrefix;
    }

    public int detail() {
        return mDetail;
    }

    protected void childBegin(Object value) {
        --mDetail;
        mPrefix += "  ";
    }

    protected void childEnd(Object value) {
        mPrefix = mPrefix.substring(0, mPrefix.length() - 2);
        ++mDetail;
    }

    private static String valueOf(Object value) {
        if (value instanceof Object[]) {
            return value.getClass().getComponentType().getSimpleName() +
                    "[" + ((Object[]) value).length + "]";
        } else if (value instanceof Bundle) {
            return value.getClass().getSimpleName()
                    + "[" + ((Bundle) value).size() + "]";
        } else if (value instanceof Collection) {
            return value.getClass().getSimpleName()
                    + "[" + ((Collection<?>) value).size() + "]";
        } else if (value instanceof Map) {
            return value.getClass().getSimpleName()
                    + "[" + ((Map<?, ?>) value).size() + "]";
        } else if (value instanceof Map.Entry) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
            return valueOf(entry.getKey() + " --> " + valueOf(entry.getValue()));
        } else if (value == null) {
            return "<null>";
        } else {
            return value.toString();
        }
    }

    public void dump(String title, Object value) {
        title = title == null ? valueOf(value) : title;
        String path = mPath;
        mPath = mPath + "/" + title;
        Matcher matcher = mMatcher;
        if (!matcher.mInPath) {
            Matcher matcher2 = new Matcher(mMatcher, title);
            if (!matcher2.mInPath) {
                if (matcher2.mMatches.isEmpty()) {
                    mPath = path;
                    return;
                }
                // continue search
                mMatcher = matcher2;
                dump(value);
                mMatcher = matcher;
                mPath = path;
                return;
            }
            mMatcher = matcher2;
            mWriter.println("---- " + mPath + " ----");
        } else if (mSearch != null) {
            if (title.toLowerCase().contains(mSearch)) {
                mWriter.println(mPath);
                mPath = path;
                return;
            }
        }
        if (mSearch == null)
            dumpTitle(title, value);
        childBegin(value);
        if (mDetail >= 0) {
            try {
                dump(value);
            } catch (Throwable e) {
                Log.w(TAG, "dump", e);
            }
        }
        childEnd(value);
        mMatcher = matcher;
        mPath = path;
    }

    protected void dumpTitle(String title, Object value) {
        mWriter.print(mPrefix);
        if (title != null) {
            mWriter.print(title);
            mWriter.print(": ");
        }
        mWriter.println(valueOf(value));
    }

    public void dump(Object value) {
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            for (int i = 0; i < array.length; ++i) {
                dump("#" + i, array[i]);
            }
        } else if (value instanceof Dumpable) {
            ((Dumpable) value).dump(this);
        } else if (mFd != null && (value instanceof IBinder)) {
            // Stub is IBinder, and also IInterface
            try {
                mWriter.flush();
                //mFd.sync();
                ((IBinder) value).dump(mFd, mMatcher.buildArgs(this));
            } catch (Exception e) {
                Log.w(TAG, "dump", e);
            }
        } else if (value instanceof IInterface) {
            // Stub.Proxy is not IBinder, but is IInterface
            IBinder binder = ((IInterface) value).asBinder();
            dump(" <Binder>", String.valueOf(binder));
            dump(binder);
        } else if (value instanceof Bundle) {
            for (String key : ((Bundle) value).keySet()) {
                dump(key, ((Bundle) value).get(key));
            }
        } else if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            int i = 0;
            for (Object o : collection) {
                dump("#" + i, o);
                ++i;
            }
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                dump(valueOf(e.getKey()), e.getValue());
            }
        } else if (value instanceof Map.Entry) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
            dump(entry.getValue());
        }
    }

    private static class Matcher {
        int mLevel;
        List<String[]> mMatches;
        boolean mInPath;

        Matcher(List<String[]> paths) {
            this(paths, -1);
        }

        Matcher(List<String[]> paths, int level) {
            mLevel = level;
            mMatches = paths;
            mInPath = mMatches.isEmpty();
        }

        @SuppressLint("DefaultLocale")
        Matcher(Matcher parent, String name) {
            mLevel = parent.mLevel + 1;
            mInPath = parent.mInPath;
            mMatches = parent.mMatches;
            if (!mInPath) {
                name = name.toLowerCase();
                //Log.d(TAG, "Path level=" + mLevel + ", name=" + name);
                List<String[]> matches = new ArrayList<String[]>();
                for (String[] path : mMatches) {
                    //Log.d(TAG, "Path try match " + Arrays.toString(path));
                    if (name.contains(path[mLevel])) {
                        //Log.d(TAG, "Path match " + Arrays.toString(path));
                        matches.add(path);
                        if (path.length == mLevel + 1) {
                            mInPath = true;
                        }
                    }
                }
                mMatches = matches;
            }
        }

        String[] buildArgs(Dumpper d) {
            int argc = 3; // detail, prefix, path
            if (d.mSearch != null)
                ++argc;
            if (!mInPath) {
                ++argc;
                argc += mMatches.size();
            }
            String args[] = new String[argc];
            args[0] = "--detail=" + d.mDetail;
            args[1] = "--prefix=" + d.mPrefix;
            args[2] = "--path=" + d.mPath;
            argc = 3;
            if (d.mSearch != null)
                args[argc++] = "--search=" + d.mPath;
            if (!mInPath) {
                args[argc++] = "--pathlevel=" + mLevel;
                for (String[] patten : mMatches) {
                    args[argc++] = TextUtils.join("/", patten);
                }
            }
            return args;
        }
    }

    public static class Command extends Console.Command {

        Console mConsole;

        public Command(Console console) {
            mConsole = console;
        }

        @Override
        protected String usage() {
            return Dumpper.sUsage;
        }

        @Override
        protected String optString() {
            return Dumpper.sOptString;
        }

        @Override
        protected LongOpt[] longOptions() {
            return Dumpper.sLongOptions;
        }

        @Override
        public void run(Getopt opts, BufferedReader reader,
                        PrintWriter writer) {
            Dumpper dumpper = new Dumpper(mFd, writer, opts);
            mConsole.dumpModules(dumpper);
        }

    }
}
