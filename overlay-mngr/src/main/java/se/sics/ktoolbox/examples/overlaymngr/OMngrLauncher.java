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

package se.sics.ktoolbox.examples.overlaymngr;

import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;
import se.sics.nat.NatLauncherProxy;
import se.sics.nat.NatSetup;
import se.sics.nat.NatSetupResult;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class OMngrLauncher extends ComponentDefinition {

    private Logger LOG = LoggerFactory.getLogger(OMngrLauncher.class);
    private String logPrefix = "";
    private static final int BIND_RETRY = 3;

    private Component timerComp;
    private Positive<Network> network;
    private Positive<SelfAddressUpdatePort> adrUpdate;
    private Component caracalClientComp;
    private Component heartbeatComp;
    
    private SystemConfig systemConfig;

    //**************************************************************************
    public OMngrLauncher() {
        LOG.info("{}initiating...", logPrefix);

        subscribe(handleStart, control);
    }

   //*****************************CONTROL**************************************
    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            connectNStartTimer();
            connectNStartNat();
            LOG.info("{}waiting for nat", logPrefix);
        }
    };

    private void connectNStartTimer() {
        timerComp = create(JavaTimer.class, Init.NONE);
        trigger(Start.event, timerComp.control());
    }

    private void connectNStartNat() {
        NatSetup natSetup = new NatSetup(new OMngrLauncherProxy(),
                timerComp.getPositive(Timer.class),
                new SystemConfigBuilder(ConfigFactory.load()));
        natSetup.setup();
        natSetup.start(false);
    }

    private void connectNStartApp() {
        connectCaracal();

//        subscribe(handleCaracalDisconnect, caracalClientComp.getPositive(CCBootstrapPort.class));
//        subscribe(handleCaracalReady, caracalClientComp.getPositive(CCBootstrapPort.class));
//        subscribe(handleHeartbeatReady, heartbeatComp.getPositive(CCHeartbeatPort.class));
    }

    public class OMngrLauncherProxy implements NatLauncherProxy {

        @Override
        public void startApp(NatSetupResult result) {
            OMngrLauncher.this.network = result.network;
            OMngrLauncher.this.adrUpdate = result.adrUpdate;
            OMngrLauncher.this.globalCroupier = result.globalCroupier;
            OMngrLauncher.this.systemConfig = result.systemConfig;
            LOG.info("{}nat started with:{}", logPrefix, result.systemConfig.self);
            OMngrLauncher.this.connectNStartApp();
        }

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return DYWSLauncher.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return DYWSLauncher.this.provides(portType);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return DYWSLauncher.this.control;
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return DYWSLauncher.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return DYWSLauncher.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return DYWSLauncher.this.connect(positive, negative);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return DYWSLauncher.this.connect(positive, negative, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return DYWSLauncher.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return DYWSLauncher.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            DYWSLauncher.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            DYWSLauncher.this.disconnect(positive, negative);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            DYWSLauncher.this.trigger(e, p);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            DYWSLauncher.this.subscribe(handler, port);
        }
    }

    //************************BASIC_SERVICES************************************
    private void connectCaracal() {
        CaracalClientConfig ccConfig = new CaracalClientConfig(systemConfig.config);
        caracalClientComp = create(CCBootstrapComp.class, new CCBootstrapComp.CCBootstrapInit(systemConfig, ccConfig, ConfigHelper.readCaracalBootstrap(systemConfig.config)));
        connect(caracalClientComp.getNegative(Timer.class), timerComp.getPositive(Timer.class));
        connect(caracalClientComp.getNegative(Network.class), network);
        trigger(Start.event, caracalClientComp.control());

        heartbeatComp = create(CCHeartbeatComp.class, new CCHeartbeatComp.CCHeartbeatInit(systemConfig, ccConfig));
        connect(heartbeatComp.getNegative(Timer.class), timerComp.getPositive(Timer.class));
        connect(heartbeatComp.getNegative(CCBootstrapPort.class), caracalClientComp.getPositive(CCBootstrapPort.class));
        trigger(Start.event, heartbeatComp.control());
    }

    private Handler handleCaracalReady = new Handler<CCReady>() {
        @Override
        public void handle(CCReady event) {
            LOG.info("{}starting: caracal ready", logPrefix);
            vodSchemaId = event.caracalSchemaData.getId("gvod.metadata");
            if (vodSchemaId == null) {
                LOG.error("exception:vod schema undefined shutting down");
                System.exit(1);
            }

            if (dyWS != null) {
                dyWS.setIsServerDown(false);
            }
        }
    };

    /**
     * Caracal client gets disconnected.
     */
    private Handler<CCDisconnected> handleCaracalDisconnect = new Handler<CCDisconnected>() {
        @Override
        public void handle(CCDisconnected event) {

            LOG.warn("{} caracal disconnected", logPrefix);

//          Inform the web service if it has already been booted.
            if (dyWS != null) {
                dyWS.setIsServerDown(true);
            }
        }
    };

    private Handler handleHeartbeatReady = new Handler<CCSimpleReady>() {
        @Override
        public void handle(CCSimpleReady e) {
            LOG.info("{}starting: heartbeat ready", logPrefix);
            connectApplication();
        }
    };

    //***************************APPLICATION************************************
    private void connectApplication() {
        connectNStartSweep();
        connectSweepSync();
        connectNStartVoDHost();
        startWebservice();
    }

    private void connectNStartVoDHost() {
        vodHostComp = create(HostManagerComp.class, new HostManagerComp.HostManagerInit(new HostManagerConfig(systemConfig.config), gvodSyncIFuture, vodSchemaId));
        connect(vodHostComp.getNegative(Network.class), network);
        connect(vodHostComp.getNegative(Timer.class), timerComp.getPositive(Timer.class));
        connect(vodHostComp.getNegative(CCBootstrapPort.class), caracalClientComp.getPositive(CCBootstrapPort.class));
        connect(vodHostComp.getNegative(CCHeartbeatPort.class), heartbeatComp.getPositive(CCHeartbeatPort.class));
        connect(vodHostComp.getNegative(SelfAddressUpdatePort.class), adrUpdate);
        trigger(Start.event, vodHostComp.control());
    }

    private void connectNStartSweep() {
        GradientConfig gradientConfig = new GradientConfig(systemConfig.config);
        CroupierConfig croupierConfig = new CroupierConfig(systemConfig.config);
        ElectionConfig electionConfig = new ElectionConfig(systemConfig.config);
        ChunkManagerConfig chunkManagerConfig = new ChunkManagerConfig(systemConfig.config);
        TreeGradientConfig treeGradientConfig = new TreeGradientConfig(systemConfig.config);

        sweepHostComp = create(SearchPeer.class, new SearchPeerInit(systemConfig, croupierConfig,
                SearchConfiguration.build(), GradientConfiguration.build(),
                chunkManagerConfig, gradientConfig, electionConfig, treeGradientConfig));
        connect(sweepHostComp.getNegative(Timer.class), timerComp.getPositive(Timer.class));
        connect(sweepHostComp.getNegative(Network.class), network);
        connect(sweepHostComp.getNegative(CCHeartbeatPort.class), heartbeatComp.getPositive(CCHeartbeatPort.class));
        connect(sweepHostComp.getNegative(SelfAddressUpdatePort.class), adrUpdate);
        trigger(Start.event, sweepHostComp.control());
    }

    private void connectSweepSync() {
        sweepSyncComp = create(SweepSyncComponent.class, Init.NONE);
        sweepSyncI = (SweepSyncI) sweepSyncComp.getComponent();
        connect(sweepSyncComp.getNegative(UiPort.class), sweepHostComp.getPositive(UiPort.class));
        trigger(Start.event, sweepSyncComp.control());
    }

    private void startWebservice() {
        LOG.info("starting webservice");

        try {
            dyWS = new DYWS(sweepSyncI, gvodSyncIFuture);
            String[] args = new String[]{"server", systemConfig.config.getString("webservice.server")};
            dyWS.run(args);
        } catch (ConfigException.Missing ex) {
            LOG.error("bad configuration, could not find webservice.server");
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            LOG.error("webservice error");
            throw new RuntimeException(ex);
        }
    }

    private static void systemSetup() {
        MessageRegistrator.register();
        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);
        serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
        serializerId = PMSerializerSetup.registerSerializers(serializerId);
        serializerId = SHPSerializerSetup.registerSerializers(serializerId);
        serializerId = NatSerializerSetup.registerSerializers(serializerId);
        serializerId = GradientSerializerSetup.registerSerializers(serializerId);
        serializerId = ElectionSerializerSetup.registerSerializers(serializerId);
        serializerId = AggregatorSerializerSetup.registerSerializers(serializerId);
        serializerId = ChunkManagerSerializerSetup.registerSerializers(serializerId);
        serializerId = SweepSerializerSetup.registerSerializers(serializerId);
        serializerId = GVoDSerializerSetup.registerSerializers(serializerId);

        if (serializerId > 255) {
            throw new RuntimeException("switch to bigger serializerIds, last serializerId:" + serializerId);
        }

        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, 0);
        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
    }

    public static void main(String[] args) throws IOException {
        HeartbeatServiceEnum.CROUPIER.setServiceId((byte) 1);
        VoDHeartbeatServiceEnum.CROUPIER.setServiceId((byte) 2);
        GetIp.NetworkInterfacesMask setIpType = GetIp.NetworkInterfacesMask.PUBLIC;
        if (args.length == 1 && args[0].equals("-tenDot")) {
            setIpType = GetIp.NetworkInterfacesMask.TEN_DOT_PRIVATE;
        }
        DYWSLauncher.setIpType(setIpType);

        systemSetup();

        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(DYWSLauncher.class, Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            System.exit(1);
        }
    }
}
