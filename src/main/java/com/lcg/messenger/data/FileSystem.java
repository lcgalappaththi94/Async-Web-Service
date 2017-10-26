package com.lcg.messenger.data;

import com.lcg.messenger.async.DemoAsyncService;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.serializer.SerializerException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class FileSystem implements PersistentResponse {
    private final static int keepDays = 1;
    private final static String fileExtension = ".txt";

    @Override
    public void write(String location, ODataResponse response) {
        System.out.println("write() @FileSystem");
        OData odata = OData.newInstance();
        createDirectory();

        String strFilePath = getStringFilePath(location);
        File file = new File(strFilePath);

        System.out.println(file.getPath());
        InputStream odResponseStream = null;

        try {
            odResponseStream = odata.createFixedFormatSerializer().asyncResponse(response);
            ByteBuffer inBuffer = ByteBuffer.allocate(8192);
            ReadableByteChannel ic = Channels.newChannel(odResponseStream);
            //WritableByteChannel oc = Channels.newChannel(output);
            while (ic.read(inBuffer) > 0) {
                inBuffer.flip();
                //oc.write(inBuffer);
                inBuffer.rewind();
            }

            FileOutputStream fop = new FileOutputStream(file);
            // if file doesn't exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            // get the content in bytes
            byte[] contentInBytes = trim(inBuffer.array());
            fop.write(contentInBytes);
            fop.flush();
            fop.close();
            ic.close();

            System.out.println("request Done");
        } catch (IOException e) {
            throw new ODataRuntimeException("Error on reading request content");
        } catch (SerializerException e) {
            e.printStackTrace();
        } finally {
            closeStream(odResponseStream);
        }
    }

    private String getStringFilePath(String location) {
        int lastIndex = location.lastIndexOf("/");
        return "D:\\Responses\\" + location.substring(lastIndex + 1) + ".txt";
    }

    static byte[] trim(byte[] bytes) {
        System.out.println("trim() @FileSystem");
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0) {
            --i;
        }
        return Arrays.copyOf(bytes, i + 1);
    }

    private static void closeStream(final Closeable closeable) {
        System.out.println("closeStream() @FileSystem");
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String read(String location) {
        System.out.println("read() @FileSystem");
        String strFilePath = getStringFilePath(location);

        byte[] fileContent = null;
        try {
            fileContent = Files.readAllBytes(Paths.get(strFilePath));
            Delete run = new Delete(location, true);                         //original location should be supplied
            DemoAsyncService.DELETE_EXECUTOR.execute(run);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String(fileContent);
    }

    @Override
    public void cleanUp() {
        System.out.println("cleanUp() @FileSystem");
        createDirectory();
        File folder = new File("D:\\Responses\\");
        if (folder.exists()) {
            File[] listFiles = folder.listFiles();
            long eligibleForDeletion = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000);                 //keepDays * 24 * 60 * 60 * 1000
            for (File listFile : listFiles) {
                if (listFile.getName().endsWith(fileExtension) && listFile.lastModified() < eligibleForDeletion) {
                    if (!listFile.delete()) {
                        System.out.println("Sorry Unable to Delete Files..");
                    }
                }
            }
        }
    }

    @Override
    public boolean find(String location) {
        //System.out.println("find() @FileSystem");
        String strFilePath = getStringFilePath(location);
        File f = new File(strFilePath);
        if (f.exists() && Files.isReadable(Paths.get(strFilePath))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void delete(String location) {
        System.out.println("delete() @FileSystem");
        String strFilePath = getStringFilePath(location);
        try {
            System.out.println("------------------------------------------before file delete-------------------------------------");
            while (!Files.isWritable(Paths.get(strFilePath))) ;
            Files.deleteIfExists(Paths.get(strFilePath));
            System.out.println("------------------------------------------file deleted-------------------------------------------");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDirectory() {
        File theDir = new File("D:\\Responses\\");
        if (!theDir.exists()) {                                                                                                   // if the directory does not exist, create it
            System.out.println("creating directory: " + theDir.getName());
            boolean result = false;
            try {
                theDir.mkdir();
                result = true;
            } catch (SecurityException se) {
                se.printStackTrace();
            }
            if (result) {
                System.out.println("directory created");
            }
        }
    }
}
