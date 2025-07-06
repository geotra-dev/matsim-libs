/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2024 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.contrib.roadpricing.run;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.roadpricing.RoadPricing;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.contrib.roadpricing.RoadPricingUtils;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.roadpricing.RoadPricingConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;

/**
 * Example showing how to run MATSim with both signals and road pricing modules.
 * This example demonstrates the integration of traffic signal control and road pricing
 * in a single simulation.
 *
 * @author [Generated Example]
 */
public class RunSignalsWithRoadPricingExample {

	public static void main(String[] args) {
		// Load configuration
		Config config;
		if (args.length == 0) {
			// Use example config - you'll need to adjust paths for your scenario
			config = createExampleConfig();
		} else {
			config = ConfigUtils.loadConfig(args[0]);
		}

		// Run the simulation
		run(config);
	}

	public static void run(Config config) {
		// Create scenario and load it
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		// Load signals data
		SignalsData signalsData = new SignalsDataLoader(config).loadSignalsData();
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, signalsData);
		
		// Load road pricing data
		RoadPricingUtils.loadRoadPricingScheme(scenario);
		
		// Create controler
		Controler controler = new Controler(scenario);
		
		// Add signals module
		Signals.configure(controler);
		System.out.println("Signals module configured");
		
		RoadPricing.configure(controler);
		System.out.println("Road pricing module configured");
		
		// Run the simulation
		controler.run();
	}

	private static Config createExampleConfig() {
		Config config = ConfigUtils.createConfig();
		
		// Basic configuration
		ControlerConfigGroup controllerConfig = (ControlerConfigGroup) config.getModule(ControlerConfigGroup.GROUP_NAME);
		controllerConfig.setLastIteration(10);
		controllerConfig.setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		controllerConfig.setOutputDirectory("output/signalsWithRoadPricing/");
		
		// Network and population files (adjust paths as needed)
		config.network().setInputFile("network.xml");
		config.plans().setInputFile("population.xml");
		
		// Signals configuration
		SignalSystemsConfigGroup signalsConfig = ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class);
		signalsConfig.setSignalSystemFile("signalSystems.xml");
		signalsConfig.setSignalGroupsFile("signalGroups.xml");
		signalsConfig.setSignalControlFile("signalControl.xml");
		
		// Road pricing configuration
		RoadPricingConfigGroup roadPricingConfig = ConfigUtils.addOrGetModule(config, RoadPricingConfigGroup.class);
		roadPricingConfig.setTollLinksFile("tollLinks.xml");
		
		// QSim configuration
		QSimConfigGroup qsimConfig = (QSimConfigGroup) config.getModule(QSimConfigGroup.GROUP_NAME);
		qsimConfig.setEndTime(24 * 3600);
		qsimConfig.setSnapshotPeriod(300);
		
		// Scoring configuration (important for road pricing)
		PlanCalcScoreConfigGroup scoringConfig = (PlanCalcScoreConfigGroup) config.getModule("planCalcScore");
		PlanCalcScoreConfigGroup.ModeParams carModeParams = scoringConfig.getOrCreateModeParams("car");
		carModeParams.setMarginalUtilityOfTraveling(-6.0);
		scoringConfig.setMarginalUtilityOfMoney(1.0);
		
		return config;
	}
}