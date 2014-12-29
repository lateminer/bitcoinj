package org.blackcoinj.store;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.StoredTransactionOutput;
import org.bitcoinj.core.StoredUndoableBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.blackcoinj.pos.BlackcoinMagic;
import org.h2.mvstore.Cursor;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;


public class H2MVStoreFullPrunedBlockstore implements FullPrunedBlockStore{
	
	static final String CHAIN_HEAD_SETTING = "chainhead";
    static final String VERIFIED_CHAIN_HEAD_SETTING = "verifiedchainhead";
	
	private Sha256Hash chainHeadHash;
    private StoredBlock chainHeadBlock;
    private Sha256Hash verifiedChainHeadHash;
    private StoredBlock verifiedChainHeadBlock;
    private BlackDes desera;
    private MVStore mvStore;
	private MVMap<byte[], byte[]> headUndoable;
	private MVMap<String, byte[]> txOutputs;
	private MVMap<String, byte[]> settings;
	private NetworkParameters params;
	private byte[] lastPrunedHash;
	
	private static final Logger log = LoggerFactory.getLogger(H2MVStoreFullPrunedBlockstore.class);
	private static final String LAST_PRUNED_HASH = "lastprunedhash";

	public H2MVStoreFullPrunedBlockstore(NetworkParameters params, String dbName) throws BlockStoreException {
		this.params = params;
		this.mvStore = new MVStore.Builder()
								.compressHigh()
								.fileName(dbName)								
								.open();
		
		mvStore.setReuseSpace(true);
		
		this.headUndoable = mvStore.openMap(MVStoreMaps.headUndo);
		this.txOutputs = mvStore.openMap(MVStoreMaps.txOuMap);
		this.settings = mvStore.openMap(MVStoreMaps.sett);
		
		this.desera = new BlackDes();
		
		try {
			initialize(params);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void initialize(NetworkParameters params) throws SQLException, BlockStoreException {
		this.chainHeadBlock = findChainHead();
		this.verifiedChainHeadBlock = findVerifiedHead();
		this.lastPrunedHash = this.settings.get(LAST_PRUNED_HASH);
		mvStore.compactMoveChunks();
		int chainHeadHeight = getChainHead().getHeight();
		//when we after the second fork and after minimum store depth we can start prunning
		// don't prune one prune minimum count
		// TODO we can do better than this
		if(( chainHeadHeight > (BlackcoinMagic.minimumStoreDepth + BlackcoinMagic.secondForkHeight))
		&& chainHeadHeight - (BlackcoinMagic.minimumStoreDepth + BlackcoinMagic.secondForkHeight) == BlackcoinMagic.minToBePruned){
			keepTimeUndoableBlocksWhereHeightIsLessThan(getChainHead().getHeight() - BlackcoinMagic.minimumStoreDepth);
		}

		if(this.chainHeadBlock==null){
			createNewStore(params);
		}
	}
	
	private void createNewStore(NetworkParameters params) throws BlockStoreException {
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
	
	private StoredBlock findVerifiedHead() {
		byte[] rawStoredBlock = this.settings.get(VERIFIED_CHAIN_HEAD_SETTING);
		if(rawStoredBlock!=null){
			return desera.buildStoredBlock(params, rawStoredBlock);
		}
		return null;
	}

	private StoredBlock findChainHead() {
		byte[] rawStoredBlock = this.settings.get(CHAIN_HEAD_SETTING);
		if(rawStoredBlock!=null){
			return desera.buildStoredBlock(params, rawStoredBlock);
		}
		return null;
	}

	@Override
	public void put(StoredBlock storedBlock) throws BlockStoreException {
		putUpdateStoredBlock(storedBlock, null, false);		
	}

	private void putUpdateStoredBlock(StoredBlock storedBlock, StoredUndoableBlock undoableBlock, boolean wasUndoable) {
		byte[] hashBytes = storedBlock.getHeader().getHash().getBytes();
		byte[] alreadyHave = this.headUndoable.get(hashBytes);
		BlackStored black = null;
		if(alreadyHave!=null){
			black = this.desera.buildBlackStored(alreadyHave);
			black.setWasUndoable(true);
		}else{
			if(undoableBlock==null){
				black = new BlackStored(desera.serialize(storedBlock), wasUndoable, new byte[0]);
			}else{
				black = new BlackStored(desera.serialize(storedBlock),wasUndoable, desera.serialize(undoableBlock));
				updatePrevBlock(storedBlock);
			}
		}
		this.headUndoable.put(hashBytes, desera.serialize(black));
	}

	@Override
	public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
		return get(hash, false);
	}

	private StoredBlock get(Sha256Hash hash, boolean wasUndoableOnly) {
		if (chainHeadHash != null && chainHeadHash.equals(hash))
            return chainHeadBlock;
        if (verifiedChainHeadHash != null && verifiedChainHeadHash.equals(hash))
            return verifiedChainHeadBlock;
        
		byte[] storedBytes = this.headUndoable.get(hash.getBytes());
		if(storedBytes==null){
			return null;
		}
		BlackStored storedBlack = this.desera.buildBlackStored(storedBytes);
		
		if (wasUndoableOnly && !storedBlack.wasUndoable())
            return null;
		
		return this.desera.buildStoredBlock(params, storedBlack.getStoredBytes());
	}

	@Override
	public StoredBlock getChainHead() throws BlockStoreException {
		return this.chainHeadBlock;
	}

	@Override
	public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
		this.chainHeadBlock = chainHead;
		this.chainHeadHash = chainHead.getHeader().getHash();
		this.settings.put(CHAIN_HEAD_SETTING, this.desera.serialize(chainHead));
	}

	@Override
	public void close() throws BlockStoreException {
		mvStore.close();
	}

	private void keepTimeUndoableBlocksWhereHeightIsLessThan(int minHeight) {
		if(this.lastPrunedHash == null){
			this.lastPrunedHash = new Sha256Hash(BlackcoinMagic.checkpoint0).getBytes();
		}
		
		int count = 0;
		Cursor<byte[], byte[]> notPrunnedHashes = this.headUndoable.cursor(this.lastPrunedHash);
		while(notPrunnedHashes.hasNext()){
			byte[] prunnedHashBytes = notPrunnedHashes.next();
			byte[] storedBytes = this.headUndoable.get(prunnedHashBytes);
			BlackStored blackStored = this.desera.buildBlackStored(storedBytes);
			StoredBlock storedBlock = this.desera.buildStoredBlock(params, blackStored.getStoredBytes());
			if(storedBlock.getHeight()!=0){
				byte[] blockTime = this.desera.serializeTime(storedBlock);
				if(storedBlock.getHeight() < minHeight){
					this.headUndoable.put(prunnedHashBytes, blockTime);
					this.settings.put(LAST_PRUNED_HASH, prunnedHashBytes);
					count++;
				}
			}
		}
		log.info("pruned " + count);
		
	}

	@Override
	public void put(StoredBlock storedBlock, StoredUndoableBlock undoableBlock)
			throws BlockStoreException {
		putUpdateStoredBlock(storedBlock, undoableBlock, true);
	}

	private void updatePrevBlock(StoredBlock storedBlock) {
		Sha256Hash nextHash = storedBlock.getHeader().getHash();
		Sha256Hash prevHash = storedBlock.getHeader().getPrevBlockHash();
		if(prevHash.equals(Sha256Hash.ZERO_HASH))
			return;
		byte[] prevHashBytes = prevHash.getBytes();
		byte[] blackBlock = this.headUndoable.get(prevHashBytes);
		
		if(blackBlock!=null&&blackBlock.length!=0){
			BlackStored storedBlack = this.desera.buildBlackStored(blackBlock);
			byte[] storedBytes = storedBlack.getStoredBytes();
			System.arraycopy(nextHash.getBytes(), 0, storedBytes, 0, nextHash.getBytes().length);			
			BlackStored updatedBlack = new BlackStored(storedBytes, storedBlack.wasUndoable(),
					storedBlack.getUndoableBytes());
			this.headUndoable.put(prevHashBytes, this.desera.serialize(updatedBlack));
		}
		
	}

	@Override
	public StoredBlock getOnceUndoableStoredBlock(Sha256Hash hash)
			throws BlockStoreException {
		return get(hash, true);
	}

	@Override
	public StoredUndoableBlock getUndoBlock(Sha256Hash hash)
			throws BlockStoreException {
		byte[] rawUndoable = this.headUndoable.get(hash.getBytes());
		BlackStored black = this.desera.buildBlackStored(rawUndoable);
		return this.desera.buildStoredUndoableBlock(hash, params, black.getUndoableBytes());
	}

	@Override
	public StoredTransactionOutput getTransactionOutput(Sha256Hash hash,
			long index) throws BlockStoreException {
		if(index==BlackcoinMagic.anyIndex){
			byte[] rawTx = getFirst(this.txOutputs, hash.toString());
			return this.desera.buildStoredTransactionOutput(hash, rawTx);
		}else{
			String key = hash.toString() + ":" + String.valueOf(index);
			byte[] storedOut = this.txOutputs.get(key);
			StoredTransactionOutput buildStoredTransactionOutput = this.desera.buildStoredTransactionOutput(hash, storedOut);
			if(buildStoredTransactionOutput.getTxTime() == 0)
				throw new BlockStoreException("probably Transaction time was forgotten to set !");
			return buildStoredTransactionOutput;
		}
		
	}

	@Override
	public void addUnspentTransactionOutput(StoredTransactionOutput out)
			throws BlockStoreException {
		String key = out.getHash().toString() + ":" + String.valueOf(out.getIndex());
		if(out.getTxTime() == 0)
			throw new BlockStoreException("probably Transaction time was forgotten to set !");
		this.txOutputs.put(key, this.desera.serialize(out));
	}

	@Override
	public void removeUnspentTransactionOutput(StoredTransactionOutput out)
			throws BlockStoreException {
		String key = out.getHash().toString() + ":" + String.valueOf(out.getIndex());
		this.txOutputs.remove(key);
	}

	@Override
	public boolean hasUnspentOutputs(Sha256Hash hash, int numOutputs)
			throws BlockStoreException {
		boolean cotainsHash = contains(this.txOutputs, hash.toString());
		if(cotainsHash)
			return true;
		return false;
	}
	
	private boolean contains(MVMap<String, byte[]> mvMap, String prefix) {
	    Cursor<String, byte[]> cursor = mvMap.cursor(prefix);
	    while (cursor.hasNext() && cursor.next().startsWith(prefix)) {
	        return true;
	    }
	    return false;
	}
	
	private byte[] getFirst(MVMap<String, byte[]> txOutputs, String prefix) {
		Cursor<String, byte[]> cursor = txOutputs.cursor(prefix);
	    while (cursor.hasNext() && cursor.next().startsWith(prefix)) {
	        return cursor.getValue();
	    }
	    return null;
	}
	

	@Override
	public StoredBlock getVerifiedChainHead() throws BlockStoreException {
		return verifiedChainHeadBlock;
	}

	@Override
	public void setVerifiedChainHead(StoredBlock chainHead)
			throws BlockStoreException {
		this.verifiedChainHeadBlock = chainHead;
		this.verifiedChainHeadHash = chainHead.getHeader().getHash();
		this.settings.put(VERIFIED_CHAIN_HEAD_SETTING, this.desera.serialize(chainHead));
		if (this.chainHeadBlock.getHeight() < chainHead.getHeight())
            setChainHead(chainHead);
	}

	@Override
	public void beginDatabaseBatchWrite() throws BlockStoreException {
	}

	@Override
	public void commitDatabaseBatchWrite() throws BlockStoreException {
	}

	@Override
	public void abortDatabaseBatchWrite() throws BlockStoreException {
	}

	
}
