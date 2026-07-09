package bflow.wallet.DTO;

import bflow.wallet.entities.Wallet;

public record WalletPair(
        Wallet oldWallet,
        Wallet newWallet
) { }
