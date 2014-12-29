/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.params;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.blackcoinj.pos.BlackcoinMagic;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends NetworkParameters {
    public MainNetParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = BlackcoinMagic.proofOfWorkLimit;
        dumpedPrivateKeyHeader = BlackcoinMagic.bulgarianConst + BlackcoinMagic.addressHeader;
        addressHeader = BlackcoinMagic.addressHeader;
        p2shHeader = BlackcoinMagic.p2shHeader;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        port = BlackcoinMagic.port;
        packetMagic = BlackcoinMagic.packetMagic;
        genesisBlock.setDifficultyTarget(BlackcoinMagic.genesisDifficultyTarget);
        genesisBlock.setTime(BlackcoinMagic.time);
        genesisBlock.setNonce(BlackcoinMagic.nonce);
        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210000;
        spendableCoinbaseDepth = BlackcoinMagic.spendableCoinbaseDepth;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals(BlackcoinMagic.checkpoint0),
                genesisHash);

        // This contains (at a minimum) the blocks which are not BIP30 compliant. BIP30 changed how duplicate
        // transactions are handled. Duplicated transactions could occur in the case where a coinbase had the same
        // extraNonce and the same outputs but appeared at different heights, and greatly complicated re-org handling.
        // Having these here simplifies block connection logic considerably.
        checkpoints.put(0, new Sha256Hash(BlackcoinMagic.checkpoint0));
        checkpoints.put(1500, new Sha256Hash(BlackcoinMagic.checkpoint1));
        checkpoints.put(5001, new Sha256Hash(BlackcoinMagic.checkpoint2));
        checkpoints.put(5500, new Sha256Hash(BlackcoinMagic.checkpoint3));
        checkpoints.put(10000, new Sha256Hash(BlackcoinMagic.checkpoint4));
        checkpoints.put(14000, new Sha256Hash(BlackcoinMagic.checkpoint5));
        checkpoints.put(37000, new Sha256Hash(BlackcoinMagic.checkpoint6));        
        checkpoints.put(38424, new Sha256Hash(BlackcoinMagic.checkpoint7));
        checkpoints.put(38425, new Sha256Hash(BlackcoinMagic.checkpoint8));
        checkpoints.put(61100, new Sha256Hash(BlackcoinMagic.checkpoint9));
        checkpoints.put(80000, new Sha256Hash(BlackcoinMagic.checkpoint10));
        checkpoints.put(254348, new Sha256Hash(BlackcoinMagic.checkpoint11));
        checkpoints.put(319002, new Sha256Hash(BlackcoinMagic.checkpoint12));

        dnsSeeds = new String[] {
        		BlackcoinMagic.dnsSeed0,       
        		BlackcoinMagic.dnsSeed1,  
        		BlackcoinMagic.dnsSeed2,
        		BlackcoinMagic.dnsSeed3,
        		BlackcoinMagic.dnsSeed4,
        		BlackcoinMagic.dnsSeed5,
        };
    }

    private static MainNetParams instance;
    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
