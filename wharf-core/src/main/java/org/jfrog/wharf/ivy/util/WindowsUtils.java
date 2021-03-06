/*
 *
 *  Copyright (C) 2010 JFrog Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */

package org.jfrog.wharf.ivy.util;

import org.apache.ivy.util.CopyProgressListener;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.Message;

import java.io.*;

/**
 * Utility class for windows
 *
 * @author Tomer Cohen
 */
public abstract class WindowsUtils {
    private static boolean mklinkWorks = true;

    // utility class
    private WindowsUtils() {
    }

    /**
     * Create a windows style symbolic link, which is slightly different than the linux symbolic links. The commands
     * that can be run in an mklink can be seen <a href="http://technet.microsoft.com/en-us/library/cc753194(WS.10).aspx">here</a>.
     * This operation will only work on Windows Vista and up, attempting to use this on Windows XP and below will result
     * in an exception and will perform a regular copy.
     *
     * @param src       The source file
     * @param dest      The destination file
     * @param l         A copy progress listener to be used to listen to operations during the mklink/copy
     * @param overwrite A flag that if set to true, and the destination file already exists, then the operation will
     *                  stop and the destination file will not be deleted.
     * @throws IOException Will be thrown should anything happen during the mklink process on the filesystem.
     */
    public static void windowsSymlink(File src, File dest, CopyProgressListener l, boolean overwrite)
            throws IOException {
        try {
            if (dest.exists()) {
                if (!dest.isFile()) {
                    throw new IOException("impossible to copy: destination is not a file: " + dest);
                }
                if (!overwrite) {
                    Message.verbose(dest + " already exists, nothing done");
                    return;
                }
            }
            if (dest.getParentFile() != null) {
                dest.getParentFile().mkdirs();
            }
            if (!mklinkWorks) {
                FileUtil.copy(src, dest, l, overwrite);
            } else {
                Runtime runtime = Runtime.getRuntime();
                Message.verbose("executing 'mklink " + src.getAbsolutePath() + " " + dest.getPath()
                        + "'");
                Process process = runtime.exec(new String[]{"cmd.exe", "/C", "mklink", dest.getAbsolutePath(),
                        src.getPath()});

                if (process.waitFor() != 0) {
                    InputStream errorStream = process.getErrorStream();
                    InputStreamReader isr = new InputStreamReader(errorStream);
                    BufferedReader br = new BufferedReader(isr);

                    StringBuffer error = new StringBuffer();
                    String line;
                    while ((line = br.readLine()) != null) {
                        error.append(line);
                        error.append('\n');
                    }

                    throw new IOException("error performing mklink " + src + " to " + dest + ":\n" + error);
                }

                // check if the creation of the symbolic link was successful
                if (!dest.exists()) {
                    throw new IOException("error performing mklink: " + dest + " doesn't exists");
                }
            }
        } catch (IOException x) {
            if (mklinkWorks) {
                Message.debug("mklink cannot be executed due to: " + x.getMessage() + "\n" +
                        "Make sure you are admin of this machine! Falling back to copy.");
                mklinkWorks = false;
            }
            StringWriter buffer = new StringWriter();
            x.printStackTrace(new PrintWriter(buffer));
            Message.debug(buffer.toString());
            FileUtil.copy(src, dest, l, overwrite);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
    }
}
