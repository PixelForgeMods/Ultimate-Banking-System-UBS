package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePairResolver;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultReadinessResolver;
import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;

public final class SafeVaultReadinessOperation {
    private final SafeDepositSetupSnapshot snapshot;
    private final SafeVaultReadinessResolver.EvaluationContext context;

    SafeVaultReadinessOperation(SafeDepositSetupSnapshot snapshot,
                                SafeVaultReadinessResolver.EvaluationContext context) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.context = Objects.requireNonNull(context, "context");
    }

    public SafeVaultReadinessResolver.RowReadiness resolve(
            SafeVaultReadinessResolver.RowLocation location) {
        return SafeVaultReadinessResolver.resolveForRow(
                new SafeVaultReadinessResolver.RowRequest(context, snapshot, location));
    }

    public SafeVaultReadinessResolver.RowReadiness resolve(
            SafeVaultReadinessResolver.VaultSelection selection) {
        return SafeVaultReadinessResolver.resolveVault(
                new SafeVaultReadinessResolver.VaultRequest(context, selection));
    }

    public SafeTellerRoutePairResolver.Context routes() {
        return context.routes();
    }

    SafeDepositSetupSnapshot snapshot() {
        return snapshot;
    }

    SafeVaultReadinessResolver.LoadedWorldFacts facts() {
        return context.facts();
    }

    public Optional<BlockPos> resolveDoorMaster(SafeVaultReadinessResolver.RowReadiness readiness) {
        if (readiness == null || !readiness.mapped() || readiness.vault() == null
                || readiness.summary() == null || !readiness.summary().ready()) {
            return Optional.empty();
        }
        return context.facts().resolvedDoorPos(readiness.vault().id()).map(BlockPos::immutable);
    }
}
