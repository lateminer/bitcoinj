package org.blackcoinj.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.StoredTransactionOutput;
import org.bitcoinj.core.StoredUndoableBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutputChanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class BlackDes {

	private Logger log  = LoggerFactory.getLogger(BlackDes.class);

	public BlackStored buildBlackStored(byte[] payload) {
		if(payload.length > 8){
			ByteBuffer bytes = ByteBuffer.wrap(payload);
			boolean wasUndoable = bytes.get() == 1? true:false;
			byte[] storedBytes = new byte[bytes.getInt()];
			bytes.get(storedBytes);
			byte[] undoableBytes = new byte[bytes.getInt()];
			bytes.get(undoableBytes);			
			return new BlackStored(storedBytes, wasUndoable, undoableBytes);
		}else{
			return new BlackStored(payload, false, null);
		}
				
	}

	

	public StoredBlock buildStoredBlock(NetworkParameters params, byte[] payload) {
		if(payload.length>8){
			ByteBuffer bytes = ByteBuffer.wrap(payload);
			byte[] nextBytes = new byte[32];
			bytes.get(nextBytes);
			
			byte[] headerBytes = new byte[80];
			bytes.get(headerBytes);
			Block block = new Block(params, headerBytes);
			int height = bytes.getInt();
			long stakeModifier = bytes.getLong();
			long stakeTime = bytes.getLong();
			long entropyBit = (long) bytes.get();
			boolean generatedStake = bytes.get()==(byte)1? true: false;
			boolean isStake = bytes.get()==(byte)1? true: false;
			byte[] hashProofBytes = new byte[32];
			bytes.get(hashProofBytes);
			Sha256Hash next = new Sha256Hash(nextBytes);
			block.setNextBlockHash(next);
			block.setStakeHashProof(new Sha256Hash(hashProofBytes));
			long timeBlock = block.getTimeSeconds();
			byte[] chainwork = new byte[bytes.remaining()];
			bytes.get(chainwork);       
			block.setStakeModifier(stakeModifier);
			block.setEntropyBit(entropyBit);
			block.setStake(isStake);
			block.setGeneratedStakeModifier(generatedStake);
			block.setStakeTime(stakeTime);
	        StoredBlock storedBlock = new StoredBlock(block, new BigInteger(chainwork), height);
	        
	        storedBlock.setTimeBlock(timeBlock);
	        return storedBlock;
		}else{
			return buildStoredBlockTime(payload);
		}
				
	}
	
	public byte[] serializeTime(StoredBlock storedBlock) {
		long blockTime = storedBlock.getHeader().getTimeSeconds();
		ByteBuffer bytes = ByteBuffer.allocate(8);
		bytes.putLong(blockTime);
		return bytes.array();
	}

	public byte[] serialize(StoredBlock storedBlock) {
        byte[] chainBytes = storedBlock.getChainWork().toByteArray();        
        
        ByteBuffer bytes = ByteBuffer.allocate(32 + 80 + 4 + 8 + 8 + 1 + 1 + 1 + 32 + chainBytes.length);
        Block storedHeader = storedBlock.getHeader();
        if(storedHeader.getNextBlockHash()!=null){
        	bytes.put(storedHeader.getNextBlockHash().getBytes());
        }else{
        	bytes.put(Sha256Hash.ZERO_HASH.getBytes());
        }
        byte[] unsafeBitcoinSerialize = storedHeader.unsafeBitcoinSerialize();
        bytes.put(unsafeBitcoinSerialize, 0, Block.HEADER_SIZE); // Trim the trailing 00 byte (zero transactions).
		bytes.putInt(storedBlock.getHeight());
		bytes.putLong(storedHeader.getStakeModifier());
		bytes.putLong(storedHeader.getStakeTime());
		bytes.put(storedHeader.getEntropyBit()==1? (byte)1:(byte)0);
		bytes.put(storedHeader.isGeneratedStakeModifier()? (byte)1:(byte)0);
		bytes.put(storedHeader.isStake()? (byte)1:(byte)0);		
		bytes.put(storedHeader.isStake()?
				storedBlock.getHeader().getStakeHashProof().getBytes():Sha256Hash.ZERO_HASH.getBytes());

		bytes.put(chainBytes);
		return bytes.array();
	}

	public byte[] serialize(BlackStored black) {
		byte[] storedBytes = black.getStoredBytes();
		byte[] undoableBytes = black.getUndoableBytes();
		ByteBuffer bytes = ByteBuffer.allocate(1 + 4 + storedBytes.length + 4 + undoableBytes.length);
		bytes.put(black.wasUndoable()?(byte)1:(byte)0);
		bytes.putInt(storedBytes.length);
		bytes.put(storedBytes);
		bytes.putInt(undoableBytes.length);
		bytes.put(undoableBytes);
		return bytes.array();
	}

	public byte[] serialize(StoredUndoableBlock undoableBlock) {
		byte[] transactions = null;
        byte[] txOutChanges = null;
        int numTxn = 0;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (undoableBlock.getTxOutChanges() != null) {
                undoableBlock.getTxOutChanges().serializeToStream(bos);
                txOutChanges = bos.toByteArray();
            } else {
            	numTxn = undoableBlock.getTransactions().size();
                for (Transaction tx : undoableBlock.getTransactions())
                    tx.bitcoinSerialize(bos);
                transactions = bos.toByteArray();
            }
            bos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ByteBuffer bytes;
        if(transactions!=null){
        	bytes = ByteBuffer.allocate(1 + 4 + transactions.length);   		
        	bytes.put((byte)1);
        	bytes.putInt(numTxn);
        	bytes.put(transactions);
        }else{
        	bytes = ByteBuffer.allocate(1 + txOutChanges.length);       	
        	bytes.put((byte)0);
        	bytes.put(txOutChanges);
        }
        return bytes.array();
	}

	public StoredUndoableBlock buildStoredUndoableBlock(Sha256Hash hash, NetworkParameters params, byte[] payload) {
		ByteBuffer bytes = ByteBuffer.wrap(payload);
		byte txOut = bytes.get();
		StoredUndoableBlock block;
		if(txOut == 1){
			int offset = 0;
            int numTxn = bytes.getInt();          
            byte[] transactions = new byte[bytes.remaining()];
            bytes.get(transactions);
            
            List<Transaction> transactionList = new LinkedList<Transaction>();
            for (int i = 0; i < numTxn; i++) {               
				Transaction tx = new Transaction(params, transactions, offset);
                transactionList.add(tx);
                offset += tx.getMessageSize();
            }
            block = new StoredUndoableBlock(hash, transactionList);
		}else{
			byte[] txOutChanges = new byte[bytes.remaining()];
            bytes.get(txOutChanges);
			TransactionOutputChanges outChangesObject;
			try {
				outChangesObject = new TransactionOutputChanges(new ByteArrayInputStream(txOutChanges));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
            block = new StoredUndoableBlock(hash, outChangesObject);
		}
        return block;
		
	}

	public StoredTransactionOutput buildStoredTransactionOutput(Sha256Hash hash, byte[] payload) {
		
		ByteBuffer bytes = ByteBuffer.wrap(payload);
		int index = bytes.getInt();
		int height = bytes.getInt();
		long value = bytes.getLong();
		long txTime = bytes.getLong();
		long txOffset = bytes.getLong();
		
		byte[] rawBlock = new byte[32];
		bytes.get(rawBlock);
		byte[] scriptBytes = new byte[bytes.remaining()];
		bytes.get(scriptBytes);

		return new StoredTransactionOutput(hash,index, Coin.valueOf(value), height, true, scriptBytes, 
        									new Sha256Hash(rawBlock), txTime, txOffset);
		
	}

	public byte[] serialize(StoredTransactionOutput txOut) {
		byte[] scriptBytes = txOut.getScriptBytes();
		ByteBuffer bytes = ByteBuffer.allocate(4 + 4 + 8 + 8 + 8 + 32 + scriptBytes.length);
		bytes.putInt((int)txOut.getIndex());
		bytes.putInt(txOut.getHeight());
		bytes.putLong(txOut.getValue().value);
		bytes.putLong(txOut.getTxTime());
		bytes.putLong(txOut.getTxOffsetInBlock());
		
		bytes.put(txOut.getHashBlock().getBytes());
		bytes.put(scriptBytes);
		return bytes.array();
	}

	public StoredBlock buildStoredBlockTime(byte[] payload) {
		ByteBuffer bytes = ByteBuffer.wrap(payload);
		long timeBlock = bytes.getLong();
		return new StoredBlock(timeBlock);
	}
}
