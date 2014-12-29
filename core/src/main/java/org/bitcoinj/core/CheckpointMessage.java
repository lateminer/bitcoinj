package org.bitcoinj.core;

import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;

public class CheckpointMessage extends Message{

	private long version;
	private Sha256Hash checkpointHash;

	public CheckpointMessage(NetworkParameters params, byte[] payloadBytes) {
		 super(params, payloadBytes, 0);
	}

	@Override
	void parse() throws ProtocolException {
		setVersion(readUint32());
		setCheckpointHash(readHash());
		//signature???
		length = cursor;
		
	}

	@Override
	protected void parseLite() throws ProtocolException {
		setVersion(readUint32());
		setCheckpointHash(readHash());
		//signature???
		length = cursor;
	}

	public Sha256Hash getCheckpointHash() {
		return checkpointHash;
	}

	public void setCheckpointHash(Sha256Hash checkpointHash) {
		this.checkpointHash = checkpointHash;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

}
