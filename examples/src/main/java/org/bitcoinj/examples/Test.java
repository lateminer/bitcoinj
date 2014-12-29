package org.bitcoinj.examples;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStoreException;
import org.blackcoinj.store.BlackDes;
import org.blackcoinj.store.MVStoreMaps;


import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Test {
	private static final Logger log = LoggerFactory.getLogger(Test.class);
	
	private static final String CREATE_HEADERS_UNDOABLE_TABLE = "CREATE TABLE headundo ( "
	        + "hash BINARY(32) NOT NULL CONSTRAINT headers_pk PRIMARY KEY,"
	        + "headers_undoable BLOB NOT NULL"
	        + ")";
	static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings ( "
	        + "name VARCHAR(32) NOT NULL CONSTRAINT settings_pk PRIMARY KEY,"
	        + "value BLOB"
	        + ")";
	static final String CREATE_TXOUTS_TABLE = "CREATE TABLE txouts ( "
			+ "hash BINARY(32) NOT NULL,"
			+ "txindex INT NOT NULL,"
	        + "tx_outs BLOB"
	        + ")";
	
	static ThreadLocal<Connection> conn = new ThreadLocal<Connection>();
	 static List<Connection> allConnections = new LinkedList<Connection>();
	 static String connectionURL = "jdbc:sqlite:";
	
	public static void main(String[] args) throws IOException, SQLException, BlockStoreException {
		 BlackDes desera = new BlackDes();
		 MVMap<byte[], byte[]> headUndoable;
		 MVMap<String, byte[]> txOutputs;
		 MVMap<String, byte[]> settings;
		 
		String dbName = "./blackchain";
		MVStore mvStore = new MVStore.Builder()
		 .compress()
		 .fileName(dbName)
		 .readOnly()
		 .open();

		mvStore.setReuseSpace(true);

		headUndoable = mvStore.openMap(MVStoreMaps.headUndo);
		txOutputs = mvStore.openMap(MVStoreMaps.txOuMap);
		settings = mvStore.openMap(MVStoreMaps.sett);
		 
		 try {
	            String driver= "org.sqlite.JDBC";
				Class.forName(driver);
	            log.info(driver + " loaded. ");
	        } catch (java.lang.ClassNotFoundException e) {
	            log.error("check CLASSPATH for H2 jar ", e);
	        }
		 File dbFile = new File(".");
		 connectionURL = connectionURL + dbFile.getAbsolutePath() + "/blackchain.db";  
		 		
	     if (conn.get() != null)
	                return;
	            
		conn.set(DriverManager.getConnection(connectionURL));
	            
		allConnections.add(conn.get());
	    log.info("Made a new connection to database " + connectionURL);
	    
	    createTables(conn);
	    
	    conn.get().setAutoCommit(false);    
	    PreparedStatement insertSettings =
	            conn.get().prepareStatement("INSERT INTO settings(name, value)"
	                    + " VALUES(?, ?)");
	    
	    PreparedStatement insertTxOuts =
	            conn.get().prepareStatement("INSERT INTO txouts(hash, txindex, tx_outs)"
	                    + " VALUES(?, ?, ?)");
	    
	    PreparedStatement insertHeadUndo =
	            conn.get().prepareStatement("INSERT INTO headundo(hash, headers_undoable)"
	                    + " VALUES(?, ?)");
	    
	    for (String name:settings.keySet()){
			byte[] hashBytes = settings.get(name);
	    	insertSettings.setString(1, name);
	    	Sha256Hash hash = desera.buildStoredBlock(MainNetParams.get(), hashBytes).getHeader().getHash();
	    	insertSettings.setBytes(2, hash.getBytes());
	    	insertSettings.executeUpdate();
	    }
	    
	    for (byte[] key:headUndoable.keySet()){
			 byte[] headUndoBytes = headUndoable.get(key);
			 insertHeadUndo.setBytes(1, key);
			 insertHeadUndo.setBytes(2, headUndoBytes);
			 insertHeadUndo.executeUpdate();
	    }
	    
	    for (String key:txOutputs.keySet()){
	    	String[] split = key.split(":");
			byte[] txOutBytes = txOutputs.get(key);
			insertTxOuts.setBytes(1, new Sha256Hash(split[0]).getBytes());
		    long parsedIndex = Long.parseLong(split[1]);
		    insertTxOuts.setInt(2,(int) parsedIndex);
		    insertTxOuts.setBytes(3, txOutBytes);
		    insertTxOuts.executeUpdate();
	    	
	    }
	    
	    conn.get().setAutoCommit(true);
	    insertSettings.close();
	    insertTxOuts.close();
	    insertHeadUndo.close();
	    
		 mvStore.close();
	}

	private static void createTables(ThreadLocal<Connection> conn) 
		 throws SQLException, BlockStoreException {
			Statement s = conn.get().createStatement();
	        log.debug("SQLitteFullPrunedBlockstore : CREATE headersundoable table");
	        s.executeUpdate(CREATE_HEADERS_UNDOABLE_TABLE);

	        log.debug("SQLitteFullPrunedBlockstore : CREATE settings table");
	        s.executeUpdate(CREATE_SETTINGS_TABLE);
	        
	        log.debug("SQLitteFullPrunedBlockstore : CREATE output block table");
	        s.executeUpdate(CREATE_TXOUTS_TABLE);
	        s.close();
	}
		
	
}
