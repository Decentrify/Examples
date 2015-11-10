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
package se.sics.example.nat;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import se.sics.example.nat.node.NatEmulatorHostComp;
import se.sics.example.nat.node.SimHostComp;
import se.sics.example.nat.node.SimHostComp.SimHostInit;
import se.sics.example.nat.node.core.NodeHostComp;
import se.sics.example.nat.node.core.NodeHostComp.NodeHostInit;
import se.sics.kompics.Init;
import se.sics.ktoolbox.ipsolver.hooks.IpSolverHookFactory;
import se.sics.nat.network.NetworkMngrKConfig;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingHookFactory;
import se.sics.ktoolbox.overlaymngr.OverlayMngrConfig;
import se.sics.nat.detection.NatDetectionHooks;
import se.sics.nat.emulator.NatEmulatorComp.NatEmulatorInit;
import se.sics.nat.hooks.BaseHooks;
import se.sics.nat.stun.client.StunClientKConfig;
import se.sics.nat.stun.server.StunServerHostComp;
import se.sics.nat.stun.server.StunServerHostComp.StunServerHostInit;
import se.sics.nat.stun.server.StunServerKConfig;
import se.sics.nat.stun.upnp.hooks.UpnpHookFactory;
import se.sics.p2ptoolbox.simulator.cmd.impl.KillNodeCmd;
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
                public StartNodeCmd generate(final Integer nodeId) {
                    return new StartNodeCmd<SimHostComp, DecoratedAddress>() {
                        private DecoratedAddress selfAdr;

                        {
                            if (nodeId == 0) {
                                selfAdr = ScenarioSetup.globalCroupierBoot;
                            } else {
                                int openType = 0;
                                ScenarioSetup.ScenarioNat scenarioNatType = ScenarioSetup.ScenarioNat.values()[openType];
                                InetAddress self = ScenarioSetup.getLocalIp(nodeId, scenarioNatType);
                                selfAdr = DecoratedAddress.open(self, ScenarioSetup.appPort, nodeId);
                            }
                        }

                        @Override
                        public Integer getNodeId() {
                            return nodeId;
                        }

                        @Override
                        public int bootstrapSize() {
                            return 5;
                        }

                        @Override
                        public Class<SimHostComp> getNodeComponentDefinition() {
                            return SimHostComp.class;
                        }

                        @Override
                        public Init<SimHostComp> getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                            List<DecoratedAddress> boot = new ArrayList<>();
                            if (nodeId != 1) {
                                boot.add(ScenarioSetup.globalCroupierBoot);
                            }

                            KConfigCore configCore = new KConfigCore(ConfigFactory.load());
                            configCore.writeValue(SystemKConfig.id, nodeId);
                            configCore.writeValue(SystemKConfig.port, ScenarioSetup.appPort);
                            configCore.writeValue(SystemKConfig.seed, ScenarioSetup.baseSeed + nodeId);
                            configCore.writeValue(NetworkMngrKConfig.prefferedInterface, selfAdr.getIp().getHostAddress());
                            configCore.writeValue(StunServerKConfig.stunServerPort1, ScenarioSetup.stunServerPorts.getValue0());
                            configCore.writeValue(StunServerKConfig.stunServerPort2, ScenarioSetup.stunServerPorts.getValue1());
                            configCore.writeValue(OverlayMngrConfig.bootstrap, boot);
                            
                            SystemHookSetup systemHooks = new SystemHookSetup();
                            systemHooks.register(BaseHooks.RequiredHooks.IP_SOLVER.hookName, IpSolverHookFactory.getIpSolverEmulator());
                            systemHooks.register(BaseHooks.RequiredHooks.PORT_BINDING.hookName, PortBindingHookFactory.getPortBinderEmulator());
                            systemHooks.register(NatDetectionHooks.RequiredHooks.UPNP.hookName, UpnpHookFactory.getNoUpnp());
                            return new SimHostInit(configCore, systemHooks, StunServerHostComp.class, new StunServerHostInit(configCore, systemHooks));
                        }

                        @Override
                        public DecoratedAddress getAddress() {
                            return selfAdr;
                        }
                    };
                }
            };

    static Operation2<StartNodeCmd, Integer, Integer> startNode
            = new Operation2<StartNodeCmd, Integer, Integer>() {

                @Override
                public StartNodeCmd generate(final Integer nodeId, final Integer natType) {
                    return new StartNodeCmd<NatEmulatorHostComp, DecoratedAddress>() {
                        private DecoratedAddress selfAdr;

                        {
                            if (nodeId == 1) {
                                if (natType != 0) {
                                    throw new RuntimeException("node 1 is expected to be open");
                                }
                                selfAdr = ScenarioSetup.globalCroupierBoot;
                            } else {
                                ScenarioSetup.ScenarioNat scenarioNatType = ScenarioSetup.ScenarioNat.values()[natType];
                                InetAddress self = ScenarioSetup.getLocalIp(nodeId, scenarioNatType);
                                selfAdr = DecoratedAddress.open(self, ScenarioSetup.appPort, nodeId);
                            }
                        }

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
                            NatEmulatorInit natEInit = ScenarioSetup.getNatEmulator(nodeId, scenarioNatType);

                            List<DecoratedAddress> boot = new ArrayList<>();
                            if (nodeId != 0) {
                                boot.add(ScenarioSetup.globalCroupierBoot);
                            }

                            KConfigCore configCore = new KConfigCore(ConfigFactory.load());
                            configCore.writeValue(SystemKConfig.id, nodeId);
                            configCore.writeValue(SystemKConfig.seed, ScenarioSetup.baseSeed + nodeId);
                            configCore.writeValue(SystemKConfig.port, ScenarioSetup.appPort);
                            configCore.writeValue(NetworkMngrKConfig.prefferedInterface, selfAdr.getIp().getHostAddress());
                            configCore.writeValue(StunClientKConfig.stunClientPort1, ScenarioSetup.stunClientPorts.getValue0());
                            configCore.writeValue(StunClientKConfig.stunClientPort2, ScenarioSetup.stunClientPorts.getValue1());
                            configCore.writeValue(OverlayMngrConfig.bootstrap, boot);

                            SystemHookSetup systemHooks = new SystemHookSetup();
                            systemHooks.register(BaseHooks.RequiredHooks.IP_SOLVER.hookName, IpSolverHookFactory.getIpSolverEmulator());
                            systemHooks.register(BaseHooks.RequiredHooks.PORT_BINDING.hookName, PortBindingHookFactory.getPortBinderEmulator());
                            systemHooks.register(NatDetectionHooks.RequiredHooks.UPNP.hookName, UpnpHookFactory.getNoUpnp());

                            return new NatEmulatorHostComp.NatEmulatorHostInit(configCore, systemHooks,
                                    NodeHostComp.class, new NodeHostInit(configCore, systemHooks), natEInit);
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

    static Operation1<KillNodeCmd, Integer> killNode
            = new Operation1<KillNodeCmd, Integer>() {

                @Override
                public KillNodeCmd generate(final Integer nodeId) {
                    return new KillNodeCmd() {

                        @Override
                        public Integer getNodeId() {
                            return nodeId;
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
                StochasticProcess stunServers = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(4, startStunServer, new BasicIntSequentialDistribution(1));
                    }
                };
                StochasticProcess openNodes = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(3, startNode, new BasicIntSequentialDistribution(100),
                                new ConstantDistribution<>(Integer.class, 0));
                    }
                };
                StochasticProcess startNatNodes1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(2, startNode, new BasicIntSequentialDistribution(200),
                                new ConstantDistribution<>(Integer.class, 1));
                    }
                };
                StochasticProcess killNatNodes1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(2, killNode, new BasicIntSequentialDistribution(200));
                    }
                };
                
                setup.start();
                stunServers.startAfterTerminationOf(2000, setup);
                openNodes.startAfterTerminationOf(10000, stunServers);
                startNatNodes1.startAfterTerminationOf(10000, openNodes);
//                killNatNodes1.startAfterTerminationOf(5*30000, startNatNodes1);
                terminateAfterTerminationOf(200000, startNatNodes1);
            }
        };
        return scen;
    }
}
