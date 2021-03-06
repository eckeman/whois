package net.ripe.db.whois.internal.api.sso;

import net.ripe.db.whois.api.CrowdServerDummy;
import net.ripe.db.whois.api.RestTest;
import net.ripe.db.whois.api.rest.domain.ErrorMessage;
import net.ripe.db.whois.api.rest.domain.WhoisObject;
import net.ripe.db.whois.api.rest.domain.WhoisResources;
import net.ripe.db.whois.api.rest.mapper.WhoisObjectClientMapper;
import net.ripe.db.whois.common.ClockDateTimeProvider;
import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.dao.jdbc.JdbcRpslObjectDao;
import net.ripe.db.whois.common.dao.jdbc.JdbcRpslObjectUpdateDao;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.sso.CrowdClient;
import net.ripe.db.whois.internal.AbstractInternalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category(IntegrationTest.class)
public class UserOrgFinderServiceTestIntegration extends AbstractInternalTest {

    @Autowired @Qualifier("whoisReadOnlySlaveDataSource") DataSource dataSource;
    @Autowired CrowdClient crowdClient;
    @Autowired UserOrgFinder userOrgFinder;
    CrowdServerDummy crowdServerDummy;

    @Before
    public void setUp() throws Exception {
        databaseHelper.insertApiKey(apiKey, "/api/user", apiKey);
        databaseHelper.setCrowdClient(crowdClient);

        // TODO: drop this once we have proper wiring in whois-internal
        databaseHelper.setRpslObjectDao(new JdbcRpslObjectDao(dataSource, null));
        databaseHelper.setRpslObjectUpdateDao(new JdbcRpslObjectUpdateDao(dataSource, new ClockDateTimeProvider()));
    }

    @Before
    public void resetDatabaseHelper() {
        databaseHelper.setupWhoisDatabase(new JdbcTemplate(dataSource));
    }

    @Before
    public void crowdServerDummyStart() {
        crowdServerDummy = new CrowdServerDummy(crowdClient);
        crowdServerDummy.start();
    }

    @After
    public void crowdServerDummyStop() throws Exception {
        crowdServerDummy.stop();
    }

    @Test
    public void no_organisations_found() throws InterruptedException {
        try {
            RestTest.target(getPort(), "api/user/aaaa-bbbb-cccc-dddd-1234/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);
            fail();
        } catch (WebApplicationException expected) {
            final WhoisResources entity = expected.getResponse().readEntity(WhoisResources.class);

            assertThat(entity.getErrorMessages().size(), is(1));

            final ErrorMessage errorMessage = entity.getErrorMessages().get(0);
            assertThat(errorMessage.getSeverity(), is("Error"));
            assertThat(errorMessage.getText(), is("No organisations found"));
        }
    }

    @Test
    public void organisations_found_via_mnt_by() {
        databaseHelper.addObject("mntner: TEST-MNT\nmnt-by:TEST-MNT\nauth: SSO db-test@ripe.net");
        final RpslObject organisation = RpslObject.parse("" +
                "organisation: ORG-TST-TEST\n" +
                "mnt-by: TEST-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(organisation);

        final WhoisResources result = RestTest.target(getPort(), "api/user/ed7cd420-6402-11e3-949a-0800200c9a66/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);

        final RpslObject resultOrg = new WhoisObjectClientMapper("test.url").map(result.getWhoisObjects().get(0));

        assertThat(resultOrg, is(organisation));
    }

    @Test
    public void organisations_w_mnt_by_RS_found() {
        databaseHelper.addObject("mntner: TEST-MNT\nmnt-by:TEST-MNT\nauth: SSO person@net.net");
        databaseHelper.addObject("mntner: RIPE-NCC-HM-MNT\nmnt-by:RIPE-NCC-HM-MNT\nauth: SSO db-test@ripe.net");
        final RpslObject organisation = RpslObject.parse("" +
                "organisation: ORG-TST-TEST\n" +
                "mnt-ref: TEST-MNT\n" +
                "mnt-by: RIPE-NCC-HM-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(organisation);
        final WhoisResources result = RestTest.target(getPort(), "api/user/ed7cd420-6402-11e3-949a-0800200c9a66/organisations", null, apiKey)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get(WhoisResources.class);

        final RpslObject resultOrg = new WhoisObjectClientMapper("test.url").map(result.getWhoisObjects().get(0));

        assertThat(resultOrg, is(organisation));
    }

    @Test
    public void organisations_not_found_via_mnt_ref() {
        databaseHelper.addObject("mntner: TEST-MNT\nmnt-by:TEST-MNT\nauth: SSO db-test@ripe.net");
        final RpslObject organisation = RpslObject.parse("" +
                "organisation: ORG-TST-TEST\n" +
                "mnt-ref: TEST-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(organisation);
        try {
            RestTest.target(getPort(), "api/user/ed7cd420-6402-11e3-949a-0800200c9a66/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);
            fail();
        } catch (NotFoundException expected) {}
    }

    @Test
    public void organisations_w_mnt_by_RS_found_via_mnt_ref(){
        databaseHelper.addObject("mntner: TEST-MNT\nmnt-by:TEST-MNT\nauth: SSO db-test@ripe.net");
        databaseHelper.addObject("mntner: RIPE-NCC-HM-MNT\nmnt-by:RIPE-NCC-HM-MNT\nauth: SSO person@net.net");
        final RpslObject organisation = RpslObject.parse("" +
                "organisation: ORG-TST-TEST\n" +
                "mnt-ref: TEST-MNT\n" +
                "mnt-by: RIPE-NCC-HM-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(organisation);

        final WhoisResources result = RestTest.target(getPort(), "api/user/ed7cd420-6402-11e3-949a-0800200c9a66/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);

        final RpslObject resultOrg = new WhoisObjectClientMapper("test.url").map(result.getWhoisObjects().get(0));

        assertThat(resultOrg, is(organisation));
    }

    @Test
    public void organisation_only_added_once() {
        databaseHelper.addObject("mntner: TEST-MNT\nmnt-by:TEST-MNT\nauth: SSO person@net.net");
        final RpslObject organisation = RpslObject.parse("" +
                "organisation: ORG-TST-TEST\n" +
                "mnt-by: TEST-MNT\n" +
                "mnt-by: TEST-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(organisation);

        final WhoisResources result = RestTest.target(getPort(), "api/user/906635c2-0405-429a-800b-0602bd716124/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);

        final List<WhoisObject> whoisObjects = result.getWhoisObjects();
        assertThat(whoisObjects, hasSize(1));

        final RpslObject resultOrg = new WhoisObjectClientMapper("test.url").map(whoisObjects.get(0));

        assertThat(resultOrg, is(organisation));
    }

    @Test
    public void only_organisations_returned() {
        databaseHelper.addObject("mntner: TEST-MNT\nmnt-by:TEST-MNT\nauth: SSO test@ripe.net");
        final RpslObject organisation = RpslObject.parse("" +
                "organisation: ORG-TST-TEST\n" +
                "mnt-ref: TEST-MNT\n" +
                "mnt-by: TEST-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(organisation);
        databaseHelper.addObject("person: Test Person\nnic-hdl: TST-TEST\nmnt-by: TEST-MNT");

        final WhoisResources result = RestTest.target(getPort(), "api/user/8ffe29be-89ef-41c8-ba7f-0e1553a623e5/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);

        final List<WhoisObject> whoisObjects = result.getWhoisObjects();
        assertThat(whoisObjects, hasSize(1));

        final RpslObject resultOrg = new WhoisObjectClientMapper("test.url").map(whoisObjects.get(0));

        assertThat(resultOrg, is(organisation));
    }

    @Test
    public void only_auth_sso_mntners_yield_results() {
        databaseHelper.addObject("mntner: SSO-MNT\nmnt-by:SSO-MNT\nauth: SSO random@ripe.net");
        databaseHelper.addObject("mntner: MD5-MNT\nmnt-by:MD5-MNT\nauth: MD5-PW 017f750e-6eb8-4ab1-b5ec-8ad64ce9a503");
        final RpslObject organisation = RpslObject.parse("" +
                "organisation: ORG-SSO-TEST\n" +
                "mnt-ref: SSO-MNT\n" +
                "mnt-by: SSO-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(organisation);
        databaseHelper.addObject("" +
                "organisation: ORG-MD5-TEST\n" +
                "mnt-ref: MD5-MNT\n" +
                "mnt-by: MD5-MNT\n" +
                "source: TEST");

        final WhoisResources result = RestTest.target(getPort(), "api/user/017f750e-6eb8-4ab1-b5ec-8ad64ce9a503/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);

        final List<WhoisObject> whoisObjects = result.getWhoisObjects();
        assertThat(whoisObjects, hasSize(1));

        final RpslObject resultOrg = new WhoisObjectClientMapper("test.url").map(whoisObjects.get(0));

        assertThat(resultOrg, is(organisation));
    }

    @Test
    public void all_organisations_returned() {
        databaseHelper.addObject("mntner: TEST-MNT\nmnt-by:TEST-MNT\nauth: SSO db-test@ripe.net\nsource:test");
        final RpslObject org1 = RpslObject.parse("" +
                "organisation: ORG-TST1-TEST\n" +
                "mnt-by: TEST-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(org1);
        final RpslObject org2 = RpslObject.parse("" +
                "organisation: ORG-MD5-TEST\n" +
                "mnt-by: TEST-MNT\n" +
                "source: TEST");
        databaseHelper.addObject(org2);

        final WhoisResources result = RestTest.target(getPort(), "api/user/ed7cd420-6402-11e3-949a-0800200c9a66/organisations", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(WhoisResources.class);

        final List<WhoisObject> whoisObjects = result.getWhoisObjects();
        assertThat(whoisObjects, hasSize(2));

        final WhoisObjectClientMapper objectMapper = new WhoisObjectClientMapper("test.url");

        assertThat(objectMapper.map(whoisObjects.get(0)), is(org2));
        assertThat(objectMapper.map(whoisObjects.get(1)), is(org1));
    }
}
