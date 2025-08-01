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
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.roadpricing.RoadPricing;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.contrib.roadpricing.RoadPricingUtils;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.roadpricing.RoadPricingConfigGroup;
import org.matsim.contrib.cadyts.car.CadytsCarModule;
import org.matsim.contrib.cadyts.car.CadytsContext;
import org.matsim.contrib.cadyts.general.CadytsConfigGroup;
import org.matsim.contrib.cadyts.general.CadytsScoring;
import org.matsim.contrib.cadyts.general.CadytsPlanChanger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;

import javax.inject.Inject;
import com.google.inject.Provider;

/**
 * Example showing how to run MATSim with signals, road pricing, and Cadyts calibration modules together.
 * This example demonstrates the integration of traffic signal control, road pricing,
 * and Cadyts traffic count calibration in a single simulation.
 *
 * @author [Generated Example]
 */
public class RunSignalsWithRoadPricingAndCadytsExample {

	public static void main(String[] args) {
		// Load configuration with all necessary config groups
		Config config;
		if (args.length == 0) {
			// Use example config - you'll need to adjust paths for your scenario
			config = createExampleConfig();
		} else {
			config = ConfigUtils.loadConfig(args[0], 
				new CadytsConfigGroup(),
				new SignalSystemsConfigGroup(),
				new RoadPricingConfigGroup());
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
		
		// Add road pricing module
		RoadPricing.configure(controler);
		System.out.println("Road pricing module configured");
		
		// Add Cadyts module
		controler.addOverridingModule(new CadytsCarModule());
		System.out.println("Cadyts module configured");
		
		// Configure scoring function with Cadyts integration
		controler.setScoringFunctionFactory(new ScoringFunctionFactory() {
			@Inject CadytsContext cadytsContext;
			@Inject ScoringParametersForPerson parameters;
			
			@Override
			public ScoringFunction createNewScoringFunction(Person person) {
				final ScoringParameters params = parameters.getScoringParameters(person);
				
				SumScoringFunction scoringFunctionAccumulator = new SumScoringFunction();
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelLegScoring(params, controler.getScenario().getNetwork(), config.transit().getTransitModes()));
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelActivityScoring(params));
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelAgentStuckScoring(params));
				
				// Add Cadyts scoring
				final CadytsScoring<Link> cadytsScoring = new CadytsScoring<>(person.getSelectedPlan(), config, cadytsContext);
				cadytsScoring.setWeightOfCadytsCorrection(1000000. * config.planCalcScore().getBrainExpBeta());
				scoringFunctionAccumulator.addScoringFunction(cadytsScoring);
				
				return scoringFunctionAccumulator;
			}
		});
		
		// Add Cadyts plan strategy
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addPlanStrategyBinding("CadytsPlanChanger").toProvider(new Provider<PlanStrategy>() {
					@Inject Scenario scenario;
					@Inject CadytsContext cadytsContext;
					
					@Override
					public PlanStrategy get() {
						return new PlanStrategyImpl(new CadytsPlanChanger(scenario, cadytsContext));
					}
				});
			}
		});
		
		// Run the simulation
		controler.run();
	}

	private static Config createExampleConfig() {
		// Create config with all necessary modules
		Config config = ConfigUtils.createConfig(
			new CadytsConfigGroup(),
			new SignalSystemsConfigGroup(),
			new RoadPricingConfigGroup()
		);
		
		// Basic configuration
		ControlerConfigGroup controllerConfig = (ControlerConfigGroup) config.getModule(ControlerConfigGroup.GROUP_NAME);
		controllerConfig.setLastIteration(10);
		controllerConfig.setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		controllerConfig.setOutputDirectory("output/signalsWithRoadPricingAndCadyts/");
		
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
		
		// Cadyts configuration
		CadytsConfigGroup cadytsConfig = ConfigUtils.addOrGetModule(config, CadytsConfigGroup.GROUP_NAME, CadytsConfigGroup.class);
		cadytsConfig.setStartTime(0);
		cadytsConfig.setEndTime(24 * 3600);
		cadytsConfig.setMinFlowStddev_vehPerHour(25);
		cadytsConfig.setVarianceScale(1.0);
		cadytsConfig.setWriteAnalysisFile(true);
		cadytsConfig.setUseBruteForce(false);
		cadytsConfig.setPreparatoryIterations(1);
		cadytsConfig.setTimeBinSize(3600);
		
		// QSim configuration
		QSimConfigGroup qsimConfig = (QSimConfigGroup) config.getModule(QSimConfigGroup.GROUP_NAME);
		qsimConfig.setEndTime(24 * 3600);
		qsimConfig.setSnapshotPeriod(300);
		
		// Scoring configuration (important for road pricing and Cadyts)
		PlanCalcScoreConfigGroup scoringConfig = (PlanCalcScoreConfigGroup) config.getModule("planCalcScore");
		PlanCalcScoreConfigGroup.ModeParams carModeParams = scoringConfig.getOrCreateModeParams("car");
		carModeParams.setMarginalUtilityOfTraveling(-6.0);
		scoringConfig.setMarginalUtilityOfMoney(1.0);
		scoringConfig.setBrainExpBeta(1.0);
		
		// Strategy configuration to include Cadyts
		StrategyConfigGroup strategyConfig = config.strategy();
		StrategyConfigGroup.StrategySettings cadytsStrategy = new StrategyConfigGroup.StrategySettings();
		cadytsStrategy.setStrategyName("CadytsPlanChanger");
		cadytsStrategy.setWeight(0.1);
		strategyConfig.addStrategySettings(cadytsStrategy);
		
		// Other strategies
		StrategyConfigGroup.StrategySettings reRouteStrategy = new StrategyConfigGroup.StrategySettings();
		reRouteStrategy.setStrategyName("ReRoute");
		reRouteStrategy.setWeight(0.5);
		strategyConfig.addStrategySettings(reRouteStrategy);
		
		StrategyConfigGroup.StrategySettings changeExpBetaStrategy = new StrategyConfigGroup.StrategySettings();
		changeExpBetaStrategy.setStrategyName("ChangeExpBeta");
		changeExpBetaStrategy.setWeight(0.4);
		strategyConfig.addStrategySettings(changeExpBetaStrategy);
		
		strategyConfig.setMaxAgentPlanMemorySize(5);
		
		return config;
	}
}