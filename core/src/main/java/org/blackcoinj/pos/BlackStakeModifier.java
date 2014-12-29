package org.blackcoinj.pos;

import static org.bitcoinj.core.Utils.doubleDigest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlackStakeModifier {
	private BlockStore blockStore;
	private static final Logger log = LoggerFactory.getLogger(BlackStakeModifier.class);
	
	public BlackStakeModifier(BlockStore blockStore){
		this.blockStore = blockStore;
	}
	
	
	private void fillWithStakeVariables(StoredBlock prevBlock, Block newBlock) throws BlockStoreException {
    	// GetBlockTrust (const)
    	//setChainTrust(prevBlock, newBlock);
    	setStakeEntropyBit(newBlock);
    	setStakeModifier(prevBlock, newBlock);
    	//
	}
	
	private void setStakeEntropyBit(Block newBlock) {
		//unsigned int nEntropyBit = ((GetHash().Get64()) & 1llu);
		long lastBit = newBlock.getHash()
				.toBigInteger().and(BigInteger.ONE).longValue();
		newBlock.setEntropyBit(lastBit);
	}
	
	private void setStakeModifier(StoredBlock prevBlock, Block newBlock) throws BlockStoreException {
		newBlock.setStakeModifier(0l);
		newBlock.setGeneratedStakeModifier(false);
		StakeModifierValues stkModifier = getLastStakeModifier(prevBlock);
		newBlock.setStakeModifier(stkModifier.getStakeModifier());
		Block prevHeader = prevBlock.getHeader();
		//kernel.cpp#L145
		//nModifierTime / nModifierInterval >= pindexPrev->GetBlockTime() / nModifierInterval
		if (stkModifier.getStakeTime() /  BlackcoinMagic.modifierInterval >= prevHeader.getTimeSeconds() /  BlackcoinMagic.modifierInterval){
			return;
		}
	    //https://github.com/rat4/blackcoin/blob/a2e518d59d8cded7c3e0acf1f4a0d9b363b46346/src/kernel.cpp#L146
		long selectionInterval = Utils.getStakeModifierSelectionInterval();
		//int64_t nSelectionIntervalStart = (pindexPrev->GetBlockTime() / nModifierInterval) * nModifierInterval - nSelectionInterval;    
		long selectionIntervalStart = (prevHeader.getTimeSeconds() / BlackcoinMagic.modifierInterval) 
	    		* BlackcoinMagic.modifierInterval - selectionInterval;
			   
	    Map<Sha256Hash, Long> sortedByTimestamp = getBlockTimeSortedMap(selectionIntervalStart, prevBlock);
	    
	    long stakeModifierNew = 0l;
	    //log.info("prev modifier " + Long.toHexString(stkModifier.getStakeModifier()) + " time="+formatDateToUTC(stkModifier.getStakeTime()));
	    //log.info(" selectionIntervalStart " + selectionIntervalStart);
	    //int64_t nSelectionIntervalStop = nSelectionIntervalStart;
	    List<Sha256Hash> alreadySelectedBlocks = new ArrayList<Sha256Hash>();
	    for (int round=0; round<Math.min(64, sortedByTimestamp.size()); round++){
	    	// add an interval section to the current selection round
	    	selectionIntervalStart += Utils.getStakeModifierSelectionIntervalSection(round);
	        // select a block from the candidates of current round
	    	StoredBlock selectedBlock = selectBlockFromCandidates(sortedByTimestamp, alreadySelectedBlocks, 
	    			selectionIntervalStart,stkModifier.getStakeModifier());
	        if (selectedBlock == null){
	        	throw new RuntimeException("unable to select block");
	        }
	        // write the entropy bit of the selected block
	        //nStakeModifierNew |= (((uint64_t)pindex->GetStakeEntropyBit()) << nRound);
	        stakeModifierNew |= ((selectedBlock.getHeader().getEntropyBit()) << round);
	        // add the selected block from candidates to selected list
	        //mapSelectedBlocks.insert(make_pair(pindex->GetBlockHash(), pindex));
	        alreadySelectedBlocks.add(selectedBlock.getHeader().getHash());
	        //logSelectedCandidates(selectionIntervalStart, selectedBlock, round);
	    }
	    //log.info("height " + newBlock.getHeight() + " stakeModifier: " + Long.toHexString(stakeModifierNew));
	    newBlock.setStakeModifier(stakeModifierNew);
	    newBlock.setGeneratedStakeModifier(true);
	}

	private StoredBlock selectBlockFromCandidates(
			Map<Sha256Hash, Long> sortedByTimestamp,List<Sha256Hash> mapSelectedBlocks, 
			long selectionIntervalStop, long stakeModifier) throws BlockStoreException {

		boolean fSelected = false;
		BigInteger hashBest = BigInteger.ZERO;
		StoredBlock pindexSelected = null;
		for (Map.Entry<Sha256Hash, Long> timestampBlocks : sortedByTimestamp.entrySet()) {
			StoredBlock pindex = blockStore.get(timestampBlocks.getKey());
			if(pindex == null)
				throw new RuntimeException("block not found ");
			if(fSelected && pindex.getHeader().getTimeSeconds() > selectionIntervalStop)
				break;
			if (mapSelectedBlocks.contains(timestampBlocks.getKey()))
				continue;
			
			// CDataStream ss(SER_GETHASH, 0);
			//ss << pindex->hashProof << nStakeModifierPrev;
			Sha256Hash hashProof = pindex.getHeader().isStake()? pindex.getHeader().getStakeHashProof() : pindex.getHeader().getHash(); 
			ByteArrayOutputStream ssStakeStream = fillStakeStream(hashProof, stakeModifier);
			Sha256Hash sha256HashSelection = new Sha256Hash(Utils.reverseBytes(doubleDigest(ssStakeStream.toByteArray())));
			BigInteger hashSelection = sha256HashSelection.toBigInteger();
			
			if (pindex.getHeader().isStake()){
				//hashSelection >>= 32;
				//hashSelection = hashSelection >> 32 
				hashSelection = hashSelection.shiftRight(32);
			}
			
			//fSelected &&  hashSelection < hashBest
			if (fSelected && hashSelection.compareTo(hashBest) < 0){
				hashBest = hashSelection;
				pindexSelected = pindex;
			}
			
			else if (!fSelected){
	            fSelected = true;
	            hashBest = hashSelection;
	            pindexSelected = pindex;
	        }
			
		}
		return pindexSelected;
	}

	private ByteArrayOutputStream fillStakeStream(Sha256Hash sha256Hash, long stakeModifier) {
		byte[] arHashProof = sha256Hash.getBytes();
		byte[] arStakeMod = new byte[8];
		Utils.uint64ToByteArrayLE(stakeModifier, arStakeMod , 0);
		//256 = 32, 64 = 8
		ByteArrayOutputStream ssStakeStream = new UnsafeByteArrayOutputStream(32 + 8);
		try {
			ssStakeStream.write(Utils.reverseBytes(arHashProof));
			ssStakeStream.write(arStakeMod);
		} catch (IOException e) {
			e.printStackTrace();
		} finally{
			try {
				ssStakeStream.close();
			} catch (IOException e) {
				throw new RuntimeException("Couldn't close the stream");
			}
		}
		return ssStakeStream;
	}

//	private void logSelectedCandidates(long selectionIntervalStop,
//			StoredBlock pindexSelected, int round) {		
//		log.info("round " + round + " stop "+formatDateToUTC(selectionIntervalStop)+" height="+pindexSelected.getHeight()+ " bit=" + pindexSelected.getEntropyBit());
//	}
	
//	private String formatDateToUTC(long timeInSeconds){
//		Date dateTime = new Date(timeInSeconds*1000);
//		DateFormat converter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//		converter.setTimeZone(TimeZone.getTimeZone("GMT"));
//		return converter.format(dateTime);
//	}

	private Map<Sha256Hash, Long> getBlockTimeSortedMap(long selectionIntervalStop, StoredBlock prevBlock) throws BlockStoreException {
		Map<Sha256Hash, Long> outMap = new HashMap<Sha256Hash, Long>();
		while(prevBlock != null && prevBlock.getHeader().getTimeSeconds()  >= selectionIntervalStop){
			outMap.put(prevBlock.getHeader().getHash(), prevBlock.getHeader().getTimeSeconds());
			prevBlock  = prevBlock.getPrev(blockStore);
		}
		Map<Sha256Hash, Long> out_sorted_map = sortByValues(outMap);
		return out_sorted_map;
	}

	private Map<Sha256Hash, Long> sortByValues(Map<Sha256Hash, Long> outMap) {
	        List<Map.Entry<Sha256Hash,Long>> entries = new LinkedList<Map.Entry<Sha256Hash,Long>>(outMap.entrySet());
	     
	        Collections.sort(entries, new Comparator<Map.Entry<Sha256Hash, Long>>() {

	        	@Override
	            public int compare(Entry<Sha256Hash, Long> o1, Entry<Sha256Hash, Long> o2) {
	            	if(o1.getValue().compareTo(o2.getValue()) == 0){
	            		return o1.getKey().toBigInteger().compareTo(o2.getKey().toBigInteger());
	            	}
	                return o1.getValue().compareTo(o2.getValue());
	            }
	        });
	     
	        //LinkedHashMap will keep the keys in the order they are inserted
	        //which is currently sorted on natural ordering
	        Map<Sha256Hash, Long> sortedMap = new LinkedHashMap<Sha256Hash, Long>();
	     
	        for(Map.Entry<Sha256Hash, Long> entry: entries){
	            sortedMap.put(entry.getKey(), entry.getValue());
	        }
	     
	        return sortedMap;
	    }

	

	private StakeModifierValues getLastStakeModifier(StoredBlock prevBlock) throws BlockStoreException {
		StakeModifierValues outStakeModifier = new StakeModifierValues();
		Block theBlock = prevBlock.getHeader();
		if(theBlock.getPrevBlockHash().equals(Sha256Hash.ZERO_HASH)){
			outStakeModifier.setStakeTime(theBlock.getTimeSeconds());
			outStakeModifier.setStakeModifier(0l);
		}else{
			while(!prevBlock.getHeader().isGeneratedStakeModifier()){
				prevBlock = blockStore.get(prevBlock.getHeader().getPrevBlockHash());
			}
			theBlock = prevBlock.getHeader();
			outStakeModifier.setStakeTime(theBlock.getTimeSeconds());
			outStakeModifier.setStakeModifier(theBlock.getStakeModifier());
		}
		return outStakeModifier;
	}

	public void setBlackCoinStake(StoredBlock storedPrev, Block newBlock) throws BlockStoreException {
		fillWithStakeVariables(storedPrev, newBlock);
	}
}
