package com.lcg.messenger.data;

import com.lcg.messenger.async.DemoAsyncService;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataResponse;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.*;
import java.util.Arrays;

public class DatabaseSystem implements PersistentResponse {
    private static Connection db = null;

    public DatabaseSystem() {
        db = getConnection();
        createTable(db);
    }

    private Connection getConnection() {
        //System.out.println("getConnection() @DatabaseSystem");
        if (db == null) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
                return DriverManager.getConnection("jdbc:mysql://localhost:3306/Asynchronous", "root", "");
            } catch (Exception se) {
                se.printStackTrace();
                return null;
            }
        } else {
            return db;
        }
    }

    @Override
    public void write(String location, ODataResponse response) {
        System.out.println("write() @DatabaseSystem");
        db = getConnection();
        OData odata = OData.newInstance();
        InputStream odResponseStream = null;
        byte[] contentInBytes;
        try {
            odResponseStream = odata.createFixedFormatSerializer().asyncResponse(response);
            ByteBuffer inBuffer = ByteBuffer.allocate(8192);
            ReadableByteChannel ic = Channels.newChannel(odResponseStream);
            while (ic.read(inBuffer) > 0) {
                inBuffer.flip();
                inBuffer.rewind();
            }

            contentInBytes = trim(inBuffer.array());
        } catch (Exception e) {
            throw new ODataRuntimeException("Error on reading request content");
        } finally {
            closeStream(odResponseStream);
        }

        try {
            PreparedStatement stmt = db.prepareStatement("INSERT INTO responses VALUES(?,?,?)");
            stmt.setString(1, location);
            stmt.setString(2, new String(contentInBytes));
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));

            int lines = stmt.executeUpdate();
            System.out.println(lines + " records inserted");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String read(String location) {
        System.out.println("read() @DatabaseSystem");
        db = getConnection();
        try {
            PreparedStatement stmt = db.prepareStatement("SELECT * FROM responses WHERE location=?");
            stmt.setString(1, location);
            ResultSet rs = stmt.executeQuery();
            String result = null;
            if (rs.next()) {
                result = rs.getString("response");
            }
            Delete run = new Delete(location, true);
            DemoAsyncService.DELETE_EXECUTOR.execute(run);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void cleanUp() {
        System.out.println("cleanUp() @DatabaseSystem");
        try {
            db = getConnection();
            PreparedStatement stmt = db.prepareStatement("DELETE FROM responses WHERE created < (NOW() - INTERVAL 1 DAY)");
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean find(String location) {
        //System.out.println("find() @DatabaseSystem");
        db = getConnection();
        try {
            PreparedStatement stmt = db.prepareStatement("SELECT * FROM responses WHERE location=?");
            stmt.setString(1, location);
            ResultSet rs;
            boolean available;
            rs = stmt.executeQuery();
            available = rs.next();
            return available;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void delete(String location) {
        System.out.println("delete() @DatabaseSystem");
        db = getConnection();
        System.out.println("------------------------------------before database entry delete---------------------------------");
        try {
            PreparedStatement stmt = db.prepareStatement("DELETE  FROM responses WHERE location=?");
            stmt.setString(1, location);
            stmt.execute();
            System.out.println("------------------------------------database entry deleted---------------------------------------");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void closeStream(final Closeable closeable) {
        System.out.println("closeStream() @DatabaseSystem");
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static byte[] trim(byte[] bytes) {
        System.out.println("trim() @DatabaseSystem");
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0) {
            --i;
        }
        return Arrays.copyOf(bytes, i + 1);
    }

    private static void createTable(Connection db) {
        PreparedStatement preparedStatement = null;
        String createTableSQL = "CREATE TABLE IF NOT EXISTS responses(location VARCHAR(100) PRIMARY KEY NOT NULL,response MEDIUMTEXT NOT NULL,created DATETIME NOT NULL)";

        try {
            preparedStatement = db.prepareStatement(createTableSQL);
            int affected = preparedStatement.executeUpdate();// execute create SQL statement
            if (affected > 0) {
                System.out.println("Table \"response\" is created!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (Exception ee) {
                ee.printStackTrace();
            }
        }
    }
}
