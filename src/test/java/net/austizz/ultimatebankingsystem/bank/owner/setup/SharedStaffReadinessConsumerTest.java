package net.austizz.ultimatebankingsystem.bank.owner.setup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.austizz.ultimatebankingsystem.bank.owner.setup.SafeStaffReadinessTestSupport.EMPLOYEE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedStaffReadinessConsumerTest {
    @Test
    void premiseConsumerStaysReadyAfterLastSafeAccessRevoke() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario = SafeStaffReadinessTestSupport.scenario();
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();
        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        assertEquals("READY", SafeStaffReadinessTestSupport.value(
                SafeStaffReadinessTestSupport.premiseProjection(scenario, metadata), "status").toString());

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("revokeSafeAccess", metadata, EMPLOYEE_ID));

        assertEquals("READY", SafeStaffReadinessTestSupport.value(
                SafeStaffReadinessTestSupport.premiseProjection(scenario, metadata), "status").toString(),
                "staffing changes must not gate the safe premise");
    }

    @Test
    void premiseProjectionPreservesStructuralReasonsAfterSafeAccessGrant() throws Exception {
        SafeStaffReadinessTestSupport.Scenario scenario =
                SafeStaffReadinessTestSupport.scenario("VAULT_DOOR_MISSING", "TELLER_ROUTE_MISSING");
        Object metadata = SafeStaffReadinessTestSupport.metadataWithEmployee();

        Object blocked = scenario.readiness();
        Object blockedSummary = SafeStaffReadinessTestSupport.value(blocked, "summary");
        assertEquals(List.of("VAULT_DOOR_MISSING", "TELLER_ROUTE_MISSING"),
                SafeStaffReadinessTestSupport.listValue(blockedSummary, "missingReasons").stream()
                        .map(Object::toString).toList());
        assertEquals(List.of(
                        "Complete BANK_VAULT_DOOR multiblock is missing.",
                        "Bank-bound teller outbound and return routes are missing."),
                SafeStaffReadinessTestSupport.listValue(blocked, "humanMissingReasons"));
        assertSame(scenario.premise(), SafeStaffReadinessTestSupport.value(blocked, "premise"));
        assertSame(scenario.area(), SafeStaffReadinessTestSupport.value(blocked, "safeArea"));
        assertSame(scenario.vault(), SafeStaffReadinessTestSupport.value(blocked, "vault"));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        assertEquals("NOT_READY", SafeStaffReadinessTestSupport.value(
                SafeStaffReadinessTestSupport.premiseProjection(scenario, metadata), "status").toString());
        assertEquals(EMPLOYEE_ID + "=STAFF:125.50",
                SafeStaffReadinessTestSupport.getString(metadata, "employees"));
    }

    @Test
    void publicSetupBuilderAndServicePublishTheSameTypedObjectiveReasons() throws Exception {
        Object metadata = SafeStaffReadinessTestSupport.setupMetadataWithEmployee();

        assertPublicReasons(metadata, List.of(
                "VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING"));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("grantSafeAccess", metadata, EMPLOYEE_ID));
        assertPublicReasons(metadata, List.of(
                "VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING"));

        assertTrue(SafeStaffReadinessTestSupport.staffingMutation("revokeSafeAccess", metadata, EMPLOYEE_ID));
        assertPublicReasons(metadata, List.of(
                "VAULT_DOOR_MISSING", "ASSIGNABLE_ROW_MISSING", "VIEWING_ROOM_MISSING"));
        assertEquals(EMPLOYEE_ID + "=STAFF:125.50",
                SafeStaffReadinessTestSupport.getString(metadata, "employees"));
    }

    private static void assertPublicReasons(Object metadata, List<String> expectedReasons) throws Exception {
        Object serviceRow = SafeStaffReadinessTestSupport.publicServiceReadiness(metadata).getFirst();
        Object serviceSummary = SafeStaffReadinessTestSupport.value(serviceRow, "summary");
        assertEquals(expectedReasons, SafeStaffReadinessTestSupport.listValue(serviceSummary, "missingReasons")
                .stream().map(Object::toString).toList());

        Object setup = SafeStaffReadinessTestSupport.publicSetupProjection(metadata);
        Object vault = SafeStaffReadinessTestSupport.listValue(setup, "vaults").getFirst();
        assertEquals(expectedReasons, SafeStaffReadinessTestSupport.listValue(vault, "missingReasons"));
        assertEquals("premise-1", SafeStaffReadinessTestSupport.value(vault, "premiseId"));
        assertEquals("safe-area-1", SafeStaffReadinessTestSupport.value(vault, "safeAreaId"));
        assertEquals("vault-1", SafeStaffReadinessTestSupport.value(vault, "vaultId"));
        assertEquals(false, SafeStaffReadinessTestSupport.listValue(
                SafeStaffReadinessTestSupport.value(setup, "objective"), "missingSteps")
                .stream().anyMatch(reason -> reason.toString().contains("Safe Access")));
    }
}
