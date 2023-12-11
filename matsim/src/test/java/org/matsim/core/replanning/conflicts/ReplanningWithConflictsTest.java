package org.matsim.core.replanning.conflicts;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ReplanningConfigGroup.StrategySettings;
import org.matsim.core.config.groups.RoutingConfigGroup.TeleportedModeParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ActivityParams;
import org.matsim.core.config.groups.ScoringConfigGroup.ModeParams;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultStrategy;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author Sebastian Hörl (sebhoerl), IRT SystemX
 */
public class ReplanningWithConflictsTest {
	@RegisterExtension
	private MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	void testModeRestriction() {
		/*
		 * This is the possibly simplest use of the the conflict resolution logic. We
		 * have 100 agents and two modes, one is "unrestricted", the other one is
		 * "restricted". Each agent has one leg with either mode, all start with an
		 * "unrestricted" leg. Having an "unrestrited" leg does not give any score,
		 * having a "restricted" leg gives a score of 1.0. The replanning logic is
		 * BestScore, so if selection is chosen, a plan with "restricted" will be chosen
		 * if it exists in the agent memory. For innovation, we use ChangeTripMode, so
		 * we choose a new random mode.
		 *
		 * Without conflicts, this will lead to all agents choosing the "restricted"
		 * mode eventually, only 10% will choose "unrestricted" because they innovate.
		 *
		 * However, we introduce a conflict logic that ensures that we never have more
		 * than 40 agents using the "restricted" mode. In our simple logic, we iterate
		 * through the agents and "accept" plans as long as we don't go over this
		 * threshold. As soon as we hit the limit, we note down the "conflicting" agents
		 * and let our ConflictResolver return their IDs. The conflict logic will then
		 * *reject* the plans generated by replanning and switch the agents back to a
		 * non-conflicting plan (in that case the ones with the "unrestricted" mode).
		 *
		 * To make sure that every agent always has a non-conflicting plan in memory, we
		 * use WorstPlanForRemovalSelectorWithConflicts as the planRemovalSelector.
		 *
		 * Running the simulation will make the agents replan and soon we hit the mark
		 * of 40 agents using the "restricted" mode. After that, the conflict logic will
		 * start rejecting plans. Note that in this particular logic as a side effect,
		 * we will also slowly sort out agents so that the 40 first in the population
		 * will eventually be the ones using the restricted mode. In a more elaborate
		 * logic we would make sure that, for instance, agents that didn't change their
		 * plan or were already using initially the restricted mode are treated with
		 * priority.
		 *
		 * This is the most simple set-up of a conflict resolution process. It can be
		 * adapted to all kind of use cases, for instance, managing parking space,
		 * matching agents for car-pooling or implementing peer-to-peer car-sharing.
		 */
		Config config = ConfigUtils.createConfig();

		config.controller().setOutputDirectory(utils.getOutputDirectory());
		config.controller().setLastIteration(10);

		ActivityParams genericActivityParams = new ActivityParams("generic");
		genericActivityParams.setScoringThisActivityAtAll(false);
		config.scoring().addActivityParams(genericActivityParams);

		ModeParams unrestrictedModeParams = new ModeParams("unrestricted_mode");
		unrestrictedModeParams.setConstant(0.0);
		config.scoring().addModeParams(unrestrictedModeParams);

		ModeParams restrictedModeParams = new ModeParams("restricted_mode");
		restrictedModeParams.setConstant(1.0);
		config.scoring().addModeParams(restrictedModeParams);

		TeleportedModeParams unrestrictedRoutingParams = new TeleportedModeParams("unrestricted_mode");
		unrestrictedRoutingParams.setTeleportedModeSpeed(1.0);
		config.routing().addTeleportedModeParams(unrestrictedRoutingParams);

		TeleportedModeParams restrictedRoutingParams = new TeleportedModeParams("restricted_mode");
		restrictedRoutingParams.setTeleportedModeSpeed(1.0);
		config.routing().addTeleportedModeParams(restrictedRoutingParams);

		TeleportedModeParams walkRoutingParams = new TeleportedModeParams("walk");
		walkRoutingParams.setTeleportedModeSpeed(1.0);
		config.routing().addTeleportedModeParams(walkRoutingParams);

		config.replanning().clearStrategySettings();

		StrategySettings selectionStrategy = new StrategySettings();
		selectionStrategy.setStrategyName(DefaultSelector.BestScore);
		selectionStrategy.setWeight(0.9);
		config.replanning().addStrategySettings(selectionStrategy);

		StrategySettings innovationStrategy = new StrategySettings();
		innovationStrategy.setStrategyName(DefaultStrategy.ChangeTripMode);
		innovationStrategy.setWeight(0.1);
		config.replanning().addStrategySettings(innovationStrategy);

		config.replanning().setPlanSelectorForRemoval(WorstPlanForRemovalSelectorWithConflicts.SELECTOR_NAME);

		config.changeMode().setModes(new String[] { "restricted_mode", "unrestricted_mode" });

		Scenario scenario = ScenarioUtils.createScenario(config);

		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();

		for (int i = 0; i < 100; i++) {
			Person person = populationFactory.createPerson(Id.createPersonId("p" + i));
			population.addPerson(person);

			Plan plan = populationFactory.createPlan();
			person.addPlan(plan);

			Activity firstActivity = populationFactory.createActivityFromCoord("generic", new Coord(0.0, 0.0));
			firstActivity.setEndTime(0.0);
			plan.addActivity(firstActivity);

			Leg leg = populationFactory.createLeg("unrestricted_mode");
			plan.addLeg(leg);

			Activity secondActivity = populationFactory.createActivityFromCoord("generic", new Coord(0.0, 0.0));
			plan.addActivity(secondActivity);
		}

		Network network = scenario.getNetwork();
		NetworkFactory networkFactory = network.getFactory();

		Node node = networkFactory.createNode(Id.createNodeId("node"), new Coord(0.0, 0.0));
		network.addNode(node);

		Link link = networkFactory.createLink(Id.createLinkId("link"), node, node);
		network.addLink(link);

		Controler controller = new Controler(scenario);

		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				ConflictModule.bindResolver(binder()).toInstance(new ConflictResolver() {
					@Override
					public IdSet<Person> resolve(Population population, int iteration) {
						IdSet<Person> conflictingPersonIds = new IdSet<>(Person.class);

						int maximumRestricted = 40;
						int restrictedCount = 0;

						for (Person person : population.getPersons().values()) {
							Plan plan = person.getSelectedPlan();
							boolean usesRestrictedMode = false;

							for (Leg leg : TripStructureUtils.getLegs(plan)) {
								if (leg.getMode().equals("restricted_mode")) {
									usesRestrictedMode = true;
									break;
								}
							}

							if (usesRestrictedMode) {
								restrictedCount++;

								if (restrictedCount > maximumRestricted) {
									conflictingPersonIds.add(person.getId());
								}
							}
						}

						return conflictingPersonIds;
					}

					@Override
					public boolean isPotentiallyConflicting(Plan plan) {
						for (Leg leg : TripStructureUtils.getLegs(plan)) {
							if (leg.getMode().equals("restricted_mode")) {
								return true;
							}
						}

						return false;
					}

					@Override
					public String getName() {
						return "restriction";
					}
				});
			}
		});

		LegCounter counter = new LegCounter();
		counter.install(controller);

		controller.run();

		assertEquals(40, counter.restricted);
		assertEquals(60, counter.unrestricted);
	}

	private class LegCounter implements PersonDepartureEventHandler {
		int restricted;
		int unrestricted;

		@Override
		public void handleEvent(PersonDepartureEvent event) {
			if (event.getLegMode().equals("unrestricted_mode")) {
				unrestricted++;
			} else if (event.getLegMode().equals("restricted_mode")) {
				restricted++;
			}
		}

		@Override
		public void reset(int iteration) {
			restricted = 0;
			unrestricted = 0;
		}

		void install(Controler controller) {
			LegCounter self = this;

			controller.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					addEventHandlerBinding().toInstance(self);
				}
			});
		}
	}
}
