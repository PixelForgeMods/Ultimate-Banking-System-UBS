package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.api.bank.UltimateBankManagementApi;
import net.austizz.ultimatebankingsystem.api.bank.UltimateBankManagementApiImpl;
import net.austizz.ultimatebankingsystem.api.general.UltimateServerApi;
import net.austizz.ultimatebankingsystem.api.general.UltimateServerApiImpl;
import net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApi;
import net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApiImpl;
import net.austizz.ultimatebankingsystem.api.heist.UltimateHeistApi;
import net.austizz.ultimatebankingsystem.api.heist.UltimateHeistApiImpl;
import net.austizz.ultimatebankingsystem.api.job.UltimateJobApi;
import net.austizz.ultimatebankingsystem.api.job.UltimateJobApiImpl;
import net.austizz.ultimatebankingsystem.api.organization.UltimateOrganizationApi;
import net.austizz.ultimatebankingsystem.api.organization.UltimateOrganizationApiImpl;
import net.austizz.ultimatebankingsystem.api.shop.UltimateShopManagementApi;
import net.austizz.ultimatebankingsystem.api.shop.UltimateShopManagementApiImpl;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("1.1.0")
public final class UltimateBankingApiProvider {
    private static final UltimateBankingApi INSTANCE = new UltimateBankingApiImpl();
    private static final UltimateServerApi SERVER = new UltimateServerApiImpl();
    private static final UltimateBankManagementApi BANKS = new UltimateBankManagementApiImpl();
    private static final UltimateShopManagementApi SHOPS = new UltimateShopManagementApiImpl();
    private static final UltimateHeistApi HEISTS = new UltimateHeistApiImpl();
    private static final UltimateEconomyApi ECONOMY = new UltimateEconomyApiImpl();
    private static final UltimateOrganizationApi ORGANIZATIONS = new UltimateOrganizationApiImpl();
    private static final UltimateJobApi JOBS = new UltimateJobApiImpl();

    private UltimateBankingApiProvider() {}

    public static UltimateBankingApi get() {
        return INSTANCE;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateServerApi server() {
        return SERVER;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateBankManagementApi banks() {
        return BANKS;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateShopManagementApi shops() {
        return SHOPS;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateHeistApi heists() {
        return HEISTS;
    }

    @ApiStatus.AvailableSince("2.1.0")
    public static UltimateEconomyApi economy() {
        return ECONOMY;
    }

    @ApiStatus.AvailableSince("2.2.0")
    public static UltimateOrganizationApi organizations() {
        return ORGANIZATIONS;
    }

    /**
     * Alias for integrations that prefer an explicit account-oriented name.
     */
    @ApiStatus.AvailableSince("2.2.0")
    public static UltimateOrganizationApi organizationAccounts() {
        return ORGANIZATIONS;
    }

    @ApiStatus.AvailableSince("2.1.2")
    public static UltimateJobApi jobs() {
        return JOBS;
    }
}
