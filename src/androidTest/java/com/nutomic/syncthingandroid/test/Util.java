package com.nutomic.syncthingandroid.test;

import java.io.File;

public class Util {

    /**
     * Deletes the given folder and all contents.
     */
    public static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteRecursive(f);
            }
        }
        file.delete();
    }

}
