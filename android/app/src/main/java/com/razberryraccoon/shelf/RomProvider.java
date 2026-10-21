package com.razberryraccoon.shelf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Lends one ROM file, read-only, to the emulator Shelf just started. Only files on shared storage, and only to an
 * app Shelf granted the link to (the provider isn't exported).
 */
public class RomProvider extends ContentProvider {
    static final String AUTHORITY = "com.razberryraccoon.shelf.roms";

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(f.getAbsolutePath()).build();
    }

    private static File fileFor(Uri uri) throws FileNotFoundException {
        String p = uri.getPath();
        if (p == null) throw new FileNotFoundException();
        File f = new File(p);
        try {
            String c = f.getCanonicalPath();
            if (!c.startsWith("/storage/") || !f.isFile()) throw new FileNotFoundException(p);
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException(p);
        }
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try { f = fileFor(uri); } catch (FileNotFoundException e) { return null; }
        MatrixCursor c = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
        c.addRow(new Object[] { f.getName(), f.length() });
        return c;
    }

    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
