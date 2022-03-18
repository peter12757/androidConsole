package com.eathemeat.xconsole;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.eathemeat.xconsole.dump.Dumpable;
import com.eathemeat.xconsole.dump.Dumpper;
import com.eathemeat.xconsole.dump.Getopt;
import com.eathemeat.xconsole.dump.Hprof;
import com.eathemeat.xconsole.dump.JsonDumpper;
import com.eathemeat.xconsole.dump.LongOpt;
import com.eathemeat.xconsole.dump.NativeTrace;
import com.eathemeat.xconsole.dump.Trace;


public class Console {

    public interface ICommand {
        void run(String[] args, FileDescriptor fd);
    }

    private static Console sInstance;

    private static final String TAG = "Console";

    public static Console getInstance() {
        return sInstance;
    }

    public static synchronized Console getInstance(Context context) {
        if (sInstance == null)
            sInstance = new Console(context);
        return sInstance;
    }

    private Context mContext;
    private Map<String, ICommand> mCommands =
            new ConcurrentHashMap<String, ICommand>();
    private Map<String, Object> mModules =
            new ConcurrentHashMap<String, Object>();

    private Console(Context context) {
        mContext = context;
        // commands
        registerCommand("echo", new EchoCommand());
        registerCommand("dump", new Dumpper.Command(this));
        registerCommand("jsondump", new JsonDumpper.Command(this));
        registerCommand("hprof", new Hprof.Command(mContext));
        registerCommand("nativetrace", new NativeTrace.Command(mContext));
        registerCommand("trace", new Trace.Command());
        // modules
        registerModule("debug", new Dumpable() {
            @Override
            public void dump(Dumpper dumpper) {
//                Debug.dump(dumpper);
            }
        });
    }

    public void registerCommand(String name, ICommand cmd) {
        mCommands.put(name, cmd);
    }

    public void registerModule(String name, Object module) {
        mModules.put(name, module);
    }

    public void unregisterCommand(String name) {
        mCommands.remove(name);
    }

    public void unregisterModule(String name) {
        mModules.remove(name);
    }

    private static final String[] ARGV_DUMP = new String[] { "dump" };

    public void execute(FileDescriptor fd, String... argv) {
        if (argv == null || argv.length == 0)
            argv = ARGV_DUMP;
        ICommand cmd = mCommands.get(argv[0]);
        if (cmd == null) {
            String[] argv2 = new String[argv.length + 1];
            argv2[0] = "dump";
            System.arraycopy(argv, 0, argv2, 1, argv.length);
            argv = argv2;
            cmd = mCommands.get(argv[0]);
        }
        cmd.run(argv, fd);
    }

    public void execute(String... argv) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream("/dev/null");
            execute(fos.getFD(), argv);
        } catch (Exception e) {
        } finally {
            if (fos != null)
                try {
                    fos.close();
                } catch (Exception e) {
                }
        }
    }

    public String executeRemoteActivity(String comp, String... argv) {
        String[] argv2 = new String[argv.length + 1];
        argv2[0] = comp;
        System.arraycopy(argv, 0, argv2, 1, argv.length);
        return executeRemote(argv2);
    }

    public String executeRemoteService(String comp, String... argv) {
        String[] argv2 = new String[argv.length + 2];
        argv2[0] = "service";
        argv2[1] = comp;
        System.arraycopy(argv, 0, argv2, 2, argv.length);
        return executeRemote(argv2);
    }

    public String executeRemote(String... argv) {
        Log.d(TAG, "executeRemote argv=" + Arrays.toString(argv));
        try {
            ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();
            android.os.Debug.dumpService("activity", fds[1].getFileDescriptor(), argv);
            fds[1].close();
            final int bufferSize = 1024;
            final char[] buffer = new char[bufferSize];
            final StringBuilder out = new StringBuilder();
            Reader in = new InputStreamReader(
                    new FileInputStream(fds[0].getFileDescriptor()), "UTF-8");
            for (; ; ) {
                int rsz = in.read(buffer, 0, buffer.length);
                if (rsz < 0)
                    break;
                out.append(buffer, 0, rsz);
            }
            in.close();
            return out.toString();
        } catch (IOException e) {
            Log.w(TAG, "executeRemote", e);
            return null;
        }
    }

    public static class Command implements ICommand {

        protected String[] mArgs;
        protected FileDescriptor mFd;
        protected InputStream mIn;
        protected OutputStream mOut;

        @Override
        public void run(String[] args, FileDescriptor fd) {
            mArgs = args;
            mFd = fd;
            FileInputStream fin = new FileInputStream(fd);
            FileOutputStream fout = new FileOutputStream(fd);
            run(args, fin, fout);
        }

        public void run(String[] argv, InputStream in, OutputStream out) {
            mIn = in;
            mOut = out;
            Getopt opts = new Getopt(argv[0], argv, optString(), longOptions()) {
                @Override
                public void setError(String str) {
                    Log.w(TAG, str);
                    throw new IllegalArgumentException(str);
                }
            };
            opts.setOptind(1);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, Charset.forName("utf-8")));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(out, Charset.forName("utf-8")), true);
            try {
                run(opts, reader, writer);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "run", e);
                writer.println(e.getMessage());
                writer.print(usage());
            } catch (Exception e) {
                Log.w(TAG, "run", e);
                writer.println(e.getMessage());
                e.printStackTrace(writer);
            }
            writer.flush();
        }

        protected String usage() {
            return "";
        }

        protected String optString() {
            return "";
        }

        protected LongOpt[] longOptions() {
            return null;
        }

        public void run(Getopt opts, BufferedReader reader,
                        PrintWriter writer) {
        }
    }

    public void dumpModules(Dumpper dumpper) {
        for (Entry<String, Object> e : mModules.entrySet()) {
            dumpper.dump(e.getKey(), e.getValue());
        }
    }

}

class EchoCommand extends Console.Command {

    @Override
    public void run(Getopt opts, BufferedReader reader,
                    PrintWriter writer) {
        try {
            while (true) {
                String line = reader.readLine();
                if (line.isEmpty())
                    break;
                writer.println(line);
            }
        } catch (Exception e) {
        }
    }

}

