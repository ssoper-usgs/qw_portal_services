package gov.usgs.cida.qw.summary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseSetups;

import gov.usgs.cida.qw.BaseIT;
import gov.usgs.cida.qw.BaseRestController;
import gov.usgs.cida.qw.CustomStringToArrayConverter;
import gov.usgs.cida.qw.LastUpdateDao;
import gov.usgs.cida.qw.springinit.DBTestConfig;
import gov.usgs.cida.qw.springinit.SpringConfig;
import gov.usgs.cida.qw.summary.SldTemplateEngine.MapDataSource;
import gov.usgs.cida.qw.summary.SldTemplateEngine.MapGeometry;

@EnableWebMvc
@AutoConfigureMockMvc(secure=false)
@SpringBootTest(webEnvironment=WebEnvironment.MOCK,
	classes={DBTestConfig.class, SpringConfig.class, CustomStringToArrayConverter.class,
			SummaryController.class, LastUpdateDao.class, SummaryDao.class})
@DatabaseSetups({
	@DatabaseSetup("classpath:/testData/clearAll.xml"),
	@DatabaseSetup("classpath:/testData/summary.xml")
})
public class SummaryControllerIT extends BaseIT {

	@Autowired
	private LastUpdateDao lastUpdateDao;
	@Autowired
	private SummaryDao summaryDao;
	@Autowired
	private MockMvc mockMvc;

	@Test
	public void getDataSourcesTest() {
		SummaryController controller = new SummaryController(lastUpdateDao, summaryDao);
		assertEquals(0, controller.getDataSources(null).length);
		assertArrayEquals(new Object[]{"N"}, controller.getDataSources(MapDataSource.USGS));
		assertArrayEquals(new Object[]{"E"}, controller.getDataSources(MapDataSource.EPA));
		assertArrayEquals(new Object[]{"E","N"}, controller.getDataSources(MapDataSource.All));
	}

	@Test
	public void getGeometryTest() {
		SummaryController controller = new SummaryController(lastUpdateDao, summaryDao);
		assertNull(controller.getGeometry(null));
		assertEquals("States", controller.getGeometry(MapGeometry.States));
		assertEquals("Counties", controller.getGeometry(MapGeometry.Counties));
		assertEquals("Huc8", controller.getGeometry(MapGeometry.Huc8));
	}

	@Test
	public void getTimeFrameTest() {
		SummaryController controller = new SummaryController(lastUpdateDao, summaryDao);
		assertEquals("ALL_TIME", controller.getTimeFrame(null));
		assertEquals("ALL_TIME", controller.getTimeFrame(""));
		assertEquals("ALL_TIME", controller.getTimeFrame("QQ"));
		assertEquals("ALL_TIME", controller.getTimeFrame("A"));
		assertEquals("PAST_12_MONTHS", controller.getTimeFrame("1"));
		assertEquals("PAST_60_MONTHS", controller.getTimeFrame("5"));
	}

	@Test
	public void deriveDbParamsTest() {
		SummaryController controller = new SummaryController(lastUpdateDao, summaryDao);
		assertEquals(0, controller.deriveDbParams(null, null, null).size());
		assertEquals(0, controller.deriveDbParams(MapDataSource.All, null, null).size());
		assertEquals(3, controller.deriveDbParams(MapDataSource.All, MapGeometry.Huc8, null).size());
		assertEquals(0, controller.deriveDbParams(MapDataSource.All, null, "A").size());
		assertEquals(0, controller.deriveDbParams(null, MapGeometry.Huc8, null).size());
		assertEquals(0, controller.deriveDbParams(null, MapGeometry.Huc8, "A").size());
		assertEquals(0, controller.deriveDbParams(null, null, "A").size());

		Map<String, Object> parms = controller.deriveDbParams(MapDataSource.All, MapGeometry.Huc8, "A");
		assertEquals(3, parms.size());
		assertTrue(parms.containsKey("sources"));
		assertArrayEquals(new Object[]{"E","N"}, (Object[]) parms.get("sources"));
		assertTrue(parms.containsKey("geometry"));
		assertEquals("Huc8", parms.get("geometry"));
		assertTrue(parms.containsKey("timeFrame"));
		assertEquals("ALL_TIME", parms.get("timeFrame"));

		parms = controller.deriveDbParams(MapDataSource.EPA, MapGeometry.States, "1");
		assertEquals(3, parms.size());
		assertTrue(parms.containsKey("sources"));
		assertArrayEquals(new Object[]{"E"}, (Object[]) parms.get("sources"));
		assertTrue(parms.containsKey("geometry"));
		assertEquals("States", parms.get("geometry"));
		assertTrue(parms.containsKey("timeFrame"));
		assertEquals("PAST_12_MONTHS", parms.get("timeFrame"));

		parms = controller.deriveDbParams(MapDataSource.USGS, MapGeometry.Counties, "5");
		assertEquals(3, parms.size());
		assertTrue(parms.containsKey("sources"));
		assertArrayEquals(new Object[]{"N"}, (Object[]) parms.get("sources"));
		assertTrue(parms.containsKey("geometry"));
		assertEquals("Counties", parms.get("geometry"));
		assertTrue(parms.containsKey("timeFrame"));
		assertEquals("PAST_60_MONTHS", parms.get("timeFrame"));
	}

	@Test
	public void retrieveBinValuesTest() {
		Map<String, Object> parms = new HashMap<>();
		SummaryController controller = new SummaryController(null, null);
		assertArrayEquals(new String[0], controller.retrieveBinValues(null));

		assertArrayEquals(new String[0], controller.retrieveBinValues(parms));

		parms.put("geometry", "States");
		parms.put("timeFrame", "PAST_60_MONTHS");
		assertArrayEquals(new String[0], controller.retrieveBinValues(parms));

		controller = new SummaryController(lastUpdateDao, summaryDao);
		assertArrayEquals(new String[0], controller.retrieveBinValues(parms));

		parms.put("sources", new Object[]{"E","N"});
		String[] bins = controller.retrieveBinValues(parms);
		assertEquals(10, bins.length);
		assertArrayEquals(new String[]{"9", "9", "10", "56", "57", "495", "496", "520", "521", "728"}, bins);
	}

	@Test
	public void getSummarySldTest() throws Exception {
		MvcResult rtn = mockMvc.perform(get("/summary?dataSource=A&geometry=S&timeFrame=1"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(BaseRestController.MEDIA_TYPE_APPLICATION_XML_UTF8_VALUE))
				.andExpect(content().encoding(BaseRestController.DEFAULT_ENCODING))
				.andReturn();
		assertThat(rtn.getResponse().getContentAsString(), isSimilarTo(getCompareFile("summary.sld")).ignoreWhitespace().throwComparisonFailure());
	}

}
