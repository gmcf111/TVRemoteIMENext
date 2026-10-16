package xllib;

/**
 * Created by kingt on 2018/1/30.
 */

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.io.File;

/**
 * Created by kingt on 2018/1/30.
 */
public class FileUtils {

    public static String convertFileSize(long size) {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;

        if (size >= gb) {
            return String.format("%.1f GB", (float) size / gb);
        } else if (size >= mb) {
            float f = (float) size / mb;
            return String.format(f > 100 ? "%.0f M" : "%.1f M", f);
        } else if (size >= kb) {
            float f = (float) size / kb;
            return String.format(f > 100 ? "%.0f K" : "%.1f K", f);
        } else
            return String.format("%d B", size);
    }

    public static String getFileExt(String fileName){
        if(TextUtils.isEmpty(fileName)) return "";
        int p = fileName.lastIndexOf('.');
        if(p != -1) {
            return fileName.substring(p).toLowerCase();
        }
        return "";
    }
    public static boolean isMediaFile(String fileName){
        switch (getFileExt(fileName)){
            case ".avi":
            case ".mp4":
            case ".m4v":
            case ".mkv":
            case ".mov":
            case ".mpeg":
            case ".mpg":
            case ".mpe":
            case ".rm":
            case ".rmvb":
            case ".3gp":
            case ".wmv":
            case ".asf":
            case ".asx":
            case ".dat":
            case ".vob":
            case ".m3u8":
                return true;
            default: return false;
        }
    }

    public static String getFileNameWithoutExt(String filePath){
        if(TextUtils.isEmpty(filePath)) return "";
        String fileName = filePath;
        int p = fileName.lastIndexOf(File.separatorChar);
        if(p != -1){
            fileName = fileName.substring(p + 1);
        }
        p = fileName.indexOf('.');
        if(p != -1){
            fileName = fileName.substring(0, p);
        }
        return fileName;
    }

    public static String getWebMediaFileName(String url){
        Uri uri = Uri.parse(url);
        return getFileName(uri.getPath());
    }

    public static String getFileName(String filePath){
        if(TextUtils.isEmpty(filePath)) return "";
        String fileName = filePath;
        int p = fileName.lastIndexOf(File.separatorChar);
        if(p != -1){
            fileName = fileName.substring(p + 1);
        }
        return fileName;
    }


    public static void deleteDirFiles(File file){
        File[] files =  file.listFiles();
        for (File f : files) {
            try {
                if(f.isDirectory())deleteDirFiles(f);
                f.delete();
            } catch (SecurityException e) {
            }
        }
    }

    /**
     * 是否为本地文件路径（无网络 scheme）。
     */
    public static boolean isLocalFile(String url){
        if(TextUtils.isEmpty(url)) return false;
        return !url.toLowerCase().startsWith("http://")
                && !url.toLowerCase().startsWith("https://")
                && !url.toLowerCase().startsWith("rtmp://")
                && !url.toLowerCase().startsWith("rtmps://")
                && !url.toLowerCase().startsWith("rtsp://")
                && !url.toLowerCase().startsWith("mms://")
                && !url.toLowerCase().startsWith("ftp://");
    }

    /**
     * 是否为直播流（无总时长、不支持拖动进度）。
     *
     * v3.0 判定：rtmp/rtmps/rtsp/mms 一律直播；http(s) 仅当扩展名不是已知媒体容器时按直播处理
     * （如 .m3u8 直播列表、无扩展名的持续流），其余按点播处理。
     */
    public static boolean isLiveMedia(String url){
        if(TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase();
        if(lower.startsWith("rtmp://") || lower.startsWith("rtmps://")
                || lower.startsWith("rtsp://") || lower.startsWith("mms://")){
            return true;
        }
        if(lower.startsWith("http://") || lower.startsWith("https://")){
            String path = lower.split("[?#]")[0];
            return !isMediaFile(path) || path.endsWith(".m3u8");
        }
        return false;
    }
}