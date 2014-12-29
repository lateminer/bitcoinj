package org.blackcoinj.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.StoredTransactionOutput;
import org.bitcoinj.core.StoredUndoableBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.blackcoinj.pos.BlackcoinMagic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class SQLiteFullPrunedBlockstore implements FullPrunedBlockStore{
	private static final Logger log = LoggerFactory.getLogger(SQLiteFullPrunedBlockstore.class);
	static final String driver = "org.sqlite.JDBC";
	private static final String CREATE_HEADERS_UNDOABLE_TABLE = "CREATE TABLE headundo ( "
	        + "hash BINARY(32) NOT NULL CONSTRAINT headers_pk PRIMARY KEY,"
	        + "headers_undoable BLOB NOT NULL,"
	        + ")";
	static final String CREATE_SETTINGS_TABLE = "CREATE TABLE settings ( "
	        + "name VARCHAR(32) NOT NULL CONSTRAINT settings_pk PRIMARY KEY,"
	        + "value BLOB"
	        + ")";
	private static final String CREATE_TXOUTS_TABLE = "CREATE TABLE txouts ( "
			+ "hash BINARY(32) NOT NULL,"
	        + "txindex INT NOT NULL,"
	        + "tx_outs BLOB NOT NULL"
	        + ")";
	
	static final String CHAIN_HEAD_SETTING = "chainhead";
    static final String VERIFIED_CHAIN_HEAD_SETTING = "verifiedchainhead";
	
	private NetworkParameters params;
	private ThreadLocal<Connection> conn;
	private String connectionURL;
	private BlackDes desera;
	private Sha256Hash chainHeadHash;
	private StoredBlock chainHeadBlock;
	private Sha256Hash verifiedChainHeadHash;
	private StoredBlock verifiedChainHeadBlock;
	private List<Connection> allConnections;
	private int prunedHeight;

	public SQLiteFullPrunedBlockstore(NetworkParameters params, String dbName) throws BlockStoreException {
		connectionURL = "jdbc:sqlite:" + dbName + ".db";
		this.params = params;
		this.desera = new BlackDes();
		conn = new ThreadLocal<Connection>();
		allConnections = new LinkedList<Connection>();
		
        try {
            Class.forName(driver);
            log.info(driver + " loaded. ");
        } catch (java.lang.ClassNotFoundException e) {
            log.error("check CLASSPATH for H2 jar ", e);
        }
        
        maybeConnect();
        
        try {
            // Create tables if needed
            if (!tableExists("settings"))
                createTables();
            initFromDatabase();
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
	}

	private void initFromDatabase() throws SQLException, BlockStoreException {
		Statement s = conn.get().createStatement();        
        ResultSet rs = s.executeQuery("SELECT value FROM settings WHERE name = '" + CHAIN_HEAD_SETTING + "'");
        if (!rs.next()) {
            throw new BlockStoreException("corrupt SQLite block store - no chain head pointer");
        }
        Sha256Hash hash = new Sha256Hash(rs.getBytes(1));
        rs.close();
        this.chainHeadBlock = get(hash);
        this.chainHeadHash = hash;
        if (this.chainHeadBlock == null)
        {
            throw new BlockStoreException("corrupt H2 block store - head block not found");
        }
        
        rs = s.executeQuery("SELECT value FROM settings WHERE name = '" + VERIFIED_CHAIN_HEAD_SETTING + "'");
        if (!rs.next()) {
            throw new BlockStoreException("corrupt SQLite block store - no verified chain head pointer");
        }
        hash = new Sha256Hash(rs.getBytes(1));
        rs.close();
        s.close();
        this.verifiedChainHeadBlock = get(hash);
        this.prunedHeight = verifiedChainHeadBlock.getHeight();
        this.verifiedChainHeadHash = hash;
        if (this.verifiedChainHeadBlock == null)
        {
            throw new BlockStoreException("corrupt SQLite block store - verified head block not found");
        }
		
	}

	private void createTables() throws SQLException, BlockStoreException {
		Statement s = conn.get().createStatement();
        log.debug("SQLitteFullPrunedBlockstore : CREATE headundo table");
        s.executeUpdate(CREATE_HEADERS_UNDOABLE_TABLE);

        log.debug("SQLitteFullPrunedBlockstore : CREATE settings table");
        s.executeUpdate(CREATE_SETTINGS_TABLE);
        
        log.debug("SQLitteFullPrunedBlockstore : CREATE output block table");
        s.executeUpdate(CREATE_TXOUTS_TABLE);
        
        s.executeUpdate("INSERT INTO settings(name, value) VALUES('" + CHAIN_HEAD_SETTING + "', NULL)");
        s.executeUpdate("INSERT INTO settings(name, value) VALUES('" + VERIFIED_CHAIN_HEAD_SETTING + "', NULL)");
        s.close();
        createNewStore(params);		
	}

	private void createNewStore(NetworkParameters params2)  throws BlockStoreException{
		try {
            // Set up the genesis block. When we start out fresh, it is by
            // definition the top of the chain.
            StoredBlock storedGenesisHeader = new StoredBlock(params.getGenesisBlock().cloneAsHeader(), params.getGenesisBlock().getWork(), 0);
            // The coinbase in the genesis block is not spendable. This is because of how the reference client inits
            // its database - the genesis transaction isn't actually in the db so its spent flags can never be updated.
            List<Transaction> genesisTransactions = Lists.newLinkedList();
            StoredUndoableBlock storedGenesis = new StoredUndoableBlock(params.getGenesisBlock().getHash(), genesisTransactions);
            put(storedGenesisHeader, storedGenesis);
            setChainHead(storedGenesisHeader);
            setVerifiedChainHead(storedGenesisHeader);
        } catch (VerificationException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
		
	}

	private boolean tableExists(String table) throws SQLException {
		Statement s = conn.get().createStatement();
        try {
            ResultSet results = s.executeQuery("SELECT * FROM " + table + " WHERE 1 = 2");
            results.close();
            return true;
        } catch (SQLException ex) {
            return false;
        } finally {
            s.close();
        }
	}

	private void maybeConnect() throws BlockStoreException {
		try {
            if (conn.get() != null)
                return;
            
            conn.set(DriverManager.getConnection(connectionURL));
            allConnections.add(conn.get());
            log.info("Made a new connection to database " + connectionURL);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
		
	}

	@Override
	public void put(StoredBlock storedBlock) throws BlockStoreException {
		maybeConnect();
		putUpdateStoredBlock(storedBlock, null, false);
	}

	private void putUpdateStoredBlock(StoredBlock storedBlock, StoredUndoableBlock undoableBlock, boolean wasUndoable) 
			throws BlockStoreException {
		byte[] hashBytes = storedBlock.getHeader().getHash().getBytes();
		BlackStored black = null;
		if(undoableBlock == null){
			black = new BlackStored(desera.serialize(storedBlock), wasUndoable, new byte[0]);
		}else{
			black = new BlackStored(desera.serialize(storedBlock),wasUndoable, desera.serialize(undoableBlock));
			updatePrevBlock(storedBlock);
		}		
		try {
            PreparedStatement s =
                    conn.get().prepareStatement("INSERT INTO headundo(hash, headers_undoable)"
                            + " VALUES(?, ?)");                 
            s.setBytes(1, hashBytes);
            s.setBytes(2, desera.serialize(black));
            s.executeUpdate();
            s.close();
        } catch (SQLException e) {
            // It is possible we try to add a duplicate StoredBlock if we upgraded
            // In that case, we just update the entry to mark it wasUndoable
            if (e.getErrorCode() != 23505 || !wasUndoable)
            	throw new BlockStoreException(e);
           
			black.setWasUndoable(true);
			updateHeadersUndoable(hashBytes, this.desera.serialize(black));
        }
    }

	private void updatePrevBlock(StoredBlock storedBlock) throws BlockStoreException {
		Sha256Hash nextHash = storedBlock.getHeader().getHash();
		Sha256Hash prevHash = storedBlock.getHeader().getPrevBlockHash();
		if(prevHash.equals(Sha256Hash.ZERO_HASH))
			return;
		maybeConnect();
		byte[] blackBlock = getStoredBlockBytes(prevHash);
		
		if(blackBlock!=null && blackBlock.length!=0){
			BlackStored storedBlack = this.desera.buildBlackStored(blackBlock);
			byte[] storedBytes = storedBlack.getStoredBytes();
			System.arraycopy(nextHash.getBytes(), 0, storedBytes, 0, nextHash.getBytes().length);			
			BlackStored updatedBlack = new BlackStored(storedBytes, storedBlack.wasUndoable(),
					storedBlack.getUndoableBytes());
			updateHeadersUndoable(prevHash.getBytes(), this.desera.serialize(updatedBlack));
		}
		
	}

	private void updateHeadersUndoable(byte[] hashBytes, byte[] blackBytes) throws BlockStoreException {
		PreparedStatement s;
		try {
			s = conn.get().prepareStatement("UPDATE headundo SET headers_undoable=? WHERE hash=?");
			s.setBytes(1, blackBytes);
		    s.setBytes(2, hashBytes);
		    s.executeUpdate();
		    s.close();	
		} catch (SQLException e) {
			throw new BlockStoreException(e);
		}
       	
	}

	private byte[] getStoredBlockBytes(Sha256Hash hash) throws BlockStoreException {
		byte[] hashBytes = hash.getBytes();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement("SELECT headers_undoable FROM headundo WHERE hash = ?");
            s.setBytes(1, hashBytes);
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
        return results.getBytes(1);            
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } catch (ProtocolException e) {
            // Corrupted database.
            throw new BlockStoreException(e);
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
	}

	@Override
	public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
		return get(hash, false);
	}

	private StoredBlock get(Sha256Hash hash, boolean wasUndoableOnly) throws BlockStoreException {
		if (chainHeadHash != null && chainHeadHash.equals(hash))
            return chainHeadBlock;
        if (verifiedChainHeadHash != null && verifiedChainHeadHash.equals(hash))
            return verifiedChainHeadBlock;
        
        maybeConnect();
       
        BlackStored storedBlack;
        try {
        	byte[] storedBytes = getStoredBlockBytes(hash);
			// Parse it.
            if(storedBytes == null){
    			return null;
    		}
    		storedBlack = this.desera.buildBlackStored(storedBytes);    		
    		if (wasUndoableOnly && !storedBlack.wasUndoable())
                return null;          
            return this.desera.buildStoredBlock(params, storedBlack.getStoredBytes());
        } catch (ProtocolException e) {
            // Corrupted database.
            throw new BlockStoreException(e);
        } catch (VerificationException e) {
            // Should not be able to happen unless the database contains bad
            // blocks.
            throw new BlockStoreException(e);
        } 
 	}

	@Override
	public StoredBlock getChainHead() throws BlockStoreException {
		return this.chainHeadBlock;
	}

	@Override
	public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
		this.chainHeadBlock = chainHead;
		this.chainHeadHash = chainHead.getHeader().getHash();
		maybeConnect();
        try {
            updateSetting(CHAIN_HEAD_SETTING, chainHeadHash);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        }
	}

	private void updateSetting(String name, Sha256Hash hash) throws SQLException {
		PreparedStatement s = conn.get()
		    .prepareStatement("UPDATE settings SET value = ? WHERE name = ?");
		s.setBytes(1, hash.getBytes());
		s.setString(2, name);		
		s.executeUpdate();
		s.close();
	}

	@Override
	public void close() throws BlockStoreException {
		vacuumDB();
		for (Connection conn : allConnections) {
            try {
                conn.rollback();
                conn.close();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        allConnections.clear();
	}

	//TODO not working
	private void keepTimeUndoableBlocksWhereHeightIsLessThan(int minHeight) throws BlockStoreException {
		List<byte[]> tobePrunedBytes = retrieveFromHeadUndo(prunedHeight, minHeight);
		for(byte[] hashByte : tobePrunedBytes){
			BlackStored blackStored = this.desera.buildBlackStored(hashByte);
			StoredBlock storedBlock = this.desera.buildStoredBlock(params, blackStored.getStoredBytes());
			//genesis won't be pruned
			if(storedBlock.getHeight()!=0){
				byte[] blockTime = this.desera.serializeTime(storedBlock);
				if(storedBlock.getHeight() < minHeight){
					updateHeadersUndoable(hashByte, blockTime);
				}
			}
		}
		
	}

	private List<byte[]> retrieveFromHeadUndo(int prundHeight, int minHeight) throws BlockStoreException {
		List<byte[]> listOfUndoables = new ArrayList<byte[]>();
        PreparedStatement s = null;
        try {
			s = conn.get().prepareStatement("SELECT headers_undoable FROM headundo WHERE height between ? and ?");
			s.setInt(1, prundHeight);
	        s.setInt(2, minHeight);
	        ResultSet results = s.executeQuery();
	        if (!results.next()) {
	        	return null;
	        } 
	        while (results.next()) {
	        	listOfUndoables.add(results.getBytes("headers_undoable"));
			}
	        s.close();
			return listOfUndoables;
		} catch (SQLException e) {
			throw new BlockStoreException(e);
		}
        
    }


	@Override
	public void put(StoredBlock storedBlock, StoredUndoableBlock undoableBlock)
			throws BlockStoreException {
			putUpdateStoredBlock(storedBlock, undoableBlock, true);		
	}

	@Override
	public StoredBlock getOnceUndoableStoredBlock(Sha256Hash hash)
			throws BlockStoreException {
		return get(hash, true);
	}

	@Override
	public StoredUndoableBlock getUndoBlock(Sha256Hash hash)
			throws BlockStoreException {
		byte[] rawUndoable = getStoredBlockBytes(hash);
		BlackStored black = this.desera.buildBlackStored(rawUndoable);
		return this.desera.buildStoredUndoableBlock(hash, params, black.getUndoableBytes());
	}

	@Override
	public StoredTransactionOutput getTransactionOutput(Sha256Hash hash,
			long index) throws BlockStoreException {
		maybeConnect();
		if(index==BlackcoinMagic.anyIndex){
			byte[] rawTx = getFirstTxOut(hash);
			return this.desera.buildStoredTransactionOutput(hash, rawTx);
		}else{
			byte[] storedOut = getTxOut(hash, index);
			StoredTransactionOutput buildStoredTransactionOutput = this.desera.buildStoredTransactionOutput(hash, storedOut);
			if(buildStoredTransactionOutput.getTxTime() == 0)
				throw new BlockStoreException("probably Transaction time was forgotten to set !");
			return buildStoredTransactionOutput;
		}
	}	

	private byte[] getTxOut(Sha256Hash hash, long index) throws BlockStoreException {
		PreparedStatement s = null;
        try {
            s = conn.get()
                .prepareStatement("SELECT tx_outs FROM txouts WHERE hash = ? AND txindex = ?");
            s.setBytes(1, hash.getBytes());
            // index is actually an unsigned int
            s.setInt(2, (int)index);
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.
            return results.getBytes(1);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (SQLException e) { throw new BlockStoreException("Failed to close PreparedStatement"); }
        }
	}

	private byte[] getFirstTxOut(Sha256Hash hash) throws BlockStoreException {
        PreparedStatement s = null;
        try {
            s = conn.get()
                .prepareStatement("SELECT tx_outs FROM txouts WHERE hash = ? LIMIT 1");
            s.setBytes(1, hash.getBytes());
            // index is actually an unsigned int
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                return null;
            }
            // Parse it.
            return results.getBytes(1);
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (SQLException e) { throw new BlockStoreException("Failed to close PreparedStatement"); }
        }
	}

	@Override
	public void addUnspentTransactionOutput(StoredTransactionOutput out)
			throws BlockStoreException {
		maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get().prepareStatement("INSERT INTO txouts (hash, txindex, tx_outs) " +
                    "VALUES (?, ?, ?)");
            s.setBytes(1, out.getHash().getBytes());
            // index is actually an unsigned int
            s.setInt(2, (int)out.getIndex());
            s.setBytes(3, this.desera.serialize(out));
            s.executeUpdate();
            s.close();
        } catch (SQLException e) {
            if (e.getErrorCode() != 23505)
                throw new BlockStoreException(e);
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (SQLException e) { throw new BlockStoreException(e); }
        }
		
	}

	@Override
	public void removeUnspentTransactionOutput(StoredTransactionOutput out)
			throws BlockStoreException {
		maybeConnect();
        try {
            PreparedStatement s = conn.get()
                .prepareStatement("DELETE FROM txouts WHERE hash = ? AND txindex = ?");
            s.setBytes(1, out.getHash().getBytes());
            // index is actually an unsigned int
            s.setInt(2, (int)out.getIndex());
            s.executeUpdate();
            int updateCount = s.getUpdateCount();
            s.close();
            if (updateCount == 0)
                throw new BlockStoreException("Tried to remove a StoredTransactionOutput from H2FullPrunedBlockStore that it didn't have!");
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
		
	}

	@Override
	public boolean hasUnspentOutputs(Sha256Hash hash, int numOutputs)
			throws BlockStoreException {
		maybeConnect();
        PreparedStatement s = null;
        try {
            s = conn.get()
                .prepareStatement("SELECT COUNT(*) FROM txouts WHERE hash = ?");
            s.setBytes(1, hash.getBytes());
            ResultSet results = s.executeQuery();
            if (!results.next()) {
                throw new BlockStoreException("Got no results from a COUNT(*) query");
            }
            int count = results.getInt(1);
            return count != 0;
        } catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (SQLException e) { throw new BlockStoreException("Failed to close PreparedStatement"); }
        }
    }

	@Override
	public StoredBlock getVerifiedChainHead() throws BlockStoreException {
		return verifiedChainHeadBlock;
	}

	@Override
	public void setVerifiedChainHead(StoredBlock chainHead)
			throws BlockStoreException {
		this.verifiedChainHeadHash = chainHead.getHeader().getHash();
		try {
			updateSetting(VERIFIED_CHAIN_HEAD_SETTING, verifiedChainHeadHash);
		} catch (SQLException e) {
			throw new BlockStoreException(e);
		}
		this.verifiedChainHeadBlock = chainHead;		
		if (this.chainHeadBlock.getHeight() < chainHead.getHeight())
            setChainHead(chainHead);
		
		if(getChainHead().getHeight() > (BlackcoinMagic.minimumStoreDepth + BlackcoinMagic.secondForkHeight)){			
				//keepTimeUndoableBlocksWhereHeightIsLessThan(prunedHeight);	
		}
			
		prunedHeight = getChainHead().getHeight() - BlackcoinMagic.minimumStoreDepth;
	}
    
    private void vacuumDB() throws BlockStoreException{
    	PreparedStatement s = null;
		try {
            s = conn.get().prepareStatement("VACUUM");
            s.executeUpdate();
            s.close();
		} catch (SQLException ex) {
            throw new BlockStoreException(ex);
        } catch (ProtocolException e) {
            // Corrupted database.
            throw new BlockStoreException(e);
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (SQLException e) {
                    throw new BlockStoreException("Failed to close PreparedStatement");
                }
            }
        }
    }

    @Override
    public void beginDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        try {
            conn.get().setAutoCommit(false);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void commitDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        try {
            conn.get().commit();
            conn.get().setAutoCommit(true);
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }

    @Override
    public void abortDatabaseBatchWrite() throws BlockStoreException {
        maybeConnect();
        try {
        	if (!conn.get().getAutoCommit()) {
        		conn.get().rollback();
                conn.get().setAutoCommit(true);
        	}           
        } catch (SQLException e) {
            throw new BlockStoreException(e);
        }
    }
}
