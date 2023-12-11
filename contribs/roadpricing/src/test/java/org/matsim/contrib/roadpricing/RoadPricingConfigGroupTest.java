package org.matsim.contrib.roadpricing;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.testcases.MatsimTestUtils;


public class RoadPricingConfigGroupTest {

	@RegisterExtension
	private MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	void getTollLinksFile() {
		RoadPricingConfigGroup cg = new RoadPricingConfigGroup();
		Assert.assertNull("Default roadpricing file is not set.", cg.getTollLinksFile());
	}

	@Test
	void setTollLinksFile() {
		String file = "./test.xml.gz";
		RoadPricingConfigGroup cg = new RoadPricingConfigGroup();
		cg.setTollLinksFile(file);
		Assert.assertEquals("Wrong input file.", file, cg.getTollLinksFile());
	}

	@Test
	void getEnforcementProbability() {
		RoadPricingConfigGroup cg = new RoadPricingConfigGroup();
		Assert.assertEquals("Default probability should be 1.0", 1.0, cg.getEnforcementProbability(), MatsimTestUtils.EPSILON);

		double prob = 0.9;
		cg.setEnforcementProbability(prob);
		Assert.assertEquals("Didn't get the adjusted probability.", prob, cg.getEnforcementProbability(), MatsimTestUtils.EPSILON);
	}

	@Test
	void setEnforcementProbability() {
		RoadPricingConfigGroup cg = new RoadPricingConfigGroup();
		try{
			cg.setEnforcementProbability(1.2);
			Assert.fail("Should not accept probability > 1.0");
		} catch (Exception e){
			e.printStackTrace();
		}

		cg.setEnforcementProbability(0.95);
	}
}
