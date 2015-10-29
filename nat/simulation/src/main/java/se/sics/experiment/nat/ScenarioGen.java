/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * ToolsExamples is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.experiment.nat;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.javatuples.Pair;
import se.sics.example.nat.node.NatEmulatorHostComp;
import se.sics.example.nat.node.core.NodeHostComp;
import se.sics.example.nat.node.core.NodeHostComp.NodeHostInit;
import se.sics.example.nat.node.core.NodeKConfig;
import se.sics.kompics.Init;
import se.sics.ktoolbox.ipsolver.hooks.IpSolverHookFactory;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.ktoolbox.networkmngr.NetworkMngrHooks;
import se.sics.ktoolbox.networkmngr.NetworkMngrKConfig;
import se.sics.ktoolbox.networkmngr.hooks.PortBindingHookFactory;
import se.sics.p2ptoolbox.simulator.cmd.impl.SetupCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation2;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.BasicIntSequentialDistribution;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.impl.SystemKConfig;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;
import se.sics.p2ptoolbox.util.traits.Trait;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarioGen {

    static Operation<SetupCmd> runSetup = new Operation<SetupCmd>() {
        @Override
        public SetupCmd generate() {
            return new SetupCmd() {
                @Override
                public void runSetup() {
                    ImmutableMap<Class<? extends Trait>, Integer> acceptedTraits
                            = ImmutableMap.<Class<? extends Trait>, Integer>builder().
                            put(NatedTrait.class, 0).build();
                    DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
                }
            };
        }
    };

    static Operation1<StartNodeCmd, Integer> startStunServer
            = new Operation1<StartNodeCmd, Integer>() {
                @Override
                public StartNodeCmd generate(Integer nodeId) {
                    return null;
                }
            };

    static Operation2<StartNodeCmd, Integer, Integer> startNode
            = new Operation2<StartNodeCmd, Integer, Integer>() {

                @Override
                public StartNodeCmd generate(final Integer nodeId, final Integer natType) {
                    return new StartNodeCmd<NatEmulatorHostComp, DecoratedAddress>() {
                        private DecoratedAddress selfAdr;

                        @Override
                        public Integer getNodeId() {
                            return nodeId;
                        }

                        @Override
                        public Class getNodeComponentDefinition() {
                            return NatEmulatorHostComp.class;
                        }

                        @Override
                        public Init getNodeComponentInit(DecoratedAddress aggregator, Set<DecoratedAddress> bootstrap) {
                            ScenarioSetup.ScenarioNat scenarioNatType = ScenarioSetup.ScenarioNat.values()[natType];
                            InetAddress self = ScenarioSetup.getLocalIp(nodeId, scenarioNatType);
                            InetAddress ping = ScenarioSetup.getLocalIp(nodeId, scenarioNatType);
//                            NatEmulatorInit natEInit = ScenarioSetup.getNatEmulator(nodeId, scenarioNatType);

                            //TODO Alex fix later 
                            selfAdr = DecoratedAddress.open(self, ScenarioSetup.appPort, nodeId);
                            DecoratedAddress pingAdr = DecoratedAddress.open(self, ScenarioSetup.appPort, nodeId);

                            KConfigCore configCore = new KConfigCore(ConfigFactory.load());
                            configCore.writeValue(SystemKConfig.id, nodeId);
                            configCore.writeValue(SystemKConfig.seed, ScenarioSetup.baseSeed + nodeId);
                            configCore.writeValue(NetworkMngrKConfig.prefferedInterface, self.getHostAddress());
                            configCore.writeValue(NodeKConfig.port, ScenarioSetup.appPort);
                            configCore.writeValue(NodeKConfig.ping, pingAdr);

                            SystemHookSetup systemHooks = new SystemHookSetup();
                            systemHooks.register(NetworkMngrHooks.RequiredHooks.IP_SOLVER.hookName, IpSolverHookFactory.getIpSolverEmulator());
                            systemHooks.register(NetworkMngrHooks.RequiredHooks.PORT_BINDING.hookName, PortBindingHookFactory.getPortBinderEmulator());
                            return new NatEmulatorHostComp.NatEmulatorHostInit(configCore, systemHooks,
                                    NodeHostComp.class, new NodeHostInit(configCore, systemHooks));
                        }

                        @Override
                        public int bootstrapSize() {
                            return 5;
                        }

                        @Override
                        public DecoratedAddress getAddress() {
                            return selfAdr;
                        }
                    };
                }
            };

    public static SimulationScenario ping() {

        SimulationScenario scen = new SimulationScenario() {
            {
                StochasticProcess setup = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, runSetup);
                    }
                };
                StochasticProcess natedNodes = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(3, startNode, new BasicIntSequentialDistribution(4),
                                new ConstantDistribution<>(Integer.class, 0));
                    }
                };
                setup.start();
                natedNodes.startAfterTerminationOf(5000, setup);
                terminateAfterTerminationOf(100000, natedNodes);
            }
        };
        return scen;
    }
}
