package org.blackcoinj.store;

import java.io.Serializable;
import java.util.Arrays;

public class BlackStored implements Serializable{
	
	private static final long serialVersionUID = -7011059126487895056L;
	private boolean wasUndoable;
	private byte[] storedBytes;
	private byte[] undoableBytes;
	
	public BlackStored(byte[] storedBytes, boolean wasUndoable, byte[] undoableBytes) {
		this.storedBytes = storedBytes;
		this.wasUndoable = wasUndoable;
		this.undoableBytes = undoableBytes;
	}
	public boolean wasUndoable() {
		return wasUndoable;
	}
	public void setWasUndoable(boolean wasUndoable) {
		this.wasUndoable = wasUndoable;
	}
	
	public byte[] getStoredBytes() {
		return storedBytes;
	}
	public void setStoredBytes(byte[] storedBytes) {
		this.storedBytes = storedBytes;
	}
	public byte[] getUndoableBytes() {
		return undoableBytes;
	}
	
	public void setUndoableBytes(byte[] undoableBytes) {
		this.undoableBytes = undoableBytes;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(storedBytes);
		result = prime * result + Arrays.hashCode(undoableBytes);
		result = prime * result + (wasUndoable ? 1231 : 1237);
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BlackStored other = (BlackStored) obj;
		if (!Arrays.equals(storedBytes, other.storedBytes))
			return false;
		if (!Arrays.equals(undoableBytes, other.undoableBytes))
			return false;
		if (wasUndoable != other.wasUndoable)
			return false;
		return true;
	}
	
	
}
