package org.blackcoinj.pos;

import static org.bitcoinj.core.Utils.doubleDigest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.StoredTransactionOutput;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;

public class BlackcoinPOS {
	private static final Logger log = LoggerFactory.getLogger(BlackcoinPOS.class);
	private FullPrunedBlockStore blockStore;
	
	public BlackcoinPOS(FullPrunedBlockStore blockStore) {
    	this.blockStore = blockStore;
    }	
	
	public Sha256Hash checkAndSetPOS(StoredBlock storedPrev, Block newBlock) throws BlockStoreException {
		return checkSetBlackCoinPOS(storedPrev , newBlock);
	}
	
	private Sha256Hash checkSetBlackCoinPOS(StoredBlock storedPrev, Block block) throws BlockStoreException {   	
    	if(block.isStake()){
    		//log.info("checkinng proof of stake");
			List<Transaction> transactions = block.getTransactions();
			//CheckProofOfStake(pindexPrev, vtx[1], nBits, hashProof, targetProofOfStake
			Sha256Hash stakeKernelHash = checkProofOfStake(storedPrev, transactions.get(1), block.getDifficultyTarget());
			if (stakeKernelHash == null){
				throw new VerificationException("The proof-of-stake failed");
			}else{
				return stakeKernelHash;
				
				//log.info("setting stake proof on height " + newBlock.getHeight());
				//log.info("stake proof=" + stakeKernelHash);
			}
		}
		return null;
		
	}

    private Sha256Hash checkProofOfStake(StoredBlock storedPrev, Transaction stakeTx,
			long target) throws BlockStoreException {
    	// Kernel (input 0) must match the stake hash target per coin age (nBits)
		TransactionInput txin = stakeTx.getInputs().get(0);
		//https://github.com/rat4/blackcoin/blob/a2e518d59d8cded7c3e0acf1f4a0d9b363b46346/src/kernel.cpp#L427
		// First try finding the previous transaction in database
		StoredTransactionOutput txPrev = blockStore.getTransactionOutput(txin.getOutpoint().getHash(), BlackcoinMagic.anyIndex);
		StoredBlock txBlock = blockStore.get(txPrev.getHashBlock());
		//CheckStakeKernelHash(pindexPrev, nBits, block, txindex.pos.nTxPos - txindex.pos.nBlockPos, txPrev, 
		//txin.prevout, tx.nTime, hashProofOfStake, targetProofOfStake, fDebug))
		Sha256Hash stakeKernelHash = checkStakeKernelHash(storedPrev, target, txPrev, txBlock, stakeTx.getnTime(),txin.getOutpoint());
		if (stakeKernelHash==null)
			throw new VerificationException("Check kernel failed");
		return stakeKernelHash;
	}
    


	private Sha256Hash checkStakeKernelHash(StoredBlock storedPrev, long target, StoredTransactionOutput txPrev, StoredBlock blockFrom, long stakeTxTime, TransactionOutPoint prevout) throws BlockStoreException {
		if (storedPrev.getHeight() + 1 > BlackcoinMagic.secondForkHeight)
			return checkStakeKernelHashV2(storedPrev, target, txPrev, blockFrom.getTimeBlock(), stakeTxTime, prevout);
		else
			return checkStakeKernelHashV1(storedPrev, target, txPrev, blockFrom, stakeTxTime, prevout);
	}

	private Sha256Hash checkStakeKernelHashV1(StoredBlock storedPrev, long target, StoredTransactionOutput txPrev, StoredBlock storedBlockFrom, long stakeTxTime, TransactionOutPoint prevout) throws BlockStoreException {
		// https://github.com/rat4/blackcoin/blob/a2e518d59d8cded7c3e0acf1f4a0d9b363b46346/src/kernel.cpp#L269
		// nTimeTx < txPrev.nTime
		if(stakeTxTime < txPrev.getTxTime())
			throw new VerificationException("Time violation");
		
		// Min age requirement
		Block blockFrom = storedBlockFrom.getHeader();
		long timeBlockFrom = blockFrom.getTimeSeconds();
		if (timeBlockFrom + BlackcoinMagic.stakeMinAge > stakeTxTime)
			throw new VerificationException("Min age violation");
		
		// https://github.com/rat4/blackcoin/blob/a2e518d59d8cded7c3e0acf1f4a0d9b363b46346/src/kernel.cpp#L278
		StoredTransactionOutput prevOut = blockStore.getTransactionOutput(txPrev.getHash(),prevout.getIndex());
		long valueIn = prevOut.getValue().getValue();
		//CBigNum bnCoinDayWeight = CBigNum(nValueIn) * GetWeight((int64_t)txPrev.nTime, (int64_t)nTimeTx)
		//log.info("valueIn " + valueIn);
		//log.info("getWeight " + getWeight(txPrev.getTxTime(), stakeTxTime));
		BigInteger targetPerCoinDay = Utils.decodeCompactBits(target);
		BigInteger coinDayWeight = BigInteger.valueOf(valueIn).multiply(BigInteger.valueOf(getWeight(txPrev.getTxTime(), stakeTxTime)))
				.divide(BigInteger.valueOf(BlackcoinMagic.nCoin)).divide(BigInteger.valueOf(24 * 60 * 60));
		// / COIN / (24 * 60 * 60);
		
		Sha256Hash hashProofOfStake = Sha256Hash.ZERO_HASH;
		long stakeModifier = getKernelStakeModifier(storedBlockFrom, 0l);
		if(stakeModifier == 0l)
			return null;
		
		byte[] arStakeMod = new byte[8];
		Utils.uint64ToByteArrayLE(stakeModifier, arStakeMod , 0);
		ByteArrayOutputStream ssStakeStream = new UnsafeByteArrayOutputStream(8 + 4 + 4 + 4 + 4 + 4);
		try {
			//ss << nStakeModifier;
			ssStakeStream.write(arStakeMod);
			//nTimeBlockFrom
			Utils.uint32ToByteStreamLE(timeBlockFrom, ssStakeStream);
			//nTxPrevOffset
			Utils.uint32ToByteStreamLE(txPrev.getTxOffsetInBlock(), ssStakeStream);
			//txPrev.nTime
			Utils.uint32ToByteStreamLE(txPrev.getTxTime(), ssStakeStream);
			//prevout.n
			Utils.uint32ToByteStreamLE(prevout.getIndex(), ssStakeStream);
			//nTimeTx
			Utils.uint32ToByteStreamLE(stakeTxTime, ssStakeStream);
			//hashProofOfStake = Hash(ss.begin(), ss.end());
			hashProofOfStake = new Sha256Hash(Utils.reverseBytes(doubleDigest(ssStakeStream.toByteArray())));
		} catch (IOException e) {
			throw new VerificationException("creating hash in checkStakeKernelHashV1 failed");
		}
		
		// Now check if proof-of-stake hash meets target protocol
		
		BigInteger bigNumHashProofOfStake = hashProofOfStake.toBigInteger();
		//CBigNum(hashProofOfStake) > bnCoinDayWeight * bnTargetPerCoinDay
		BigInteger targetPerCoinWeight = coinDayWeight.multiply(targetPerCoinDay);		
		if (bigNumHashProofOfStake.compareTo(targetPerCoinWeight) > 0){
			log.info(" hashProofOfStake not as big ");
			return null;
		}
		
		return hashProofOfStake;
	}
	
	private Sha256Hash checkStakeKernelHashV2(StoredBlock storedPrev, long target, StoredTransactionOutput txPrev, long timeBlockFrom, long stakeTxTime, TransactionOutPoint prevout) throws BlockStoreException {
		// nTimeTx < txPrev.nTime
		if(stakeTxTime < txPrev.getTxTime())
			throw new VerificationException("Time violation");

		// Min age requirement
		if (timeBlockFrom + BlackcoinMagic.stakeMinAge > stakeTxTime)
			throw new VerificationException("Min age violation");
		
		// Base target
		StoredTransactionOutput prevOut = blockStore.getTransactionOutput(txPrev.getHash(),prevout.getIndex());
		// Weighted target
		long weight = prevOut.getValue().getValue();
		// bnTarget *= bnWeight;
		BigInteger targetPerCoinDay = Utils.decodeCompactBits(target).multiply(BigInteger.valueOf(weight));
		Sha256Hash hashProofOfStake = Sha256Hash.ZERO_HASH;
		long stakeModifier = storedPrev.getHeader().getStakeModifier();
		byte[] arrayHashPrevout = prevout.getHash().getBytes();
		byte[] arStakeMod = new byte[8];
		Utils.uint64ToByteArrayLE(stakeModifier, arStakeMod , 0);
		ByteArrayOutputStream ssStakeStream = new UnsafeByteArrayOutputStream(8 + 4 + 4 + 32 + 4 + 4);
		try {
			//ss << nStakeModifier;
			ssStakeStream.write(arStakeMod);
			//nTimeBlockFrom
			Utils.uint32ToByteStreamLE(timeBlockFrom, ssStakeStream);
			//txPrev.nTime
			Utils.uint32ToByteStreamLE(txPrev.getTxTime(), ssStakeStream);
			//prevout.hash
			ssStakeStream.write(Utils.reverseBytes(arrayHashPrevout));
			//prevout.n
			Utils.uint32ToByteStreamLE(prevout.getIndex(), ssStakeStream);
			//nTimeTx
			Utils.uint32ToByteStreamLE(stakeTxTime, ssStakeStream);
			hashProofOfStake = new Sha256Hash(Utils.reverseBytes(doubleDigest(ssStakeStream.toByteArray())));
		} catch (IOException e) {
			throw new VerificationException("creating hash in checkStakeKernelHashV2 failed");
		}
		
		if(storedPrev.getHeight()==330000){
			log.info("stakeModifier " + Long.toHexString(stakeModifier));
			log.info("timeBlockFrom " + timeBlockFrom);
			log.info("txPrev.getTxTime() " + txPrev.getTxTime());
			log.info("is reversed prevout.getHash() " + prevout.getHash());
			log.info("prevout.getIndex() " + prevout.getIndex());
			log.info("stakeTxTime " + stakeTxTime);
			log.info("hashProofOfStake " +hashProofOfStake.toString());
		}
		
		BigInteger bigNumHashProofOfStake = hashProofOfStake.toBigInteger();		
		if(bigNumHashProofOfStake.compareTo(targetPerCoinDay) > 0){
			log.info("height: " + storedPrev.getHeight());
			log.info("hash " + storedPrev.getHeader().getHash().toString());
			log.info("stakeModifier " + Long.toHexString(stakeModifier));
			log.info("timeBlockFrom " + timeBlockFrom);
			log.info("txPrev.getTxTime() " + txPrev.getTxTime());
			log.info("is not reversed prevout.getHash() " + prevout.getHash());
			log.info("prevout.getIndex() " + prevout.getIndex());
			log.info("stakeTxTime " + stakeTxTime);
			log.info("hashProofOfStake " +hashProofOfStake.toString());
			return null;
		}
		return hashProofOfStake;
	}
	
	private long getWeight(long intervalBeginning, long intervalEnd) {
		return intervalEnd - intervalBeginning - BlackcoinMagic.stakeMinAge;
	}

	
	
	// The stake modifier used to hash for a stake kernel is chosen as the stake
		// modifier about a selection interval later than the coin generating the kernel
		private long getKernelStakeModifier(StoredBlock blockFrom, long stakeModifier) throws BlockStoreException {
			// https://github.com/rat4/blackcoin/blob/f93e74f5786daabb784bbee39be997842e5a718f/src/kernel.cpp#L216
			Block blockFromHeader = blockFrom.getHeader();
			long stakeModifierTime = blockFromHeader.getTimeSeconds();
			long stakeModifierSelectionInterval = Utils.getStakeModifierSelectionInterval();
			// loop to find the stake modifier later by a selection interval
			StoredBlock pindex = blockFrom;
			while (stakeModifierTime < blockFromHeader.getTimeSeconds() + stakeModifierSelectionInterval){
				if(pindex.getHeader().getNextBlockHash()==null){
					// reached best block; may happen if node is behind on block chain
					if(pindex.getHeader().getTimeSeconds() + BlackcoinMagic.stakeMinAge 
							- stakeModifierSelectionInterval > Utils.currentTimeSeconds()){
						throw new VerificationException("Reached best block");
					}else{
						log.info("getKernelStakeModifier false ");
						return 0l;
					}
				}
				pindex = blockStore.get(pindex.getHeader().getNextBlockHash());
				
				if(pindex.getHeader().isGeneratedStakeModifier()){
					stakeModifierTime = pindex.getHeader().getTimeSeconds();
				}
			}
			//log.info("stakeModifier " + Long.toHexString(pindex.getStakeModifier()));
			//log.info("height : " + pindex.getHeight());
			return pindex.getHeader().getStakeModifier();
		}
}
