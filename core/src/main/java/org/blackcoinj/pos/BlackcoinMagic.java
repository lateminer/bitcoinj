package org.blackcoinj.pos;

import java.math.BigInteger;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;

public class BlackcoinMagic {
	public static final String FULLPRUNEDCHAIN_RESOURCE = "blackchain";
	
	///main.cpp#L2521 nBits=1e0fffff
	public static final long genesisDifficultyTarget = (0x1e0fffffL);
	//main.cpp#L39 bnProofOfWorkLimit (~uint256(0) >> 20);
	public static final BigInteger proofOfWorkLimit =   Utils.decodeCompactBits(genesisDifficultyTarget);
	public static final BigInteger proofOfWorkLimitV2 = Utils.decodeCompactBits(0x1b00ffffL);
	//base58.h#L279 PUBKEY_ADDRESS = 25
	public static final int addressHeader = 25;
	//common to all coins
	public static final int bulgarianConst = 128;
	//base58.h#L280 SCRIPT_ADDRESS = 85
	public static final int p2shHeader = 85;
	//protocol.h#L21 return testnet ? 25714 : 15714;
	public static final int port = 15714;
	//main.cpp#L2822 pchMessageStart[4] = { 0x70, 0x35, 0x22, 0x05 };
	public static final long packetMagic = 0x70352205L;
	//clientmodel.cpp#L56 fromTime_t(1393221600); // Genesis block's time
	public static final long time = 1393221600;
	//main.cpp#L2521 nNonce=164482
	public static final long nonce = 164482;
	// don't know
	public static final String ID_MAINNET = "org.blackcoin.production";
	//main.cpp#L48 nCoinbaseMaturity = 500
	public static final int spendableCoinbaseDepth = 500;
	public static final String checkpoint0 = "000001faef25dec4fbcf906e6242621df2c183bf232f263d0ba5b101911e4563";
	public static final String checkpoint1 = "000000000079987db951032e017b8e337016296bffdb83ae87dd7fc79c26668d";
	public static final String checkpoint2 = "2fac9021be0c311e7b6dc0933a72047c70f817e2eb1e01bede011193ad1b28bc";
	public static final String checkpoint3 = "00000000001636e6cb9747abc92354385f43d6580ecf7326269aa92bd5b2beac";
	public static final String checkpoint4 = "0000000000827e4dc601f7310a91c45af8df0dfc1b6fa1dfa5b896cb00c8767c";
	public static final String checkpoint5 = "a82c673016dcb5ebf6dad3e772a22848454e4a32568b02a994a60612ba68a3b1";
	public static final String checkpoint6 = "a36f0013842adeb27aa70d20541925364879fdec25e84d6b4c4b256b71e48791";
	public static final String checkpoint7 = "8d82bf5332ea5540ae7aae53b77c4bde6ce96f00a30358b755ba3f15ee01096f";
	public static final String checkpoint8 = "62bf2e9701226d2f88d9fa99d650bd81f3faf2e56f305b7d71ccd1e7aa9c3075";
	public static final String checkpoint9 = "dbb6934ec506b0c6d96f3c1ab36cd8831c966446a15c128486361399a8fdc4c2";
	public static final String checkpoint10 = "a47cec53d42bc095b7b6e10d96a221bd85c0796f8cdcf157d96cf0d91e6a52b2";	
	public static final String checkpoint11 = "9bf8d9bd757d3ef23d5906d70567e5f0da93f1e0376588c8d421a95e2421838b";
	public static final String checkpoint12 = "0011494d03b2cdf1ecfc8b0818f1e0ef7ee1d9e9b3d1279c10d35456bc3899ef";
	
	public static final String dnsSeed0 = "rat4.blackcoin.co";
	public static final String dnsSeed1 = "seed.blackcoin.co";
	public static final String dnsSeed2 = "archon.darkfox.id.au";
	public static final String dnsSeed3 = "foxy.seeds.darkfox.id.au";
	public static final String dnsSeed4 = "6.syllabear.us.to";
	public static final String dnsSeed5 = "bcseed.syllabear.us.to";
	
	public static final Sha256Hash merkleRoot = new Sha256Hash("12630d16a97f24b287c8c2594dda5fb98c9e6c70fc61d44191931ea2aa08dc90");
	public static final Sha256Hash lastlastPowHash = new Sha256Hash("0000000000a9e46b1eb2617c8d03f29288482ee30e0c4aab1972b67a4b2697d4");
	public static final Sha256Hash lastPowHash = new Sha256Hash("000000000032c7897d4cfd01a48df7d7b43b861eb9c6fd5a2575e3761fa158d8");
	
	//main.cpp#L988 nTargetTimespan = 16 * 60;
	public static final int targetTimespan = 16 * 60;
	//main.cpp#L43 nTargetSpacing = 1 * 60;
	public static final int targetSpacing = 1 * 60;
	public static final int targetSpacing2 = 64;
	//main.h#L38 MAX_MONEY = 2000000000 * COIN;
	
	//main.cpp#L46 unsigned int nModifierInterval = 10 * 60; 
	// time to elapse before new modifier is computed
	public static final int modifierInterval = 10 * 60;
	
	public static final int interval = targetTimespan / targetSpacing;
	
	public static final String coin = "100000000";
	public static final long nCoin = 100000000;
	public static final long  maxMoney = 2000000000 * nCoin;
	public static final int protocolVersion = 60014;
	public static final int minProtocolVersion = 209;
	
	//kernel.h#L18 ratio of group interval length between the last group and the first group
	public static final int modifierIntervalRatio = 3;
	
	public static final int firstForkHeight = 38424;
	public static final int secondForkHeight = 319000;
	
	public static final int stakeMinAge = 8 * 60 * 60; // 8 hours
	public static final int stakeMaxAge = Integer.MAX_VALUE; // unlimited
	
	public static final long actualBlockVersion = 7;
	public static final long maxCoins = 2000000000;
	public static final int minimumStoreDepth = 1322;
	public static final long anyIndex = -33;
	public static final CharSequence blackAlertSigningKey = "0486bce1bac0d543f104cbff2bd23680056a3b9ea05e1137d2ff90eeb5e08472eb500322593a2cb06fbf8297d7beb6cd30cb90f98153b5b7cce1493749e41e0284";

	public static final int minToBePruned = 50;	
}
